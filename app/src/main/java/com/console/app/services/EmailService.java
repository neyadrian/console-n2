package com.console.app.services;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String emailOrigem;

    @Value("${app.imap.host:192.168.100.10}")
    private String imapHost;

    @Value("${app.imap.port:993}")
    private int imapPort;

    @Value("${app.imap.username:vboxuser}")
    private String imapUsername;

    @Value("${app.imap.password:sua_senha}")
    private String imapPassword;

    // --- ENVIO DE E-MAIL VIA SMTP (GMAIL) ---
    public void enviarEmail(String para, String assunto, String mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(para);
        email.setSubject(assunto);
        email.setText(mensagem);
        email.setFrom(emailOrigem); // Usa automaticamente neyadriancoliveira@gmail.com

        mailSender.send(email);
    }

    // --- LEITURA DE MENSAGENS VIA IMAP (DOVECOT DEBIAN) ---
    public List<String> listarMensagens(String nomePasta) {
        List<String> lista = new ArrayList<>();
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", imapHost);
            props.put("mail.imaps.port", String.valueOf(imapPort));
            props.put("mail.imaps.ssl.trust", "*");

            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect(imapHost, imapPort, imapUsername, imapPassword);

            Folder folder = store.getFolder(nomePasta); // Ex: "INBOX" ou "Sent"
            if (folder.exists()) {
                folder.open(Folder.READ_ONLY);
                Message[] messages = folder.getMessages();

                // Obter as 10 mensagens mais recentes
                int inicio = Math.max(0, messages.length - 10);
                for (int i = messages.length - 1; i >= inicio; i--) {
                    Message msg = messages[i];
                    lista.add(msg.getSubject() + " | De: " + msg.getFrom()[0].toString());
                }
                folder.close(false);
            }
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
            lista.add("Aviso IMAP: " + e.getMessage());
        }
        return lista;
    }
}