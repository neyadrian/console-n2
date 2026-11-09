package com.console.app.services;

import jakarta.mail.*;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImapService {

    public List<String> listarMensagens(String host, String user, String password, String pasta) {
        List<String> mensagensList = new ArrayList<>();
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");

        try {
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect(host, user, password);

            Folder folder = store.getFolder(pasta); // Ex: "INBOX" ou "Sent"
            folder.open(Folder.READ_ONLY);

            Message[] messages = folder.getMessages();
            for (Message msg : messages) {
                String info = "Assunto: " + msg.getSubject() + " | De: " + msg.getFrom()[0];
                mensagensList.add(info);
            }

            folder.close(false);
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
            mensagensList.add("Erro ao consultar IMAP: " + e.getMessage());
        }

        return mensagensList;
    }
}