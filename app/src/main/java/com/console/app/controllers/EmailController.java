package com.console.app.controllers;

import com.console.app.security.RoleCheck;
import com.console.app.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class EmailController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/email")
    public String paginaEmail(@RequestParam(value = "conta", required = false, defaultValue = "aluno") String conta,
                              @RequestParam(value = "pasta", required = false, defaultValue = "INBOX") String pasta,
                              Model model) {
        String user = "suporte".equalsIgnoreCase(conta) ? "suporte" : "aluno";
        String folder = "Sent".equalsIgnoreCase(pasta) ? "Sent" : "INBOX";

        List<String> mensagens = emailService.listarMensagensDoUsuario(user, "123456", folder);

        model.addAttribute("contaSelecionada", user);
        model.addAttribute("pastaSelecionada", folder);
        model.addAttribute("mensagens", mensagens);
        return "email";
    }

    @PostMapping({"/email/enviar", "/enviar-email"})
    public String enviarEmail(
            @RequestParam("remetente") String remetente,
            @RequestParam("destinatario") String destinatario,
            @RequestParam("assunto") String assunto,
            @RequestParam("mensagem") String mensagem,
            Model model,
            Authentication authentication) {

        if (!RoleCheck.canOperate(authentication)) {
            model.addAttribute("erro", "Acesso recusado pelo backend: o perfil "
                    + RoleCheck.perfil(authentication)
                    + " não pode enviar e-mails. Apenas ADMINISTRADOR e OPERADOR.");
            carregarMensagens(model, "aluno", "INBOX");
            return "email";
        }

        try {
            emailService.enviarESalvarCopia(remetente, destinatario, assunto, mensagem);
            model.addAttribute("sucesso", "E-mail enviado por SMTP com sucesso e cópia registrada na pasta Enviados (Sent)!");
        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
        }

        String user = remetente.contains("suporte") ? "suporte" : "aluno";
        carregarMensagens(model, user, "INBOX");
        return "email";
    }

    private void carregarMensagens(Model model, String user, String folder) {
        model.addAttribute("contaSelecionada", user);
        model.addAttribute("pastaSelecionada", folder);
        model.addAttribute("mensagens", emailService.listarMensagensDoUsuario(user, "123456", folder));
    }
}