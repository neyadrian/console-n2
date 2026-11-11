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

    private volatile Map<String, Object> latestValidData = null;

    @Scheduled(fixedDelay = 10000, initialDelay = 1000)
    public void coletarMetricasPeriodicas() {
        executarColeta();
    }

    public Map<String, Object> getMetricsData() {
        if (latestValidData == null) executarColeta();
        return latestValidData != null ? latestValidData : getFallbackData();
    }

    public Map<String, Object> forcarAtualizacao() {
        executarColeta();
        return getMetricsData();
    }

    private void executarColeta() {
        Map<String, Object> data = new HashMap<>();
        Map<String, String> services = new HashMap<>();
        boolean sshOk = false;

        try {
            Map<String, String> sshMetrics = sshService.getMetrics();

            if (!sshMetrics.containsKey("erro") && sshMetrics.containsKey("cpu")) {
                sshOk = true;
                data.put("cpu", sshMetrics.get("cpu"));
                data.put("ram", sshMetrics.get("ram"));
                data.put("ramDetails", sshMetrics.get("ramDetails"));
                data.put("disk", sshMetrics.get("diskUsage"));
                data.put("uptime", sshMetrics.get("uptime"));
                data.put("hostname", sshMetrics.get("hostname"));

                services.put("apache2", sshMetrics.getOrDefault("svc_apache2", "unknown"));
                services.put("vsftpd", sshMetrics.getOrDefault("svc_vsftpd", "unknown"));
                services.put("postfix", sshMetrics.getOrDefault("svc_postfix", "unknown"));
                services.put("dovecot", sshMetrics.getOrDefault("svc_dovecot", "unknown"));
                services.put("ssh", sshMetrics.getOrDefault("svc_ssh", "unknown"));
            } else {
                sshOk = false;
                data.put("cpu", "Erro SSH");
                data.put("ram", "Erro SSH");
                data.put("ramDetails", sshMetrics.getOrDefault("erro", "Falha de Leitura"));
                data.put("disk", "Erro SSH");
                data.put("uptime", "Erro SSH");
                data.put("hostname", "debian-servidor");
            }
        } catch (Exception e) {
            sshOk = false;
        }

        data.put("sshOk", sshOk);
        data.put("services", services);

        int httpStatus = 0;
        long latency = 0;
        try {
            long startTime = System.currentTimeMillis();
            URL url = new URL("http://" + debianIp);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            httpStatus = conn.getResponseCode();
            latency = System.currentTimeMillis() - startTime;
            conn.disconnect();
        } catch (Exception e) {}

        data.put("httpLatency", latency);
        data.put("httpStatus", httpStatus);
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        latestValidData = data;
    }

    private Map<String, Object> getFallbackData() {
        Map<String, Object> data = new HashMap<>();
        data.put("cpu", "Aguardando...");
        data.put("ram", "Aguardando...");
        data.put("ramDetails", "Tentando SSH...");
        data.put("disk", "Aguardando...");
        data.put("uptime", "Aguardando...");
        data.put("hostname", "debian-servidor");
        data.put("sshOk", false);
        return data;
    }
}