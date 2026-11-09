package com.console.app.controllers;

import com.console.app.services.FtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class FtpController {

    @Autowired
    private FtpService ftpService;

    @GetMapping("/ftp")
    public String paginaFtp() {
        return "ftp-view"; // Página HTML do módulo FTP
    }

    @PostMapping("/ftp/upload")
    public String uploadArquivo(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("mensagem", "Por favor, selecione um ficheiro.");
            return "ftp-view";
        }

        try {
            // Calcular o hash SHA-256 do ficheiro original enviado
            String sha256Original = ftpService.calcularSha256(file.getInputStream());

            // Enviar via FTP para o vsftpd
            boolean sucesso = ftpService.uploadFile(file);

            if (sucesso) {
                model.addAttribute("mensagem", "Ficheiro enviado com sucesso via FTP!");
                model.addAttribute("hash", sha256Original);
                model.addAttribute("nomeArquivo", file.getOriginalFilename());
            } else {
                model.addAttribute("mensagem", "Falha na transferência FTP.");
            }
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro: " + e.getMessage());
        }

        return "ftp-view";
    }
}