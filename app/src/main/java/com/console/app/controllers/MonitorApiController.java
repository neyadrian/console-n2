package com.console.app.controllers;

import com.console.app.services.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class MonitorApiController {

    @Autowired
    private SshService sshService;

    @Value("${debian.server.ip:192.168.100.10}")
    private String debianIp;

    @GetMapping("/api/monitor")
    public Map<String, Object> getMonitorData() {
        Map<String, Object> data = new HashMap<>();

        Map<String, String> services = new HashMap<>();
        boolean sshOk = false;

        try {
            Map<String, String> sshMetrics = sshService.getMetrics();
            boolean isError = String.valueOf(sshMetrics.getOrDefault("cpu", "")).startsWith("Erro");

            if (!isError) {
                sshOk = true;
                data.put("cpu", sshMetrics.getOrDefault("cpu", "0%"));
                data.put("ram", sshMetrics.getOrDefault("ram", "0%"));
                data.put("ramDetails", sshMetrics.getOrDefault("ramDetails", ""));
                data.put("disk", sshMetrics.getOrDefault("diskUsage", "N/A"));
                data.put("uptime", sshMetrics.getOrDefault("uptime", "N/A"));
                data.put("hostname", sshMetrics.getOrDefault("hostname", "Debian"));

                services.put("apache2", sshMetrics.getOrDefault("svc_apache2", "unknown"));
                services.put("vsftpd", sshMetrics.getOrDefault("svc_vsftpd", "unknown"));
                services.put("postfix", sshMetrics.getOrDefault("svc_postfix", "unknown"));
                services.put("dovecot", sshMetrics.getOrDefault("svc_dovecot", "unknown"));
                services.put("ssh", sshMetrics.getOrDefault("svc_ssh", "unknown"));
            } else {
                data.put("cpu", "Sem SSH");
                data.put("ram", "Sem SSH");
                data.put("disk", "Sem SSH");
                data.put("uptime", "Sem SSH");
                data.put("hostname", "Debian");
                data.put("sshError", sshMetrics.getOrDefault("sshError", ""));

                services.put("apache2", "unknown");
                services.put("vsftpd", "unknown");
                services.put("postfix", "unknown");
                services.put("dovecot", "unknown");
                services.put("ssh", "unknown");
            }
        } catch (Exception e) {
            data.put("cpu", "Sem SSH");
            data.put("ram", "Sem SSH");
            data.put("disk", "Sem SSH");
            data.put("uptime", "Sem SSH");
            data.put("hostname", "Debian");

            services.put("apache2", "unknown");
            services.put("vsftpd", "unknown");
            services.put("postfix", "unknown");
            services.put("dovecot", "unknown");
            services.put("ssh", "unknown");
        }

        data.put("sshOk", sshOk);
        data.put("services", services);

        // Teste de latência e resposta HTTP para o Apache do Debian
        int httpStatus = 0;
        long latency = 0;
        try {
            long startTime = System.currentTimeMillis();
            URL url = new URL("http://" + debianIp);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            httpStatus = connection.getResponseCode();
            latency = System.currentTimeMillis() - startTime;
            connection.disconnect();
        } catch (Exception e) {
            latency = 0;
            httpStatus = 0;
        }

        data.put("httpLatency", latency);
        data.put("httpStatus", httpStatus);
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return data;
    }
}
