package com.console.app.controllers;

import com.console.app.services.ImapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class EmailController {

    @Autowired
    private ImapService imapService;

    @GetMapping("/email/caixa-entrada")
    public String verCaixaEntrada(Model model) {
        // Utilize a conta de teste autorizada (ex: aluno@empresa.test)
        List<String> inbox = imapService.listarMensagens("192.168.100.10", "aluno", "sua_senha", "INBOX");
        model.addAttribute("mensagens", inbox);
        model.addAttribute("pasta", "Caixa de Entrada (INBOX)");
        return "email-lista"; // Nome da página HTML
    }

    @GetMapping("/email/enviados")
    public String verEnviados(Model model) {
        List<String> enviados = imapService.listarMensagens("192.168.100.10", "aluno", "sua_senha", "Sent");
        model.addAttribute("mensagens", enviados);
        model.addAttribute("pasta", "Mensagens Enviadas (Sent)");
        return "email-lista";
    }
}