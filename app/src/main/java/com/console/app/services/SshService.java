package com.console.app.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class SshService {

    @Value("${debian.server.ssh.host:192.168.100.10}")
    private String host;

    @Value("${debian.server.ssh.port:22}")
    private int port;

    @Value("${debian.server.ssh.user:vboxuser}")
    private String user;

    @Value("${debian.server.ssh.password:sua_senha}")
    private String password;

    // Obter o hostname estático atual do Debian
    public String getCurrentHostname() {
        return executeCommand("hostnamectl --static");
    }

    // Alterar o hostname temporariamente
    public String setHostname(String newHostname) {
        // Validação simples para evitar injeção de comandos
        if (newHostname == null || !newHostname.matches("^[a-zA-Z0-9-]+$")) {
            return "Erro: Nome de hostname inválido.";
        }
        // Utilizando sudo para alterar o hostname conforme exigido no laboratório
        String command = "sudo hostnamectl set-hostname " + newHostname;
        return executeCommand(command);
    }

    // Método auxiliar genérico para executar comandos via JSch
    private String executeCommand(String command) {
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