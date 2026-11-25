package com.console.app.services;

import com.console.app.models.RegistroAuditoria;
import com.console.app.repositories.AuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    public void registrar(String recurso, String acao, String resultado, String detalhes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SISTEMA";
        String perfil = "ANONIMO";

        if (auth != null && auth.getAuthorities() != null) {
            perfil = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.replace("ROLE_", ""))
                    .findFirst()
                    .orElse("DESCONHECIDO");
        }

        // Sanitização básica para garantir que segredos nunca vazem para o banco (Seção 7.3)
        String detalhesLimpos = sanitizar(detalhes);

        RegistroAuditoria log = new RegistroAuditoria(usuario, perfil, recurso, acao, resultado, detalhesLimpos);
        auditoriaRepository.save(log);
    }

    public List<RegistroAuditoria> consultarLogsPorPermissao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));

        // Seção 7: Administrador vê auditoria global; Operador vê apenas as próprias ações
        if (isAdmin) {
            return auditoriaRepository.findAllByOrderByInstanteDesc();
        } else {
            return auditoriaRepository.findByUsuarioOrderByInstanteDesc(auth.getName());
        }
    }

    private String sanitizar(String texto) {
        if (texto == null) return "";
        return texto
                .replaceAll("(?i)(password|senha|token|secret)=[^&\\s]+", "$1=***")
                .substring(0, Math.min(texto.length(), 950));
    }
}
