package com.console.app.controllers;

import com.console.app.security.RoleCheck;
import com.console.app.services.EmailService;
import com.console.app.services.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebController {

    @Autowired
    private SshService sshService;

    @Autowired
    private EmailService emailService;

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
        switch (service.toLowerCase()) {
            case "apache2":
            case "apache":
                command = "journalctl -u apache2 -n " + safeLines + " --no-pager 2>/dev/null || tail -n " + safeLines + " /var/log/apache2/access.log 2>/dev/null";
                break;
            case "vsftpd":
            case "ftp":
                command = "journalctl -u vsftpd -n " + safeLines + " --no-pager 2>/dev/null || tail -n " + safeLines + " /var/log/vsftpd.log 2>/dev/null";
                break;
            case "postfix":
            case "smtp":
                command = "journalctl -u postfix -n " + safeLines + " --no-pager 2>/dev/null || tail -n " + safeLines + " /var/log/mail.log 2>/dev/null";
                break;
            case "dovecot":
            case "imap":
                command = "journalctl -u dovecot -n " + safeLines + " --no-pager 2>/dev/null || tail -n " + safeLines + " /var/log/mail.log 2>/dev/null";
                break;
            case "ssh":
                command = "journalctl -u ssh -n " + safeLines + " --no-pager";
                break;
            default:
                service = "geral";
                command = "journalctl -n " + safeLines + " --no-pager";
                break;
        }

        String logsDebian = sshService.executeCommand(command);
        model.addAttribute("logs", logsDebian);
        model.addAttribute("selectedService", service);
        model.addAttribute("selectedLines", safeLines);
        return "logs";
    }
}
