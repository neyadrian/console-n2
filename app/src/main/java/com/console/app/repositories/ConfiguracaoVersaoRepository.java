package com.console.app.repositories;

import com.console.app.models.ConfiguracaoVersao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConfiguracaoVersaoRepository extends JpaRepository<ConfiguracaoVersao, Long> {
    List<ConfiguracaoVersao> findTop15ByOrderByAplicadoEmDesc();
    Optional<ConfiguracaoVersao> findTopByServicoAndParametroAndRevertidaFalseOrderByAplicadoEmDesc(String servico, String parametro);
}