package com.console.app.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tarefas")
public class TarefaOperacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo;
    private String alvo;
    private String status;
    private String solicitadoPor;
    private LocalDateTime criadaEm =  LocalDateTime.now();
    private LocalDateTime finalizadaEm;
    private Long duracaoMs;

    @Column(length = 1000)
    private String mensagemRetorno;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getAlvo() { return alvo; }
    public void setAlvo(String alvo) { this.alvo = alvo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSolicitadoPor() { return solicitadoPor; }
    public void setSolicitadoPor(String solicitadoPor) { this.solicitadoPor = solicitadoPor; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
    public LocalDateTime getFinalizadaEm() { return finalizadaEm; }
    public void setFinalizadaEm(LocalDateTime finalizadaEm) { this.finalizadaEm = finalizadaEm; }
    public Long getDuracaoMs() { return duracaoMs; }
    public void setDuracaoMs(Long duracaoMs) { this.duracaoMs = duracaoMs; }
    public String getMensagemRetorno() { return mensagemRetorno; }
    public void setMensagemRetorno(String mensagemRetorno) { this.mensagemRetorno = mensagemRetorno; }
}
