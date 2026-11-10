package com.console.app.services;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    // --- ENVIO DE E-MAIL SIMPLES VIA SMTP ---
    public void enviarEmail(String para, String assunto, String mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(para);
        email.setSubject(assunto);
        email.setText(mensagem);
        email.setFrom(emailOrigem);

        mailSender.send(email);
    }

    // --- ENVIO DE SMTP + GRAVAÇÃO NA PASTA "Sent" (IMAP) ---
    // Atende rigorosamente ao requisito 2.4 do documento da N2 Parte 1
    public void enviarESalvarCopia(String para, String assunto, String texto) throws Exception {
        // 1. Envio normal via SMTP
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(para);
            email.setSubject(assunto);
            email.setText(texto);
            email.setFrom(emailOrigem);
            mailSender.send(email);
        } catch (Exception e) {
            throw new RuntimeException("Falha no envio SMTP: " + e.getMessage());
        }

        // 2. Gravação da cópia na pasta IMAP "Sent" (Enviados)
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", imapHost);
            props.put("mail.imaps.port", String.valueOf(imapPort));
            props.put("mail.imaps.ssl.trust", "*");

            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect(imapHost, imapPort, imapUsername, imapPassword);

            // Tenta abrir a pasta de Enviados (pode ser "Sent" ou "INBOX.Sent" dependendo do Dovecot)
            Folder folder;
            try {
                folder = store.getFolder("Sent");
                if (!folder.exists()) {
                    folder = store.getFolder("INBOX.Sent");
                }
            } catch (Exception e) {
                folder = store.getFolder("INBOX.Sent");
            }

            if (!folder.exists()) {
                folder.create(Folder.HOLDS_MESSAGES);
            }

            folder.open(Folder.READ_WRITE);

            // Constrói a mensagem MIME para injetar na pasta IMAP
            MimeMessage mimeMessage = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(emailOrigem);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(texto);

            folder.appendMessages(new Message[]{mimeMessage});

            folder.close(false);
            store.close();

        } catch (Exception e) {
            // Distingue o erro de salvamento da cópia conforme exigido no ponto 2.4 do manual
            throw new RuntimeException("E-mail enviado por SMTP com sucesso, mas ocorreu falha ao salvar a cópia na pasta Enviados (IMAP): " + e.getMessage());
        }
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

                // Obter as 10 mensagens mais recentes (Corrigido o operador de subtração)
                int inicio = Math.max(0, messages.length - 10);
                for (int i = messages.length - 1; i >= inicio; i--) {
                    Message msg = messages[i];
                    String remetente = (msg.getFrom() != null && msg.getFrom().length > 0) ? msg.getFrom()[0].toString() : "Desconhecido";
                    lista.add(msg.getSubject() + " | De: " + remetente);
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