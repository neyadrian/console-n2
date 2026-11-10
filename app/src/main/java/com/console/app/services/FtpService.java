package com.console.app.services;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class FtpService {

    @Value("${debian.ftp.host:192.168.100.10}")
    private String server;

    @Value("${debian.ftp.port:21}")
    private int port;

    @Value("${debian.ftp.user:ftpuser}")
    private String user;

    @Value("${debian.ftp.password:2798}")
    private String pass;

    @Value("${debian.ftp.remote-dir:/arquivos}")
    private String remoteDir;

    @Value("${debian.sftp.remote-dir:/srv/portal/arquivos}")
    private String sftpDir;

    @Autowired
    private SshService sshService;

    public static class FtpFileInfo {
        private final String name;
        private final long size;
        private final String formattedSize;

        public FtpFileInfo(String name, long size) {
            this.name = name;
            this.size = size;
            if (size < 1024) {
                this.formattedSize = size + " B";
            } else if (size < 1024 * 1024) {
                this.formattedSize = String.format("%.1f KB", size / 1024.0);
            } else {
                this.formattedSize = String.format("%.2f MB", size / (1024.0 * 1024.0));
            }
        }

        public String getName() { return name; }
        public long getSize() { return size; }
        public String getFormattedSize() { return formattedSize; }
    }

    public static class FtpUploadResult {
        private boolean success;
        private String filename;
        private long size;
        private String formattedSize;
        private String sha256Original;
        private String sha256Retrieved;
        private boolean integrityMatch;
        private String message;
        private String error;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public String getFormattedSize() { return formattedSize; }
        public void setFormattedSize(String formattedSize) { this.formattedSize = formattedSize; }
        public String getSha256Original() { return sha256Original; }
        public void setSha256Original(String sha256Original) { this.sha256Original = sha256Original; }
        public String getSha256Retrieved() { return sha256Retrieved; }
        public void setSha256Retrieved(String sha256Retrieved) { this.sha256Retrieved = sha256Retrieved; }
        public boolean isIntegrityMatch() { return integrityMatch; }
        public void setIntegrityMatch(boolean integrityMatch) { this.integrityMatch = integrityMatch; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public FtpUploadResult uploadFileDetailed(MultipartFile file) {
        FtpUploadResult res = new FtpUploadResult();
        if (file == null || file.isEmpty()) {
            res.setError("Por favor, selecione um ficheiro para enviar.");
            return res;
        }

        String filename = sanitizeName(file.getOriginalFilename());
        if (filename == null || filename.isBlank()) {
            res.setError("Nome de ficheiro inválido.");
            return res;
        }

        res.setFilename(filename);
        res.setSize(file.getSize());
        res.setFormattedSize(new FtpFileInfo(filename, file.getSize()).getFormattedSize());

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
            res.setSha256Original(calcularSha256(fileBytes));
        } catch (Exception e) {
            res.setError("Erro ao ler ficheiro enviado: " + e.getMessage());
            return res;
        }

        FTPClient ftpClient = new FTPClient();
        try {
            configureFtpClient(ftpClient);
            ftpClient.connect(server, port);

            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                res.setError("Servidor FTP em " + server + ":" + port + " recusou a conexão (código " + reply + "): " + ftpClient.getReplyString());
                return res;
            }

            if (!ftpClient.login(user, pass)) {
                res.setError("Falha de autenticação FTP no servidor " + server + " para o utilizador '" + user + "'. Verifique usuário e senha no Debian.");
                return res;
            }

            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            changeToRemoteDir(ftpClient);

            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                boolean done = ftpClient.storeFile(filename, is);
                if (!done) {
                    res.setError("Falha ao gravar ficheiro no vsftpd: " + ftpClient.getReplyString());
                    return res;
                }
            }

            res.setSuccess(true);
            res.setMessage("Arquivo '" + filename + "' (" + res.getFormattedSize() + ") transferido com sucesso ao vsftpd.");

            // Verificação de Integridade (Requisito 2.3 item 5 do manual IFCE)
            // Tenta recuperar via HTTP ou via FTP para comparar os hashes SHA-256
            byte[] retrievedBytes = retrieveViaHttp(filename);
            if (retrievedBytes == null) {
                // Se o Apache ainda não leu, recupera via FTP para testar integridade
                retrievedBytes = retrieveViaFtp(ftpClient, filename);
            }

            if (retrievedBytes != null) {
                String retrievedHash = calcularSha256(retrievedBytes);
                res.setSha256Retrieved(retrievedHash);
                res.setIntegrityMatch(res.getSha256Original().equalsIgnoreCase(retrievedHash));
            } else {
                res.setSha256Retrieved("Não foi possível recuperar via HTTP/FTP para hash automático");
                res.setIntegrityMatch(false);
            }

            return res;

        } catch (Exception e) {
            res.setError("Erro de comunicação FTP com " + server + ":" + port + " -> " + e.getMessage());
            return res;
        } finally {
            disconnectFtpQuietly(ftpClient);
        }
    }

    public String uploadFile(MultipartFile file) {
        FtpUploadResult res = uploadFileDetailed(file);
        if (res.isSuccess()) {
            return null;
        }
        return res.getError();
    }

    public List<FtpFileInfo> listFilesDetailed() {
        List<FtpFileInfo> fileList = new ArrayList<>();
        FTPClient ftpClient = new FTPClient();
        try {
            configureFtpClient(ftpClient);
            ftpClient.connect(server, port);
            if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode()) && ftpClient.login(user, pass)) {
                ftpClient.enterLocalPassiveMode();
                changeToRemoteDir(ftpClient);
                FTPFile[] files = ftpClient.listFiles();
                if (files != null) {
                    for (FTPFile f : files) {
                        if (f.isFile() && !f.getName().startsWith(".")) {
                            fileList.add(new FtpFileInfo(f.getName(), f.getSize()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            disconnectFtpQuietly(ftpClient);
        }

        // Se FTP falhar ou estiver vazio, fallback de visualização via SFTP
        if (fileList.isEmpty()) {
            List<String> sftpFiles = sshService.listSftp(sftpDir);
            for (String s : sftpFiles) {
                fileList.add(new FtpFileInfo(s, 0));
            }
        }
        return fileList;
    }

    public List<String> listFiles() {
        List<String> names = new ArrayList<>();
        for (FtpFileInfo info : listFilesDetailed()) {
            names.add(info.getName());
        }
        return names;
    }

    public byte[] downloadFile(String filename) {
        String safeName = sanitizeName(filename);
        if (safeName == null) return null;

        FTPClient ftpClient = new FTPClient();
        try {
            configureFtpClient(ftpClient);
            ftpClient.connect(server, port);
            if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode()) && ftpClient.login(user, pass)) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                changeToRemoteDir(ftpClient);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                boolean success = ftpClient.retrieveFile(safeName, bos);
                if (success) {
                    return bos.toByteArray();
                }
            }
        } catch (Exception ignored) {
        } finally {
            disconnectFtpQuietly(ftpClient);
        }
        return null;
    }

    public String calcularSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Erro ao calcular hash";
        }
    }

    public String calcularSha256(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteBuffer = new byte[2048];
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
            return "Erro ao calcular hash";
        }
    }

    private void configureFtpClient(FTPClient ftpClient) {
        ftpClient.setConnectTimeout(10000);
        ftpClient.setDefaultTimeout(10000);
        ftpClient.setDataTimeout(Duration.ofSeconds(15));
        ftpClient.setRemoteVerificationEnabled(false);
        ftpClient.setControlKeepAliveTimeout(Duration.ofSeconds(10));
    }

    private boolean changeToRemoteDir(FTPClient ftpClient) {
        String[] candidates = {remoteDir, "arquivos", "/arquivos", "/srv/portal/arquivos", "."};
        for (String dir : candidates) {
            try {
                if (dir != null && !dir.isBlank() && ftpClient.changeWorkingDirectory(dir)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private byte[] retrieveViaHttp(String filename) {
        try {
            URL url = new URL("http://" + server + "/arquivos/" + filename);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[2048];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        bos.write(buf, 0, len);
                    }
                    return bos.toByteArray();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] retrieveViaFtp(FTPClient ftpClient, String filename) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (ftpClient.retrieveFile(filename, bos)) {
                return bos.toByteArray();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void disconnectFtpQuietly(FTPClient ftpClient) {
        if (ftpClient != null && ftpClient.isConnected()) {
            try {
                ftpClient.logout();
            } catch (Exception ignored) {
            }
            try {
                ftpClient.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        String filename = original.replace("\\", "/");
        return filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
