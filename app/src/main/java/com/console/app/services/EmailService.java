package com.console.app.services;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    @Value("${spring.mail.host:192.168.100.10}")
    private String smtpHost;

    @Value("${spring.mail.port:25}")
    private int smtpPort;

    @Value("${app.imap.host:192.168.100.10}")
    private String imapHost;

    @Value("${app.imap.port:143}")
    private int imapPort;

    @Value("${app.imap.username:aluno}")
    private String defaultImapUser;

    @Value("${app.imap.password:123456}")
    private String defaultImapPass;

    public void enviarESalvarCopia(String remetente, String destinatario, String assunto, String texto) throws Exception {
        if (remetente == null || remetente.isBlank()) {
            remetente = "aluno@empresa.test";
        }

        // 1. Envio normal via SMTP do Debian (Postfix)
        try {
            Properties smtpProps = new Properties();
            smtpProps.put("mail.smtp.host", smtpHost);
            smtpProps.put("mail.smtp.port", String.valueOf(smtpPort));
            smtpProps.put("mail.smtp.auth", "false");
            smtpProps.put("mail.smtp.starttls.enable", "false");
            smtpProps.put("mail.smtp.connectiontimeout", "3000");
            smtpProps.put("mail.smtp.timeout", "3000");

            Session smtpSession = Session.getInstance(smtpProps, null);
            MimeMessage message = new MimeMessage(smtpSession);
            message.setFrom(new InternetAddress(remetente));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            message.setSubject(assunto, "UTF-8");
            message.setText(texto, "UTF-8");

            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Falha no envio SMTP para " + smtpHost + ":" + smtpPort + " -> " + e.getMessage());
        }

        // 2. Gravação da cópia na pasta IMAP "Sent" do remetente
        try {
            String imapUser = remetente.contains("@") ? remetente.substring(0, remetente.indexOf('@')) : remetente;
            salvarCopiaImap(imapUser, defaultImapPass, remetente, destinatario, assunto, texto);
        } catch (Exception e) {
            // Distingue explicitamente o erro ao salvar cópia conforme Seção 2.4 do manual IFCE
            throw new RuntimeException("E-mail aceito e enviado pelo SMTP com sucesso! Porém houve falha ao salvar a cópia na pasta Enviados (IMAP): " + e.getMessage()
                    + " (Atenção: a mensagem já foi enviada, não reenvie para evitar duplicidade).");
        }
    }

    private void salvarCopiaImap(String username, String password, String de, String para, String assunto, String texto) throws Exception {
        Store store = connectImapStore(username, password);
        try {
            Folder folder = null;
            String[] folderNames = {"Sent", "INBOX.Sent", "Enviadas", "Enviados"};
            for (String name : folderNames) {
                try {
                    Folder f = store.getFolder(name);
                    if (f.exists()) {
                        folder = f;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (folder == null) {
                folder = store.getFolder("Sent");
                if (!folder.exists()) {
                    folder.create(Folder.HOLDS_MESSAGES);
                }
            }

            folder.open(Folder.READ_WRITE);

            MimeMessage mimeMessage = new MimeMessage((Session) null);
            mimeMessage.setFrom(new InternetAddress(de));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para));
            mimeMessage.setSubject(assunto, "UTF-8");
            mimeMessage.setText(texto, "UTF-8");

            folder.appendMessages(new Message[]{mimeMessage});
            folder.close(false);
        } finally {
            try { store.close(); } catch (Exception ignored) {}
        }
    }

    public List<String> listarMensagens(String nomePasta) {
        return listarMensagensDoUsuario(defaultImapUser, defaultImapPass, nomePasta);
    }

    public List<String> listarMensagensDoUsuario(String username, String password, String nomePasta) {
        List<String> lista = new ArrayList<>();
        try {
            Store store = connectImapStore(username, password);
            try {
                Folder folder = store.getFolder(nomePasta);
                if (!folder.exists() && "Sent".equalsIgnoreCase(nomePasta)) {
                    folder = store.getFolder("INBOX.Sent");
                }

                if (folder.exists()) {
                    folder.open(Folder.READ_ONLY);
                    Message[] messages = folder.getMessages();
                    int inicio = Math.max(0, messages.length - 15);
                    for (int i = messages.length - 1; i >= inicio; i--) {
                        Message msg = messages[i];
                        String remetente = (msg.getFrom() != null && msg.getFrom().length > 0) ? msg.getFrom()[0].toString() : "Desconhecido";
                        lista.add(msg.getSubject() + " | De: " + remetente);
                    }
                    folder.close(false);
                }
            } finally {
                try { store.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            lista.add("Aviso IMAP (" + username + "@" + imapHost + ":" + imapPort + "): " + e.getMessage());
        }
        return lista;
    }

    private Store connectImapStore(String username, String password) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", imapHost);
        props.put("mail.imap.port", String.valueOf(imapPort > 0 ? imapPort : 143));
        props.put("mail.imap.auth.plain.disable", "false");
        props.put("mail.imap.starttls.enable", "false");
        props.put("mail.imap.ssl.enable", "false");
        props.put("mail.imap.connectiontimeout", "2500");
        props.put("mail.imap.timeout", "2500");

        try {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imap");
            store.connect(imapHost, imapPort > 0 ? imapPort : 143, username, password);
            return store;
        } catch (Exception e1) {
            // Se falhou na 143, checa se porta 993 responde de forma não-bloqueante
            if (imapPort == 993) {
                throw e1;
            }
            try {
                Properties propsSsl = new Properties();
                propsSsl.put("mail.store.protocol", "imaps");
                propsSsl.put("mail.imaps.host", imapHost);
                propsSsl.put("mail.imaps.port", "993");
                propsSsl.put("mail.imaps.ssl.trust", "*");
                propsSsl.put("mail.imaps.connectiontimeout", "1500");
                propsSsl.put("mail.imaps.timeout", "1500");

                Session sessionSsl = Session.getInstance(propsSsl, null);
                Store storeSsl = sessionSsl.getStore("imaps");
                storeSsl.connect(imapHost, 993, username, password);
                return storeSsl;
            } catch (Exception ignored) {
                // Se SSL também falhar, lança a exceção original da porta 143 para diagnóstico real
                throw e1;
            }
        }
    }
}