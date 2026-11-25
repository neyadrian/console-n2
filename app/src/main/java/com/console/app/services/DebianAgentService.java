package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

@Service
public class DebianAgentService {

    @Value("${debian.agent.url:http://192.168.100.10:8090}")
    private String agentUrl;

    @Value("${debian.agent.token:N2_AGENT_TOKEN_SEGURO_2026}")
    private String agentToken;

    @Autowired
    private AuditoriaService auditoriaService;

    private static final Set<String> SERVICOS_VALIDOS = Set.of(
            "ssh", "apache2", "vsftpd", "postfix", "dovecot", "bind9", "kea-dhcp4-server"
    );
    private static final Set<String> ACOES_VALIDAS = Set.of("start", "stop", "restart");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public String controlarServico(String servico, String acao) {
        if (!SERVICOS_VALIDOS.contains(servico) || !ACOES_VALIDAS.contains(acao)) {
            auditoriaService.registrar("SERVICO:" + servico, acao.toUpperCase(), "RECUSADO", "Parâmetros fora da whitelist");
            throw new IllegalArgumentException("Serviço ou ação não permitida.");
        }

        try {
            String jsonBody = String.format("{\"servico\":\"%s\",\"acao\":\"%s\"}", servico, acao);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("X-Agent-Token", agentToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                auditoriaService.registrar("SERVICO:" + servico, acao.toUpperCase(), "SUCESSO", "Resposta agente: " + response.body());
                return "Operação '" + acao + "' executada com sucesso em " + servico + ". Resposta: " + response.body();
            } else {
                auditoriaService.registrar("SERVICO:" + servico, acao.toUpperCase(), "FALHA", "HTTP " + response.statusCode());
                return "Falha ao executar '" + acao + "' em " + servico + " (HTTP " + response.statusCode() + ").";
            }
        } catch (Exception e) {
            auditoriaService.registrar("SERVICO:" + servico, acao.toUpperCase(), "RESULTADO_DESCONHECIDO", e.getMessage());
            return "Timeout ou falha de comunicação com o agente (resultado desconhecido — verifique o estado antes de repetir).";
        }
    }
}