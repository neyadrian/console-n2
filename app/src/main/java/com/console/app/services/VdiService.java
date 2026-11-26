package com.console.app.services;

import com.console.app.models.InstanciaVdi;
import com.console.app.repositories.InstanciaVdiRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class VdiService {

    @Value("${vdi.agent.url:http://192.168.56.1:8095}")
    private String vdiAgentUrl;

    @Value("${vdi.agent.token:N2_VDI_HOST_TOKEN_2026}")
    private String vdiToken;

    @Autowired
    private InstanciaVdiRepository vdiRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final int COTA_MAX_POR_USUARIO = 1;
    private static final int CAPACIDADE_MAX_HOST = 2;

    /**
     * Admissão transacional: verifica cota, capacidade global, reserva recurso e aciona o agente no Windows.
     */
    @Transactional
    public synchronized String solicitarNovaVm(String perfil) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth.getName();

        // 1. Verifica cota do usuário (Teste T-VDI-02)
        long ativasUsuario = vdiRepository.countByProprietarioAndEstadoNotIn(usuario, List.of("FALHA", "EXCLUIDA"));
        if (ativasUsuario >= COTA_MAX_POR_USUARIO) {
            auditoriaService.registrar("VDI:COTA", "SOLICITAR_VM", "RECUSADO", "Cota excedida para " + usuario);
            throw new IllegalStateException("Cota excedida: cada usuário pode manter no máximo " + COTA_MAX_POR_USUARIO + " instância ativa.");
        }

        // 2. Verifica capacidade total do host
        long ativasGlobal = vdiRepository.countByEstadoIn(List.of("SOLICITADA", "EM_CRIACAO", "INICIANDO", "DISPONIVEL"));
        if (ativasGlobal >= CAPACIDADE_MAX_HOST) {
            auditoriaService.registrar("VDI:HOST", "SOLICITAR_VM", "RECUSADO", "Capacidade máxima do host atingida");
            throw new IllegalStateException("Capacidade do host esgotada no momento. Aguarde a liberação de uma instância.");
        }

        int cpus = "AVANCADO".equalsIgnoreCase(perfil) ? 2 : 1;
        int ram = "AVANCADO".equalsIgnoreCase(perfil) ? 4096 : 2048;
        String sufixo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String nomeVm = "LAB-" + usuario.toUpperCase().replaceAll("[^A-Z0-9]", "") + "-" + sufixo;

        InstanciaVdi vm = new InstanciaVdi();
        vm.setNome(nomeVm);
        vm.setPerfil(cpus == 2 ? "AVANCADO (2 vCPU / 4 GiB)" : "BASICO (1 vCPU / 2 GiB)");
        vm.setCpus(cpus);
        vm.setRamMb(ram);
        vm.setProprietario(usuario);
        vm.setEstado("EM_CRIACAO");
        vdiRepository.save(vm);

        try {
            String json = String.format("{\"acao\":\"criar\",\"nome\":\"%s\",\"cpus\":%d,\"ramMb\":%d}", nomeVm, cpus, ram);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(vdiAgentUrl))
                    .timeout(Duration.ofSeconds(65))
                    .header("Content-Type", "application/json")
                    .header("X-Vdi-Token", vdiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode node = mapper.readTree(resp.body());
                vm.setUuidVirtualBox(node.path("uuid").asText());
                vm.setEstado("INICIANDO");
                vdiRepository.save(vm);
                auditoriaService.registrar("VDI:" + nomeVm, "PROVISIONAR_VM", "SUCESSO", "UUID: " + vm.getUuidVirtualBox());
                return "Instância " + nomeVm + " provisionada e iniciando! Atualize o status para obter o IP e acesso RDP.";
            } else {
                vm.setEstado("FALHA");
                vdiRepository.save(vm);
                auditoriaService.registrar("VDI:" + nomeVm, "PROVISIONAR_VM", "FALHA", resp.body());
                return "Falha no provisionamento pelo agente: " + resp.body();
            }
        } catch (Exception e) {
            vm.setEstado("FALHA");
            vdiRepository.save(vm);
            auditoriaService.registrar("VDI:" + nomeVm, "PROVISIONAR_VM", "FALHA", e.getMessage());
            return "Erro de comunicação com o agente do host (reserva liberada): " + e.getMessage();
        }
    }

    /**
     * Reconcilia estado com o VirtualBox e valida prontidão da porta RDP (3389).
     */
    public void atualizarProntidao(InstanciaVdi vm) {
        if ("EXCLUIDA".equals(vm.getEstado()) || "FALHA".equals(vm.getEstado())) return;
        try {
            String json = String.format("{\"acao\":\"status\",\"nome\":\"%s\"}", vm.getNome());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(vdiAgentUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Vdi-Token", vdiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode node = mapper.readTree(resp.body());
                String estadoVbox = node.path("estado").asText();
                String ip = node.path("ip").asText();
                if (!ip.isEmpty()) vm.setIpAtribuido(ip);

                if ("poweroff".equals(estadoVbox) || "aborted".equals(estadoVbox)) {
                    vm.setEstado("DESLIGADA");
                } else if ("running".equals(estadoVbox)) {
                    if (!ip.isEmpty() && testarPortaRdp(ip, 3389)) {
                        vm.setEstado("DISPONIVEL");
                    } else {
                        vm.setEstado("INICIANDO");
                    }
                }
                vdiRepository.save(vm);
            }
        } catch (Exception ignored) {}
    }

    private boolean testarPortaRdp(String ip, int porta) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, porta), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Desliga ou exclui instância validando proprietário ou perfil ADMINISTRADOR (Teste T-VDI-04).
     */
    public String operarInstancia(Long id, String acao) {
        InstanciaVdi vm = vdiRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instância não encontrada."));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));

        if (!isAdmin && !vm.getProprietario().equals(auth.getName())) {
            auditoriaService.registrar("VDI:" + vm.getNome(), acao.toUpperCase(), "RECUSADO", "Usuário não é proprietário da VM");
            throw new SecurityException("Acesso negado: você só pode operar instâncias criadas por você.");
        }

        try {
            String json = String.format("{\"acao\":\"%s\",\"nome\":\"%s\"}", acao, vm.getNome());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(vdiAgentUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Vdi-Token", vdiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                vm.setEstado("excluir".equals(acao) ? "EXCLUIDA" : "DESLIGADA");
                vdiRepository.save(vm);
                auditoriaService.registrar("VDI:" + vm.getNome(), acao.toUpperCase(), "SUCESSO", "Operação concluída no host");
                return "Operação '" + acao + "' concluída na instância " + vm.getNome() + ".";
            }
            return "Falha ao executar '" + acao + "': " + resp.body();
        } catch (Exception e) {
            return "Erro de comunicação com o agente VDI: " + e.getMessage();
        }
    }
}