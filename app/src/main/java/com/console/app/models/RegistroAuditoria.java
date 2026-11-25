package com.console.app.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String correlationId = UUID.randomUUID().toString();

    @Column(nullable = false, updatable = false)
    private LocalDateTime instante = LocalDateTime.now();

    @Column(nullable = false, updatable = false)
    private String usuario;

    @Column(nullable = false, updatable = false)
    private String perfil;

    @Column(nullable = false, updatable = false)
    private String recurso;

    @Column(nullable = false, updatable = false)
    private String acao;

    @Column(nullable = false, updatable = false)
    private String resultado;

    @Column(length = 1000, updatable = false)
    private String detalhesSanitizados;

    public RegistroAuditoria() {}

    public RegistroAuditoria(String usuario, String perfil, String recurso, String acao, String resultado, String detalhesSanitizados) {
        this.usuario = usuario;
        this.perfil = perfil;
        this.recurso = recurso;
        this.acao = acao;
        this.resultado = resultado;
        this.detalhesSanitizados = detalhesSanitizados;
    }

    public Long getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public LocalDateTime getInstante() { return instante; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public String getRecurso() { return recurso; }
    public String getAcao() { return acao; }
    public String getResultado() { return resultado; }
    public String getDetalhesSanitizados() { return detalhesSanitizados; }
}
