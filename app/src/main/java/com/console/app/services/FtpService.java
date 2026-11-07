package com.console.app.services;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FtpService {

    private String server = "192.168.100.10";
    private int port = 21;
    private String user = "ftpuser"; // Altere se o seu utilizador FTP no Debian for diferente
    private String pass = "123456";  // Senha do utilizador FTP

    // Faz o upload do arquivo recebido pelo formulário web para o diretório /arquivos/ do FTP
    public boolean uploadFile(MultipartFile file) {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(user, pass);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            // Entra na pasta remota de publicação configurada na N1
            ftpClient.changeWorkingDirectory("/arquivos");

            InputStream inputStream = file.getInputStream();
            boolean done = ftpClient.storeFile(file.getOriginalFilename(), inputStream);
            inputStream.close();

            ftpClient.logout();
            ftpClient.disconnect();
            return done;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Lista os arquivos presentes no diretório /arquivos/ do servidor FTP
    public List<String> listFiles() {
        List<String> fileList = new ArrayList<>();
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(user, pass);
            ftpClient.enterLocalPassiveMode();

            FTPFile[] files = ftpClient.listFiles("/arquivos");
            if (files != null) {
                for (FTPFile file : files) {
                    if (file.isFile()) {
                        fileList.add(file.getName() + " (" + file.getSize() + " bytes)");
                    }
                }
            }
            ftpClient.logout();
            ftpClient.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileList;
    }
}