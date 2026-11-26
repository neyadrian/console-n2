package com.console.app.controllers;

import com.console.app.repositories.ConfiguracaoVersaoRepository;
import com.console.app.security.RoleCheck;
import com.console.app.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {

    @Autowired
    private SshService sshService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DebianMetricsService debianMetricsService;

    @Autowired
    private DebianAgentService debianAgentService;

    @Autowired
    private ServicoConfigService servicoConfigService;

    @Autowired
    private ConfiguracaoVersaoRepository versaoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private DnsService dnsService;

    @Autowired
    private DhcpService dhcpService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        return "dashboard";
    }

    @GetMapping("/logs")
    public String viewLogs(@RequestParam(value = "service", required = false, defaultValue = "geral") String service,
                           @RequestParam(value = "lines", required = false, defaultValue = "50") int lines,
                           Model model) {

        // Limita a quantidade de linhas para segurança
        int safeLines = Math.min(Math.max(lines, 10), 200);

        String command;
        String pass = "123456";

        switch (service.toLowerCase()) {
            case "apache2":
            case "apache":
                // Força a leitura do access.log com sudo
                command = "echo '" + pass + "' | sudo -S tail -n " + safeLines + " /var/log/apache2/access.log 2>/dev/null || journalctl -u apache2 -n " + safeLines + " --no-pager";
                break;

            case "vsftpd":
            case "ftp":
                command = "echo '" + pass + "' | sudo -S journalctl -u vsftpd -n " + safeLines + " --no-pager || echo '" + pass + "' | sudo -S tail -n " + safeLines + " /var/log/vsftpd.log 2>/dev/null";
                break;

            case "postfix":
            case "smtp":
                command = "echo '" + pass + "' | sudo -S journalctl -u postfix -n " + safeLines + " --no-pager || echo '" + pass + "' | sudo -S tail -n " + safeLines + " /var/log/mail.log 2>/dev/null";
                break;

            case "dovecot":
            case "imap":
                command = "echo '" + pass + "' | sudo -S journalctl -u dovecot -n " + safeLines + " --no-pager || echo '" + pass + "' | sudo -S tail -n " + safeLines + " /var/log/mail.log 2>/dev/null";
                break;

            case "ssh":
                command = "echo '" + pass + "' | sudo -S journalctl -u ssh -n " + safeLines + " --no-pager";
                break;

            default:
                service = "geral";
                command = "echo '" + pass + "' | sudo -S journalctl -n " + safeLines + " --no-pager";
                break;
        }

        String logsDebian = sshService.executeCommand(command);

        model.addAttribute("logs", logsDebian);
        model.addAttribute("selectedService", service);
        model.addAttribute("selectedLines", safeLines);

        return "logs";
    }

    @GetMapping("/servicos")
    public String viewServicos(Model model) {
        model.addAttribute("metricas", debianMetricsService.getMetricsData());
        model.addAttribute("configs", servicoConfigService.lerConfiguracoesAtuais());
        model.addAttribute("historicoConfigs", versaoRepository.findTop15ByOrderByAplicadoEmDesc());
        return "servicos";
    }

    // Iniciar, Parar ou Reiniciar serviço (com confirmação no frontend e validação no backend)
    @PostMapping("/servicos/operar")
    public String operarServico(@RequestParam String servico,
                                @RequestParam String acao,
                                RedirectAttributes redirectAttributes) {
        try {
            String resultado = debianAgentService.controlarServico(servico, acao);
            redirectAttributes.addFlashAttribute("mensagemSucesso", resultado);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Erro: " + e.getMessage());
        }
        return "redirect:/servicos";
    }

    // Alterar configuração validada (Apenas ADMINISTRADOR)
    @PostMapping("/servicos/configurar")
    public String configurarServico(@RequestParam String servico,
                                    @RequestParam String parametro,
                                    @RequestParam int valor,
                                    RedirectAttributes redirectAttributes) {
        try {
            String resultado = servicoConfigService.alterarConfiguracao(servico, parametro, valor);
            redirectAttributes.addFlashAttribute("mensagemSucesso", resultado);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Configuração recusada: " + e.getMessage());
        }
        return "redirect:/servicos";
    }

    @PostMapping("/servicos/configurar/restaurar")
    public String restaurarConfiguracao(@RequestParam Long versaoId,
                                        RedirectAttributes redirectAttributes) {
        try {
            String resultado = servicoConfigService.restaurarUltimaVersao(versaoId);
            redirectAttributes.addFlashAttribute("mensagemSucesso", resultado);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Falha ao restaurar: " + e.getMessage());
        }
        return "redirect:/servicos";
    }

    @GetMapping("/auditoria")
    public String viewAuditoria(Model model) {
        model.addAttribute("registros", auditoriaService.consultarLogsPorPermissao());
        return "auditoria";
    }

    @GetMapping("/dns")
    public String viewDns(@RequestParam(value = "consulta", required = false, defaultValue = "site.empresa.test") String consulta,
                          @RequestParam(value = "tipoConsulta", required = false, defaultValue = "A") String tipoConsulta,
                          Model model) {
        model.addAttribute("dns", dnsService.obterEstadoZona());
        model.addAttribute("resultadoDig", dnsService.testarConsultaDig(consulta, tipoConsulta));
        model.addAttribute("consultaAtual", consulta);
        return "dns";
    }

    @PostMapping("/dns/operar/salvar")
    public String salvarRegistroDns(@RequestParam String nome,
                                    @RequestParam String tipo,
                                    @RequestParam String valor,
                                    RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("mensagemSucesso", dnsService.salvarRegistro(nome, tipo, valor));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/dns";
    }

    @PostMapping("/dns/operar/remover")
    public String removerRegistroDns(@RequestParam String nome, RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("mensagemSucesso", dnsService.removerRegistro(nome));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/dns";
    }

    @PostMapping("/dns/operar/restaurar")
    public String restaurarZonaDns(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensagemSucesso", dnsService.restaurarBackupZona());
        return "redirect:/dns";
    }

    // ================= MÓDULO DHCP (KEA) =================
    @GetMapping("/dhcp")
    public String viewDhcp(Model model) {
        model.addAttribute("dhcp", dhcpService.obterEstadoDhcp());
        return "dhcp";
    }

    @PostMapping("/dhcp/operar/reservar")
    public String adicionarReservaDhcp(@RequestParam String mac,
                                       @RequestParam String ip,
                                       @RequestParam String hostname,
                                       RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("mensagemSucesso", dhcpService.adicionarReserva(mac, ip, hostname));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/dhcp";
    }

    @PostMapping("/dhcp/operar/remover")
    public String removerReservaDhcp(@RequestParam String mac, RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("mensagemSucesso", dhcpService.removerReserva(mac));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/dhcp";
    }

    @PostMapping("/dhcp/operar/restaurar")
    public String restaurarConfigDhcp(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensagemSucesso", dhcpService.restaurarBackupDhcp());
        return "redirect:/dhcp";
    }
}
