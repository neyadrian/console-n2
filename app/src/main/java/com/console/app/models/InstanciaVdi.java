package com.console.app.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "instancias_vdi")
public class InstanciaVdi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nome;

    private String uuidVirtualBox;
    private String imagemBase = "BASE-LINUX-v1";
    private String perfil;
    private int cpus;
    private int ramMb;
    private String proprietario;
    private String ipAtribuido;

    private String estado;
    private LocalDateTime criadaEm = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getUuidVirtualBox() { return uuidVirtualBox; }
    public void setUuidVirtualBox(String uuidVirtualBox) { this.uuidVirtualBox = uuidVirtualBox; }
    public String getImagemBase() { return imagemBase; }
    public void setImagemBase(String imagemBase) { this.imagemBase = imagemBase; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public int getCpus() { return cpus; }
    public void setCpus(int cpus) { this.cpus = cpus; }
    public int getRamMb() { return ramMb; }
    public void setRamMb(int ramMb) { this.ramMb = ramMb; }
    public String getProprietario() { return proprietario; }
    public void setProprietario(String proprietario) { this.proprietario = proprietario; }
    public String getIpAtribuido() { return ipAtribuido; }
    public void setIpAtribuido(String ipAtribuido) { this.ipAtribuido = ipAtribuido; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
}