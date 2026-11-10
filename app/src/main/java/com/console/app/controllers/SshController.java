package com.console.app.controllers;

import com.console.app.security.RoleCheck;
import com.console.app.services.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
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
        model.addAttribute("currentHost", sshService.getCurrentHostname());
        model.addAttribute("originalHost", sshService.getOriginalHostname());
        return "ssh";
    }

    @PostMapping("/ssh/hostname")
    public String alterarHostname(@RequestParam("newHostname") String newHostname,
                                  Model model,
                                  Authentication authentication) {
        if (!RoleCheck.isAdmin(authentication)) {
            model.addAttribute("error", "Acesso recusado pelo backend: o perfil "
                    + RoleCheck.perfil(authentication)
                    + " não pode alterar o hostname. Apenas o ADMINISTRADOR.");
            model.addAttribute("currentHost", sshService.getCurrentHostname());
            model.addAttribute("originalHost", sshService.getOriginalHostname());
            return "ssh";
        }

        String resultado = sshService.setHostname(newHostname);
        if (resultado.startsWith("Erro")) {
            model.addAttribute("error", resultado);
        } else {
            model.addAttribute("message", resultado);
        }

        model.addAttribute("currentHost", sshService.getCurrentHostname());
        model.addAttribute("originalHost", sshService.getOriginalHostname());
        return "ssh";
    }

    @PostMapping("/ssh/restore")
    public String restaurarHostname(Model model, Authentication authentication) {
        if (!RoleCheck.isAdmin(authentication)) {
            model.addAttribute("error", "Acesso recusado pelo backend: o perfil "
                    + RoleCheck.perfil(authentication)
                    + " não pode restaurar o hostname. Apenas o ADMINISTRADOR.");
            model.addAttribute("currentHost", sshService.getCurrentHostname());
            model.addAttribute("originalHost", sshService.getOriginalHostname());
            return "ssh";
        }

        String resultado = sshService.restoreOriginalHostname();
        if (resultado.startsWith("Erro")) {
            model.addAttribute("error", resultado);
        } else {
            model.addAttribute("message", resultado);
        }

        model.addAttribute("currentHost", sshService.getCurrentHostname());
        model.addAttribute("originalHost", sshService.getOriginalHostname());
        return "ssh";
    }
}
