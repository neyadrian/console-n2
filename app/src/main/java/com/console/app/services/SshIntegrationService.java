package com.console.app.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@Service
public class SshIntegrationService {

    private String host = "192.168.100.10";
    private String user = "vboxuser";
    private String password = "123456"; // Ou a sua senha configurada

    // Método genérico para executar comandos SSH
    public String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            output.append("Erro ao executar comando SSH: ").append(e.getMessage());
        }
        return output.toString().trim();
    }

    // Consulta o hostname estático atual do Debian
    public String getCurrentHostname() {
        return executeCommand("hostnamectl --static");
    }

    // Altera o hostname no Debian com privilégios sudo
    public String updateHostname(String newHostname) {
        // Validação simples para evitar caracteres indesejados
        if (newHostname == null || !newHostname.matches("^[a-zA-Z0-9-]+$")) {
            return "Erro: Nome de host inválido.";
        }
        String command = "echo " + password + " | sudo -S hostnamectl set-hostname " + newHostname;
        return executeCommand(command);
    }
}