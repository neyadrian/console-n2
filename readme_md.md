# Console de Administração de Serviços — N2 Parte 1 (Redes de Computadores II)

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Debian](https://img.shields.io/badge/Debian-13-blue.svg)
![Ubuntu](https://img.shields.io/badge/Ubuntu-24.04%20LTS-orange.svg)
![License](https://img.shields.io/badge/License-Academic-lightgrey.svg)

Sistema web centralizado desenvolvido para a disciplina de **Redes de Computadores II** (IFCE — Campus Fortaleza / Engenharia de Software) ministrada pelo **Prof. Matias**. O console permite gerenciar, visualizar métricas em tempo real e orquestrar serviços de rede hospedados em um servidor **Debian 13** a partir de uma interface web executada no cliente **Ubuntu**.

---

## 📋 Sumário
1. [Arquitetura do Ambiente](#-arquitetura-do-ambiente)
2. [Funcionalidades Implementadas](#-funcionalidades-implementadas)
3. [Tecnologias Utilizadas](#-tecnologias-utilizadas)
4. [Estrutura do Repositório](#-estrutura-do-repositório)
5. [Instruções de Execução e Deploy](#-instruções-de-execução-e-deploy)
6. [Credenciais e Configurações de Exemplo](#-credenciais-e-configurações-de-exemplo)
7. [Ficha de Verificação e Testes práticos](#-ficha-de-verificação-e-testes-práticos)
8. [Segurança e Tratamento de Exceções](#-segurança-e-tratamento-de-exceções)

---

## 📐 Arquitetura do Ambiente

A infraestrutura é composta por duas máquinas virtuais em rede interna isolada no VirtualBox (`lab`):

```
+------------------------------------+           +------------------------------------+
|         VM UBUNTU (Cliente)        |           |        VM DEBIAN (Servidor)        |
|        IP: 192.168.100.20          |           |        IP: 192.168.100.10          |
|                                    |           |                                    |
| +--------------------------------+ |  SSH/SFTP | +--------------------------------+ |
| |   Spring Boot Web Application  | | --------> | | OpenSSH Daemon (Porta 22)      | |
| |   (Dashboard / Management)     | |   HTTP    | +--------------------------------+ |
| |   Porta: 8080                  | | --------> | | Apache Web Server (Porta 80)    | |
| +--------------------------------+ |   FTP     | +--------------------------------+ |
|                                    | --------> | | vsftpd FTP Server (Porta 21)   | |
| +--------------------------------+ |   SMTP    | +--------------------------------+ |
| |   Navegador Web (Firefox)      | | --------> | | Postfix Mail Server (Porta 25) | |
| +--------------------------------+ |   IMAP    | +--------------------------------+ |
|                                    | --------> | | Dovecot IMAP Server (Porta 143) | |
+------------------------------------+           +------------------------------------+
```

* **Servidor Debian (`192.168.100.10`)**: Executa os serviços essenciais de infraestrutura (Apache, vsftpd, Postfix, Dovecot e OpenSSH).
* **Cliente Ubuntu (`192.168.100.20`)**: Hospeda a aplicação Spring Boot e provê acesso visual aos operadores via navegador.

---

## ✨ Funcionalidades Implementadas

### 1. Dashboard e Visão Geral do Servidor
* **Métricas em Tempo Real**: Telemetria ao vivo contendo porcentagem de CPU usada, consumo de Memória RAM (`MB usados / Total`), ocupação de Disco (`/`), Uptime e Hostname do Debian via SSH seguro.
* **Saúde dos Serviços**: Verificação do estado operacional dos 5 serviços (`active`/`inactive`).
* **Latência HTTP**: Medição em tempo real da resposta do Apache HTTP com status code (ex: `200 OK`) e latência em milissegundos.
* **Gráficos Dinâmicos**: Histórico visual recente de amostras de CPU e Memória RAM.

### 2. Módulo FTP e Publicação Web (vsftpd + Apache)
* **Upload via FTP**: Envio de arquivos diretamente do navegador para o diretório de publicação remota (`/srv/portal/arquivos`).
* **Validação SHA-256**: Cálculo automatizado do hash do arquivo enviado e comparação com a cópia recuperada via HTTP e FTP, garantindo **100% de integridade da transferência**.
* **Listagem e Download**: Visualização dos arquivos publicados no servidor com suporte a download direto pelo console.

### 3. Módulo de Correio (SMTP Postfix + IMAP Dovecot)
* **Envio SMTP (Porta 25)**: Composição e despacho de e-mails entre contas autorizadas (`aluno@empresa.test` e `suporte@empresa.test`).
* **Leitura IMAP (Porta 143)**: Leitura das mensagens da caixa de entrada (*INBOX*) extraindo Assunto, Remetente e Trecho do Corpo da Mensagem.
* **Cópia em Enviados (*Sent*)**: Gravação automática e persistente da mensagem enviada na pasta de enviados do servidor Dovecot.

### 4. Gestão SSH Remota (OpenSSH)
* **Alteração Reversível de Hostname**: Interface dedicada para mudar o nome estático do servidor Debian via `hostnamectl` e funcionalidade para restaurar instantaneamente o hostname original salvo.
* **Conexão não-interativa segura**: Uso da biblioteca JSch com execução de comandos predefinidos de privilégio limitado.

### 5. Logs Técnicos do Sistema
* **Filtro por Serviço**: Leitura sob demanda dos logs individuais de cada componente (`Apache`, `vsftpd`, `Postfix`, `Dovecot`, `OpenSSH`).
* **Tratamento Específico**: Diferenciação transparente no backend entre logs do `journalctl` (Systemd) e arquivos de log diretos do Apache (`/var/log/apache2/access.log` via `tail`).
* **Limitação Configurável**: Seleção dinâmica da quantidade de linhas consultadas (ex: 10 a 200 linhas).

### 6. Autenticação e Controle de Acesso (RBAC)
* **Perfis de Usuário**: Suporte a perfil `ADMINISTRADOR`, `OPERADOR` e `LEITOR`.
* **Segurança no Backend**: Validação estrita de permissões de escrita/execução diretamente na camada de serviço (serviços bloqueados para o perfil LEITOR mesmo em acesso direto via URL).

---

## 🛠️ Tecnologias Utilizadas

* **Linguagem & Framework**: Java 17, Spring Boot 3.x, Spring MVC, Spring Security.
* **Template Engine**: Thymeleaf + Tailwind CSS / Vanilla CSS.
* **Bibliotecas de Protocolo**:
  * **SSH / SFTP**: JSch (`com.jcraft:jsch:0.1.55`)
  * **E-mail (SMTP/IMAP)**: Jakarta Mail / JavaMail (`org.eclipse.angus:jakarta.mail`)
  * **FTP**: Apache Commons Net (`commons-net:commons-net:3.9.0`)
* **Gráficos**: Chart.js / SVG Dinâmico.

---

## 📁 Estrutura do Repositório

```text
console-n2/
├── src/
│   ├── main/
│   │   ├── java/com/console/app/
│   │   │   ├── controllers/       # Controladores Web (Dashboard, Email, Ftp, Ssh, Logs)
│   │   │   ├── services/          # Camada de Integração (SshService, EmailService, FtpService)
│   │   │   ├── config/            # Configurações de Segurança e Beans
│   │   │   └── AppApplication.java
│   │   └── resources/
│   │       ├── templates/         # Páginas HTML (Visão Geral, E-mail, FTP, SSH, Logs)
│   │       ├── static/            # CSS, JS e Assets locais (Sem dependência de CDN)
│   │       └── application.properties
├── pom.xml                        # Ficheiro de dependências do Maven
└── README.md                      # Documentação do projeto
```

---

## 🚀 Instruções de Execução e Deploy

### 1. Compilação do Projeto
No ambiente de desenvolvimento (Windows ou Ubuntu com Maven instalado):
```bash
mvn clean package -DskipTests
```
Isso gerará o artefato executável em `target/console-app-1.0.0.jar`.

### 2. Implantação e Execução no Ubuntu (`192.168.100.20`)
Copie o arquivo `.jar` para o diretório home do usuário `vboxuser` e inicie o serviço do sistema:

```bash
# Copiar o artefato executável
cp console-app-1.0.0.jar ~/app.jar

# Reiniciar o serviço systemd configurado
sudo systemctl restart console-n2.service
```

Para verificar o status da aplicação:
```bash
sudo journalctl -u console-n2.service -f
```

### 3. Acesso à Interface Web
Abra o navegador Firefox no Ubuntu e acesse:
```text
http://localhost:8080/dashboard
```

---

## 🔑 Credenciais e Configurações de Exemplo

> **Nota**: O projeto roda exclusivamente em ambiente de laboratório isolado sem exposição pública.

### Contas do Console Web (Spring Security)
| Usuário | Senha | Perfil | Permissões |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | ADMINISTRADOR | Acesso total e alterações de sistema |
| `operador` | `operador123` | OPERADOR | Operações de FTP, Envio de E-mail e Logs |
| `leitor` | `leitor123` | LEITOR | Apenas visualização e consulta de dados |

### Contas do Servidor Debian (`192.168.100.10`)
* **SSH / Sudo**: Usuário `vboxuser` | Senha: `123456`
* **Contas de E-mail (Postfix/Dovecot)**:
  * `aluno@empresa.test` | Senha: `123456`
  * `suporte@empresa.test` | Senha: `123456`
* **FTP (vsftpd)**:
  * Usuário `ftpuser` | Senha: `123456` | Diretório: `/srv/portal/arquivos`

---

## 🧪 Ficha de Verificação e Testes Práticos

Para homologação pela banca avaliadora, execute a seguinte sequência de checagens:

| Item | Ação de Teste | Resultado Esperado | Status |
| :---: | :--- | :--- | :---: |
| **1** | Acessar `/dashboard` | Exibição de CPU, RAM, Disco, Uptime reais e latência HTTP (~8ms). | **OK** |
| **2** | Upload de arquivo na aba *Arquivos (FTP)* | Envio via FTP com publicação imediata em HTTP e Hashes SHA-256 coincidentes. | **OK** |
| **3** | Envio de e-mail na aba *E-mail* | Mensagem enviada via SMTP, visualizada em *INBOX* e salva na pasta *Sent*. | **OK** |
| **4** | Alterar Hostname na aba *Acesso SSH* | Nome estático do Debian alterado via `hostnamectl` e posteriormente restaurado. | **OK** |
| **5** | Consultar Logs na aba *Logs Técnicos* | Filtro funcional individual para Apache (`tail`), SSH, Dovecot, Postfix e vsftpd (`journalctl`). | **OK** |
| **6** | Login com perfil `leitor` | Bloqueio de ações de escrita/alteração no backend com mensagem informativa. | **OK** |

---

## 🛡️ Segurança e Tratamento de Exceções

* **Prevenção contra Command Injection**: Nenhum comando enviado ao terminal é montado via concatenação direta de entradas não sanitizadas do usuário.
* **Isolamento de Credenciais**: Chaves e senhas de serviço são gerenciadas via variáveis de ambiente/properties protegidas no backend.
* **Tratamento de Timeouts**: Conexões de rede (IMAP, SSH, HTTP) possuem timeouts curtos e resilientes (3s a 10s), impedindo o travamento da interface em caso de indisponibilidade momentânea do servidor.
* **Princípio do Menor Privilégio**: O backend executa como usuário comum sem sudo irrestrito, invocando comandos administrativos estritamente identificados (`hostnamectl`, `tail`, `journalctl`).

---
*Desenvolvido por **Ney Adrian** — IFCE 2026*