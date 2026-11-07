package com.console.app.controllers;

import com.console.app.services.DebianMetricsService;
import com.console.app.services.SshIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @Autowired
    private SshIntegrationService sshIntegrationService;

    @Autowired
    private DebianMetricsService metricsService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        // Envia as métricas reais lidas do Debian para o HTML com o nome "metricas"
        model.addAttribute("metricas", metricsService.getMetrics());
        return "dashboard";
    }

    @Autowired
    private com.console.app.services.SshIntegrationService sshService;

    @org.springframework.web.bind.annotation.PostMapping("/alterar-hostname")
    public String alterarHostname(@org.springframework.web.bind.annotation.RequestParam String novoNome) {
        // ATENÇÃO: Substitua '123' pela senha real do usuário administrador do seu Debian
        String comando = "echo '123' | sudo -S hostnamectl set-hostname " + novoNome;

        sshService.executeCommand(comando);

        // Recarrega o Dashboard automaticamente após enviar o comando
        return "redirect:/dashboard";
    }

    @GetMapping("/logs")
    public String viewLogs(Model model) {
        String logsDebian = sshIntegrationService.executeCommand("tail -n 50 /var/log/syslog");
        model.addAttribute("logs", logsDebian);
        return "logs";
    }
}
