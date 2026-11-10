package com.console.app.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

@Service
public class SshService {

    @Value("${debian.server.ip:192.168.100.10}")
    private String host;

    @Value("${debian.server.ssh.port:22}")
    private int port;

    @Value("${debian.server.ssh.user:vboxuser}")
    private String user;

    @Value("${debian.server.ssh.password:123456}")
    private String password;

    private volatile String originalHostname = null;

    public String getCurrentHostname() {
        String h = executeCommand("hostnamectl --static 2>/dev/null || hostname");
        if (h != null && !h.startsWith("Erro") && !h.isBlank() && originalHostname == null) {
            originalHostname = h;
        }
        return h;
    }

    public String getOriginalHostname() {
        if (originalHostname == null) {
            getCurrentHostname();
        }
        return originalHostname != null ? originalHostname : "debian";
    }

    public String setHostname(String newHostname) {
        if (newHostname == null || !newHostname.matches("^[a-zA-Z0-9-]+$")) {
            return "Erro: nome de hostname inválido. Use apenas letras, números e hífen.";
        }
        if (originalHostname == null) {
            getCurrentHostname();
        }
        String command = "echo '" + password + "' | sudo -S hostnamectl set-hostname " + newHostname;
        String res = executeCommand(command);
        if (res.startsWith("Erro")) {
            return res;
        }
        return "Hostname alterado para: " + newHostname;
    }

    public String restoreOriginalHostname() {
        String original = getOriginalHostname();
        if (original == null || original.isBlank() || original.startsWith("Erro")) {
            return "Erro: hostname original não identificado.";
        }
        String command = "echo '" + password + "' | sudo -S hostnamectl set-hostname " + original;
        String res = executeCommand(command);
        if (res.startsWith("Erro")) {
            return res;
        }
        return "Hostname restaurado com sucesso para: " + original;
    }

    public Map<String, String> getMetrics() {
        Map<String, String> metrics = new HashMap<>();

        // Script super leve de 100ms sem sobrecarga de VM
        String script = "read c u n s i w x y z < /proc/stat; "
                + "t1=$((u+n+s+i+w+x+y+z)); id1=$i; "
                + "sleep 0.1; "
                + "read c u n s i w x y z < /proc/stat; "
                + "t2=$((u+n+s+i+w+x+y+z)); id2=$i; "
                + "dt=$((t2-t1)); di=$((id2-id1)); "
                + "if [ $dt -gt 0 ]; then CPU=$(( (dt-di)*100 / dt )); else CPU=0; fi; "
                + "RAM=$(free -m | awk '/Mem:/ {if($2>0) printf(\"%d\", ($3/$2)*100); else print 0}'); "
                + "RAM_DETAILS=$(free -m | awk '/Mem:/ {printf \"%d MB / %d MB\", $3, $2}'); "
                + "DISK=$(df -h /srv/portal/arquivos 2>/dev/null | awk 'NR==2{print $3\" / \"$2}'); "
                + "if [ -z \"$DISK\" ]; then DISK=$(df -h /srv 2>/dev/null | awk 'NR==2{print $3\" / \"$2}'); fi; "
                + "if [ -z \"$DISK\" ]; then DISK=$(df -h / | awk 'NR==2{print $3\" / \"$2}'); fi; "
                + "UP=$(uptime -p 2>/dev/null | sed 's/^up //'); "
                + "HOST=$(hostnamectl --static 2>/dev/null || hostname); "
                + "printf 'CPU:%s\\nRAM:%s\\nRAM_DETAILS:%s\\nDISK:%s\\nUP:%s\\nHOST:%s\\n' \"$CPU\" \"$RAM\" \"$RAM_DETAILS\" \"$DISK\" \"$UP\" \"$HOST\"; "
                + "for s in apache2 vsftpd postfix dovecot ssh; do "
                + "  st=$(systemctl is-active \"$s\" 2>/dev/null || echo unknown); "
                + "  printf 'SVC_%s:%s\\n' \"$s\" \"$st\"; "
                + "done";

        String output = executeCommand(script);
        if (output.startsWith("Erro SSH")) {
            metrics.put("cpu", "Erro");
            metrics.put("cpuUsage", "Erro");
            metrics.put("ram", "Erro");
            metrics.put("memoryUsage", "Erro");
            metrics.put("memoria", "Erro");
            metrics.put("diskUsage", "Erro");
            metrics.put("disco", "Erro");
            metrics.put("uptime", "Erro");
            metrics.put("sshError", output);
            metrics.put("svc_apache2", "unknown");
            metrics.put("svc_vsftpd", "unknown");
            metrics.put("svc_postfix", "unknown");
            metrics.put("svc_dovecot", "unknown");
            metrics.put("svc_ssh", "unknown");
            return metrics;
        }

        String cpu = valueOf(output, "CPU");
        String ram = valueOf(output, "RAM");
        String ramDetails = valueOf(output, "RAM_DETAILS");
        String disk = valueOf(output, "DISK");
        String up = valueOf(output, "UP");
        String hostName = valueOf(output, "HOST");

        if (originalHostname == null && !hostName.isEmpty()) {
            originalHostname = hostName;
        }

        metrics.put("cpu", (cpu.isEmpty() ? "0" : cpu) + "%");
        metrics.put("cpuUsage", metrics.get("cpu"));
        metrics.put("ram", (ram.isEmpty() ? "0" : ram) + "%");
        metrics.put("memoryUsage", metrics.get("ram"));
        metrics.put("memoria", metrics.get("ram"));
        metrics.put("ramDetails", ramDetails.isEmpty() ? "N/A" : ramDetails);
        metrics.put("diskUsage", disk.isEmpty() ? "N/A" : disk);
        metrics.put("disco", metrics.get("diskUsage"));
        metrics.put("uptime", up.isEmpty() ? "N/A" : up);
        metrics.put("hostname", hostName.isEmpty() ? "Debian" : hostName);

        metrics.put("svc_apache2", valueOf(output, "SVC_apache2"));
        metrics.put("svc_vsftpd", valueOf(output, "SVC_vsftpd"));
        metrics.put("svc_postfix", valueOf(output, "SVC_postfix"));
        metrics.put("svc_dovecot", valueOf(output, "SVC_dovecot"));
        metrics.put("svc_ssh", valueOf(output, "SVC_ssh"));

        return metrics;
    }

