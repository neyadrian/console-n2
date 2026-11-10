package com.console.app.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class SshService {

    @Value("${debian.server.ssh.host:192.168.100.10}")
    private String host;

    @Value("${debian.server.ssh.port:22}")
    private int port;

    @Value("${debian.server.ssh.user:vboxuser}")
    private String user;

    // 1. ALTERADO: Defina a senha real do Debian no fallback (ex: 123456)
    @Value("${debian.server.ssh.password:123456}")
    private String password;

    // Obter o hostname estático atual do Debian
    public String getCurrentHostname() {
        return executeCommand("hostnamectl --static");
    }

    // Alterar o hostname temporariamente
    public String setHostname(String newHostname) {
        if (newHostname == null || !newHostname.matches("^[a-zA-Z0-9-]+$")) {
            return "Erro: Nome de hostname inválido.";
        }
        String command = "sudo hostnamectl set-hostname " + newHostname;
        return executeCommand(command);
    }

    // 2. NOVOS MÉTODOS: Coleta tratada de métricas do sistema Debian
    public Map<String, String> getMetrics() {
        Map<String, String> metrics = new HashMap<>();

        // Leitura de CPU (%)
        String cpu = executeCommand("top -bn1 | grep 'Cpu(s)' | awk '{print 100 - $8\"%\"}'");
        metrics.put("cpuUsage", (cpu.isEmpty() || cpu.startsWith("Erro")) ? "0%" : cpu);
        metrics.put("cpu", metrics.get("cpuUsage"));

        // Leitura de RAM (%)
        String ram = executeCommand("free | awk '/Mem:/ {printf(\"%.0f%%\", $3/$2*100)}'");
        metrics.put("memoryUsage", (ram.isEmpty() || ram.startsWith("Erro")) ? "0%" : ram);
        metrics.put("ram", metrics.get("memoryUsage"));

        // Leitura de Disco (/srv ou raiz /)
        String disk = executeCommand("df -h /srv 2>/dev/null | awk 'NR==2 {print $3 \" / \" $2}'");
        if (disk.isEmpty() || disk.startsWith("Erro")) {
            disk = executeCommand("df -h / | awk 'NR==2 {print $3 \" / \" $2}'");
        }
        metrics.put("diskUsage", disk.isEmpty() ? "Erro" : disk);

        // Leitura de Uptime
        String uptime = executeCommand("uptime -p | sed 's/up //'");
        metrics.put("uptime", uptime.isEmpty() ? "Erro" : uptime);

        return metrics;
    }

    // Método auxiliar genérico para executar comandos via JSch
    public String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }
                Thread.sleep(100);
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            return "Erro SSH: " + e.getMessage();
        }
        return output.toString().trim();
    }
}