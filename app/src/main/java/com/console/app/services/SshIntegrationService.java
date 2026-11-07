package com.console.app.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

@Service
public class SshIntegrationService {

    @Value("${debian.server.ip}")
    private String serverIp;

    private String sshUser = "n2user";

    public String executeCommand(String command) {
        try {
            JSch jsch = new JSch();

            Session session = jsch.getSession(sshUser, serverIp, 22);
            session.setPassword("123456");

            // Configurações para ignorar host key e FORÇAR apenas senha
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            session.setConfig(config);

            session.connect(5000);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect();

            Scanner scanner = new Scanner(in).useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";

            channel.disconnect();
            session.disconnect();

            return response.trim();
        } catch (Exception e) {
            System.err.println("Erro ao executar comando SSH: " + e.getMessage());
            return "Erro de conexão";
        }
    }
}