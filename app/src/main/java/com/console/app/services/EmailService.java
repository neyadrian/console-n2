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

    @Value("${spring.mail.username:aluno@empresa.test}")
    private String emailOrigem;

    @Value("${app.imap.host:192.168.100.10}")
    private String imapHost;

    @Value("${app.imap.port:143}")
    private int imapPort;

    @Value("${app.imap.username:aluno}")
    private String imapUsername;

    @Value("${app.imap.password:123456}")
    private String imapPassword;

    // Envio simples via SMTP
    public void enviarEmail(String para, String assunto, String mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(para);
        email.setSubject(assunto);
        email.setText(mensagem);
        email.setFrom(emailOrigem);
        mailSender.send(email);
    }

    // Sobrecarga de 3 parâmetros (compatibilidade)
    public void enviarESalvarCopia(String para, String assunto, String texto) throws Exception {
        enviarESalvarCopia(emailOrigem, para, assunto, texto);
    }

    // Sobrecarga de 4 parâmetros chamada pelo EmailController (remetente, destinatario, assunto, mensagem)
    public void enviarESalvarCopia(String remetente, String para, String assunto, String texto) throws Exception {
        String from = (remetente != null && !remetente.isBlank()) ? remetente : emailOrigem;

        // 1. Envio via SMTP
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(para);
            email.setSubject(assunto);
            email.setText(texto);
            email.setFrom(from);
            mailSender.send(email);
        } catch (Exception e) {
            throw new RuntimeException("Falha no envio SMTP: " + e.getMessage());
        }

        // 2. Gravação da cópia via IMAP (Porta 143 sem SSL/TLS)
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imap");
            props.put("mail.imap.host", imapHost);
            props.put("mail.imap.port", String.valueOf(imapPort));
            props.put("mail.imap.connectiontimeout", "10000");
            props.put("mail.imap.timeout", "10000");

            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imap");

            String user = from.contains("@") ? from.substring(0, from.indexOf("@")) : imapUsername;
            String pass = imapPassword;

            store.connect(imapHost, imapPort, user, pass);

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

            MimeMessage mimeMessage = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(texto);

            folder.appendMessages(new Message[]{mimeMessage});

            folder.close(false);
            store.close();

        } catch (Exception e) {
            throw new RuntimeException("E-mail enviado por SMTP com sucesso, mas ocorreu falha ao salvar a cópia na pasta Enviados (IMAP): " + e.getMessage());
        }
    }

    public List<String> listarMensagensDoUsuario(String usuario, String senha, String nomePasta) {
        List<String> lista = new ArrayList<>();
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imap");
            props.put("mail.imap.host", imapHost);
            props.put("mail.imap.port", String.valueOf(imapPort));
            props.put("mail.imap.connectiontimeout", "10000");
            props.put("mail.imap.timeout", "10000");

            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imap");

            String user = (usuario != null && !usuario.isBlank()) ? usuario : imapUsername;
            String pass = (senha != null && !senha.isBlank()) ? senha : imapPassword;

            store.connect(imapHost, imapPort, user, pass);

            Folder folder = store.getFolder(nomePasta);
            if (folder.exists()) {
                folder.open(Folder.READ_ONLY);
                Message[] messages = folder.getMessages();

                int inicio = Math.max(0, messages.length - 10);
                for (int i = messages.length - 1; i >= inicio; i--) {
                    Message msg = messages[i];
                    String remetente = (msg.getFrom() != null && msg.getFrom().length > 0) ? msg.getFrom()[0].toString() : "Desconhecido";

                    String corpo = "";
                    try {
                        Object content = msg.getContent();
                        if (content != null) {
                            corpo = content.toString().trim();
                            if (corpo.length() > 60) {
                                corpo = corpo.substring(0, 60) + "...";
                            }
                        }
                    } catch (Exception ex) {
                        corpo = "[Conteúdo não texto ou indisponível]";
                    }

                    lista.add(msg.getSubject() + " | De: " + remetente + " | Conteúdo: " + corpo);
                }
                folder.close(false);
            }
            store.close();
        } catch (Exception e) {
            lista.add("Aviso IMAP: " + e.getMessage());
        }
        return lista;
    }

    public List<String> listarMensagens(String nomePasta) {
        return listarMensagensDoUsuario(imapUsername, imapPassword, nomePasta);
    }
}