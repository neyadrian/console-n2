package com.console.app.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracoes_versoes")
public class ConfiguracaoVersao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String servico;

    @Column(nullable = false)
    private String parametro;

    @Column(nullable = false)
    private String valorAnterior;

    @Column(nullable = false)
    private String valorNovo;

    @Column(nullable = false)
    private String aplicadoPor;

    @Column(nullable = false)
    private LocalDateTime aplicadoEm = LocalDateTime.now();

    private boolean revertida = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServico() { return servico; }
    public void setServico(String servico) { this.servico = servico; }
    public String getParametro() { return parametro; }
    public void setParametro(String parametro) { this.parametro = parametro; }
    public String getValorAnterior() { return valorAnterior; }
    public void setValorAnterior(String valorAnterior) { this.valorAnterior = valorAnterior; }
    public String getValorNovo() { return valorNovo; }
    public void setValorNovo(String valorNovo) { this.valorNovo = valorNovo; }
    public String getAplicadoPor() { return aplicadoPor; }
    public void setAplicadoPor(String aplicadoPor) { this.aplicadoPor = aplicadoPor; }
    public LocalDateTime getAplicadoEm() { return aplicadoEm; }
    public void setAplicadoEm(LocalDateTime aplicadoEm) { this.aplicadoEm = aplicadoEm; }
    public boolean isRevertida() { return revertida; }
    public void setRevertida(boolean revertida) { this.revertida = revertida; }
}
