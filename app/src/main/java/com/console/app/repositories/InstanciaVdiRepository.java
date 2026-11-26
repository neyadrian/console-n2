package com.console.app.repositories;

import com.console.app.models.InstanciaVdi;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InstanciaVdiRepository extends JpaRepository<InstanciaVdi, Long> {
    List<InstanciaVdi> findAllByOrderByCriadaEmDesc();
    long countByProprietarioAndEstadoNotIn(String proprietario, List<String> estadosFinais);
    long countByEstadoIn(List<String> estadosAtivos);
}