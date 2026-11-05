package com.console.app.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Scanner;

@Service
public class SshIntegrationService {

    @Value("${debian.server.ip}")
    private String serverIp;

    @Value("${debian.server.ssh.user}")
    private String sshUser;

    // Caminho para a sua chave privada no Windows (ajuste se necessário)
    private final String privateKeyPath = System.getProperty("user.home") + "/.ssh/id_ed25519_redes_n1";

    public String executeCommand(String command) {
        try {
            JSch jsch = new JSch();

            // 1. COMENTE OU APAGUE A LINHA DA CHAVE:
            // jsch.addIdentity(privateKeyPath);

            Session session = jsch.getSession(sshUser, serverIp, 22);

            // 2. ADICIONE A SENHA DIRETAMENTE AQUI (coloque a senha real do seu Debian):
            session.setPassword("123");

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            // ... (o resto do código fica exatamente igual)
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect();

            // Lê a resposta do comando Linux
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
