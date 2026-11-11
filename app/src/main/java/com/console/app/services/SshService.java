package com.console.app.services;

import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
public class SshService {

    @Value("${debian.server.ip:192.168.100.10}")
    private String host;
    @Value("${debian.server.ssh.port:22}")
    private int port;
    @Value("${debian.server.ssh.user:vboxuser}")
    private String user;
    @Value("${debian.server.ssh.password:123456}")
    private String password;

    private volatile String originalHostname = null;

    public String getCurrentHostname() {
        return executeCommand("hostname").trim();
    }

    public String getOriginalHostname() {
        if (originalHostname == null) {
            originalHostname = getCurrentHostname();
        }
        return originalHostname;
    }

    public String setHostname(String newHostname) {
        String res = executeCommand("echo '" + password + "' | sudo -S hostnamectl set-hostname " + newHostname);
        return res.contains("Erro") ? res : "Hostname alterado para: " + newHostname;
    }

    public String restoreOriginalHostname() {
        String orig = getOriginalHostname();
        String res = executeCommand("echo '" + password + "' | sudo -S hostnamectl set-hostname " + orig);
        return res.contains("Erro") ? res : "Hostname restaurado para: " + orig;
    }

    public Map<String, String> getMetrics() {
        Map<String, String> metrics = new HashMap<>();
        try {
            // 1. CPU Real
            String cpuOut = executeCommand("export LC_ALL=C; top -bn1 | grep 'Cpu(s)' | awk '{print 100 - $8}'").trim();
            metrics.put("cpu", cpuOut.isEmpty() ? "0%" : cpuOut + "%");

            // 2. RAM Real (Lê o comando free -m linha a linha)
            String freeOut = executeCommand("export LC_ALL=C; free -m").trim();
            String[] freeLines = freeOut.split("\n");
            for (String line : freeLines) {
                if (line.contains("Mem:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        double total = Double.parseDouble(parts[1]);
                        double used = Double.parseDouble(parts[2]);
                        int percent = (int) ((used / total) * 100);
                        metrics.put("ram", percent + "%");
                        metrics.put("ramDetails", (int)used + " MB / " + (int)total + " MB");
                    }
                }
            }

            // 3. Disco Real (Lê o df -h da raiz)
            String dfOut = executeCommand("export LC_ALL=C; df -h /").trim();
            String[] dfLines = dfOut.split("\n");
            if (dfLines.length >= 2) {
                String[] dfParts = dfLines[1].trim().split("\\s+");
                if (dfParts.length >= 4) {
                    metrics.put("diskUsage", dfParts[2] + " / " + dfParts[1]);
                }
            }

            // 4. Uptime e Hostname
            metrics.put("uptime", executeCommand("uptime -p").replace("up ", "").trim());
            metrics.put("hostname", executeCommand("hostname").trim());

            // 5. Estado dos Serviços
            metrics.put("svc_apache2", executeCommand("systemctl is-active apache2").trim());
            metrics.put("svc_vsftpd", executeCommand("systemctl is-active vsftpd").trim());
            metrics.put("svc_postfix", executeCommand("systemctl is-active postfix").trim());
            metrics.put("svc_dovecot", executeCommand("systemctl is-active dovecot").trim());
            metrics.put("svc_ssh", executeCommand("systemctl is-active ssh").trim());

        } catch (Exception e) {
            metrics.put("erro", "Falha ao processar métricas: " + e.getMessage());
        }
        return metrics;
    }

    /**
     * Consulta inteligente dos Logs Técnicos no Debian.
     * Diferencia o Apache (ficheiro direto via tail) dos demais serviços (journalctl).
     */
    public String obterLogs(String servico, int quantidadeLinhas) {
        int qtd = (quantidadeLinhas > 0) ? quantidadeLinhas : 50;
        String s = (servico != null) ? servico.toLowerCase().trim() : "geral";
        String comando;

        switch (s) {
            case "apache2":
            case "apache":
            case "http":
                // O Apache grava no access.log do sistema
                comando = "echo '" + password + "' | sudo -S tail -n " + qtd + " /var/log/apache2/access.log";
                break;

            case "vsftpd":
            case "ftp":
                // Tenta pelo journalctl, se vazio tenta pelo ficheiro de log direto
                comando = "echo '" + password + "' | sudo -S journalctl -u vsftpd -n " + qtd + " --no-pager || echo '" + password + "' | sudo -S tail -n " + qtd + " /var/log/vsftpd.log";
                break;

            case "postfix":
            case "smtp":
                comando = "echo '" + password + "' | sudo -S journalctl -u postfix -n " + qtd + " --no-pager";
                break;

            case "dovecot":
            case "imap":
                comando = "echo '" + password + "' | sudo -S journalctl -u dovecot -n " + qtd + " --no-pager";
                break;

            case "ssh":
            case "sshd":
                comando = "echo '" + password + "' | sudo -S journalctl -u ssh -n " + qtd + " --no-pager";
                break;

            default:
                // Visão Geral (logs do journalctl)
                comando = "echo '" + password + "' | sudo -S journalctl -n " + qtd + " --no-pager";
                break;
        }

        return executeCommand(comando);
    }

    public String executeCommand(String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(3000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect(3000);

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    outStream.write(tmp, 0, i);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }
                Thread.sleep(50);
            }
            return outStream.toString("UTF-8").trim();
        } catch (Exception e) {
            return "Erro SSH: " + e.getMessage();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    public String uploadSftp(String remoteDir, String filename, InputStream inputStream) { return null; }
    public List<String> listSftp(String remoteDir) { return new ArrayList<>(); }
}