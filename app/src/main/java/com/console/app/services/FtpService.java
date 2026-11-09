package com.console.app.services;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Service
public class FtpService {

    private final String server = "192.168.100.10";
    private final int port = 21;
    private final String user = "ftpuser";
    private final String pass = "sua_senha";

    public boolean uploadFile(MultipartFile file) {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(user, pass);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            InputStream inputStream = file.getInputStream();
            String remotePath = "/arquivos/" + file.getOriginalFilename();

            boolean done = ftpClient.storeFile(remotePath, inputStream);
            inputStream.close();
            ftpClient.logout();
            ftpClient.disconnect();

            return done;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> listFiles() {
        List<String> fileList = new ArrayList<>();
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(user, pass);
            ftpClient.enterLocalPassiveMode();

            FTPFile[] files = ftpClient.listFiles("/arquivos/");
            if (files != null) {
                for (FTPFile file : files) {
                    if (file.isFile()) {
                        fileList.add(file.getName());
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

    public String calcularSha256(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(byteBuffer)) != -1) {
                digest.update(byteBuffer, 0, bytesRead);
            }
            byte[] hashedBytes = digest.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Erro ao calcular hash";
        }
    }
}