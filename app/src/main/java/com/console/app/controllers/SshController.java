package com.console.app.controllers;

import com.console.app.services.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SshController {

    @Autowired
    private SshService sshService;

    @GetMapping("/ssh")
    public String paginaSsh(Model model) {
        String currentHost = sshService.getCurrentHostname();
        model.addAttribute("currentHost", currentHost);
        return "ssh"; // Nome da vista HTML
    }

    @PostMapping("/ssh/hostname")
    public String alterarHostname(@RequestParam("newHostname") String newHostname, Model model) {
        String resultado = sshService.setHostname(newHostname);
        model.addAttribute("message", "Operação concluída. Resultado: " + resultado);
        model.addAttribute("currentHost", sshService.getCurrentHostname());
        return "ssh";
    }
}