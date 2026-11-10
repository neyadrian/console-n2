package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class DebianMetricsService {

    @Autowired
    private SshService sshService;

    @Value("${debian.server.ip:192.168.100.10}")
    private String debianIp;

    private volatile Map<String, Object> latestData = null;
    private final Object collectionLock = new Object();

    // Executa em background a cada 4 segundos, sem travar threads HTTP do Tomcat
    @Scheduled(fixedDelay = 4000, initialDelay = 1000)
    public void coletarMetricasPeriodicas() {
        executarColeta();
    }

    public Map<String, Object> getMetricsData() {
        if (latestData == null) {
            executarColeta();
        }
        return latestData != null ? latestData : getFallbackData();
    }

    public Map<String, Object> forcarAtualizacao() {
        executarColeta();
        return getMetricsData();
    }

    private void executarColeta() {
        synchronized (collectionLock) {
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
                    data.put("hostname", sshMetrics.getOrDefault("hostname", "debian-servidor"));

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

            // Teste rápido de latência e código HTTP para o Apache (timeout de 2s)
            int httpStatus = 0;
            long latency = 0;
            try {
                long startTime = System.currentTimeMillis();
                URL url = new URL("http://" + debianIp);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
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

            latestData = data;
        }
    }

    private Map<String, Object> getFallbackData() {
        Map<String, Object> data = new HashMap<>();
        data.put("cpu", "...");
        data.put("ram", "...");
        data.put("ramDetails", "Carregando...");
        data.put("disk", "...");
        data.put("uptime", "...");
        data.put("hostname", "debian-servidor");
        data.put("sshOk", false);
        Map<String, String> services = new HashMap<>();
        services.put("apache2", "unknown");
        services.put("vsftpd", "unknown");
        services.put("postfix", "unknown");
        services.put("dovecot", "unknown");
        services.put("ssh", "unknown");
        data.put("services", services);
        data.put("httpLatency", 0);
        data.put("httpStatus", 0);
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        return data;
    }
}
