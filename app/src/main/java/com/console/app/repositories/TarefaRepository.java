package com.console.app.repositories;

import com.console.app.models.TarefaOperacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TarefaRepository extends JpaRepository<TarefaOperacao, Long> {
    List<TarefaOperacao> findTop20ByOrderByCriadaEmDesc();
}