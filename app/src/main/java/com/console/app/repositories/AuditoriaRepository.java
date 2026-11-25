package com.console.app.repositories;

import com.console.app.models.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findAllByOrderByInstanteDesc();
    List<RegistroAuditoria> findByUsuarioOrderByInstanteDesc(String usuario);
}
