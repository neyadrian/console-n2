package com.console.app.controllers;

import com.console.app.security.RoleCheck;
import com.console.app.services.FtpService;
import com.console.app.services.FtpService.FtpFileInfo;
import com.console.app.services.FtpService.FtpUploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class FtpController {

    @Autowired
    private FtpService ftpService;

    @GetMapping("/ftp")
    public String paginaFtp() {
        return "redirect:/arquivos";
    }

    @GetMapping("/arquivos")
    public String arquivosPage(Model model) {
        List<FtpFileInfo> arquivos;
        try {
            arquivos = ftpService.listFilesDetailed();
        } catch (Exception e) {
            arquivos = java.util.Collections.emptyList();
            model.addAttribute("error", "Não foi possível listar os arquivos remotos: " + e.getMessage());
        }
        model.addAttribute("arquivos", arquivos);
        model.addAttribute("uploadResult", null);
        return "arquivos";
    }

    @PostMapping({"/arquivos/upload", "/ftp/upload"})
    public String uploadArquivo(@RequestParam("file") MultipartFile file,
                                Model model,
                                Authentication authentication) {
        if (!RoleCheck.canOperate(authentication)) {
            model.addAttribute("error", "Acesso recusado pelo backend: o perfil "
                    + RoleCheck.perfil(authentication)
                    + " não pode enviar ficheiros via FTP. Apenas ADMINISTRADOR e OPERADOR.");
            model.addAttribute("arquivos", ftpService.listFilesDetailed());
            return "arquivos";
        }

        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "Por favor, selecione um ficheiro para enviar.");
            model.addAttribute("arquivos", ftpService.listFilesDetailed());
            return "arquivos";
        }

        FtpUploadResult result = ftpService.uploadFileDetailed(file);
        if (result.isSuccess()) {
            model.addAttribute("uploadResult", result);
            model.addAttribute("message", result.getMessage());
        } else {
            model.addAttribute("error", result.getError());
        }

        model.addAttribute("arquivos", ftpService.listFilesDetailed());
        return "arquivos";
    }

    @GetMapping("/arquivos/download/{filename:.+}")
    public ResponseEntity<Resource> downloadArquivo(@PathVariable("filename") String filename) {
        byte[] data = ftpService.downloadFile(filename);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }
}
