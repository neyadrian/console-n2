package com.console.app.config;

import com.console.app.security.RoleCheck;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute
    public void addLoggedUser(Model model, Authentication authentication) {
        if (authentication != null) {
            model.addAttribute("usuarioLogado", authentication.getName());
            model.addAttribute("perfilLogado", RoleCheck.perfil(authentication));
        }
    }
}
