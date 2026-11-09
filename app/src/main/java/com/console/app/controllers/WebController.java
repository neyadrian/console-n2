package com.console.app.controllers;

import com.console.app.services.DebianMetricsService;
import com.console.app.services.EmailService;
import com.console.app.services.FtpService;
import com.console.app.services.SshIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class WebController {

    @Autowired
    private SshIntegrationService sshService;

    @Autowired
    private DebianMetricsService metricsService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private FtpService ftpService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("metricas", metricsService.getMetrics());
        return "dashboard";
    }

    @GetMapping("/logs")
    public String viewLogs(Model model) {
        String logsDebian = sshService.executeCommand("journalctl -n 50 --no-pager");
        model.addAttribute("logs", logsDebian);
        return "logs";
    }

    @GetMapping("/ssh")
    public String sshPage(Model model) {
        String currentHost = sshService.getCurrentHostname();
        model.addAttribute("currentHost", currentHost);
        return "ssh";
    }

    @PostMapping("/ssh/update-hostname")
    public String updateHostname(@RequestParam("newHostname") String newHostname, Model model) {
        String result = sshService.updateHostname(newHostname);
        model.addAttribute("message", "Operação concluída. Resultado: " + result);
        model.addAttribute("currentHost", sshService.getCurrentHostname());
        return "ssh";
    }

    @GetMapping("/arquivos")
    public String arquivosPage(Model model) {
        List<String> arquivos = ftpService.listFiles();
        model.addAttribute("arquivos", arquivos);
        return "arquivos";
    }

    @PostMapping("/arquivos/upload")
    public String uploadArquivo(@RequestParam("file") MultipartFile file, Model model) {
        boolean success = ftpService.enviarArquivo(file);
        if (success) {
            model.addAttribute("message", "Arquivo enviado com sucesso para o vsftpd!");
        } else {
            model.addAttribute("error", "Falha ao enviar o arquivo via FTP.");
        }
        model.addAttribute("arquivos", ftpService.listFiles());
        return "arquivos";
    }

    @GetMapping("/email")
    public String paginaEmail() {
        return "email";
    }

    @PostMapping("/enviar-email")
    public String enviarEmail(
            @RequestParam("destinatario") String destinatario,
            @RequestParam("assunto") String assunto,
            @RequestParam("mensagem") String mensagem,
            Model model) {
        try {
            emailService.enviarEmail(destinatario, assunto, mensagem);
            model.addAttribute("sucesso", "E-mail enviado com sucesso para " + destinatario);
        } catch (Exception e) {
            model.addAttribute("erro", "Falha ao enviar e-mail: " + e.getMessage());
        }
        return "email";
    }
}