    public String executeCommand(String command) {
        String lastError = null;

        // Tenta executar criando conexão limpa e fresca. Se falhar por oscilação momentânea, tenta mais uma vez
        for (int attempt = 1; attempt <= 2; attempt++) {
            Session session = null;
            ChannelExec channel = null;
            try {
                session = openFreshSession();
                channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);

                InputStream in = channel.getInputStream();
                InputStream err = channel.getErrStream();

                channel.connect(4000);

                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                ByteArrayOutputStream errStream = new ByteArrayOutputStream();
                byte[] tmp = new byte[1024];

                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline) {
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, 1024);
                        if (i < 0) break;
                        outStream.write(tmp, 0, i);
                    }
                    while (err.available() > 0) {
                        int i = err.read(tmp, 0, 1024);
                        if (i < 0) break;
                        errStream.write(tmp, 0, i);
                    }
                    if (channel.isClosed()) {
                        if (in.available() > 0 || err.available() > 0) continue;
                        break;
                    }
                    Thread.sleep(20);
                }

                String stdout = outStream.toString("UTF-8").trim();
                String stderr = errStream.toString("UTF-8").trim();

                if (!stderr.isEmpty() && stdout.isEmpty() && !stderr.contains("password")) {
                    return "Erro SSH: " + stderr;
                }

                return stdout;

            } catch (Exception e) {
                lastError = e.getMessage();
                if (attempt == 1) {
                    try { Thread.sleep(150); } catch (Exception ignored) {}
                }
            } finally {
                if (channel != null && channel.isConnected()) {
                    try { channel.disconnect(); } catch (Exception ignored) {}
                }
                if (session != null && session.isConnected()) {
                    try { session.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
        return "Erro SSH: " + (lastError != null ? lastError : "timeout ou perda de comunicação com 192.168.100.10:22");
    }

    public String uploadSftp(String remoteDir, String filename, InputStream inputStream) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openFreshSession();
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(5000);
            ensureDir(sftp, remoteDir);
            sftp.cd(remoteDir);
            sftp.put(inputStream, filename);
            return null;
        } catch (Exception e) {
            return "Falha SFTP: " + e.getMessage();
        } finally {
            if (sftp != null && sftp.isConnected()) {
                try { sftp.disconnect(); } catch (Exception ignored) {}
            }
            if (session != null && session.isConnected()) {
                try { session.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    public List<String> listSftp(String remoteDir) {
        List<String> files = new ArrayList<>();
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openFreshSession();
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(5000);
            Vector<?> entries = sftp.ls(remoteDir);
            for (Object entry : entries) {
                ChannelSftp.LsEntry ls = (ChannelSftp.LsEntry) entry;
                if (!ls.getAttrs().isDir() && !ls.getFilename().startsWith(".")) {
                    files.add(ls.getFilename());
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (sftp != null && sftp.isConnected()) {
                try { sftp.disconnect(); } catch (Exception ignored) {}
            }
            if (session != null && session.isConnected()) {
                try { session.disconnect(); } catch (Exception ignored) {}
            }
        }
        return files;
    }

    private Session openFreshSession() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password");
        session.setConfig("TCPKeepAlive", "yes");
        session.setTimeout(6000);
        session.connect(5000);
        return session;
    }

    private void ensureDir(ChannelSftp sftp, String remoteDir) throws SftpException {
        String[] parts = remoteDir.split("/");
        StringBuilder path = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            path.append('/').append(part);
            try {
                sftp.cd(path.toString());
            } catch (SftpException e) {
                sftp.mkdir(path.toString());
            }
        }
    }

    private String valueOf(String output, String key) {
        for (String line : output.split("\\R")) {
            if (line.startsWith(key + ":")) {
                return line.substring(key.length() + 1).trim();
            }
        }
        return "";
    }
}
