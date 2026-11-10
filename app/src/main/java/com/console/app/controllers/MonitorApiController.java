package com.console.app.controllers;

import com.console.app.services.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@RestController
public class MonitorApiController {

    @Autowired
    private SshService sshService;

    @GetMapping("/api/monitor")
    public Map<String, Object> getMonitorData() {
        Map<String, Object> data = new HashMap<>();

        try {
            Map<String, String> sshMetrics = sshService.getMetrics();
            data.put("cpu", sshMetrics.getOrDefault("cpu", sshMetrics.getOrDefault("cpuUsage", "0%")));
            data.put("ram", sshMetrics.getOrDefault("ram", sshMetrics.getOrDefault("memoryUsage", "0%")));
            data.put("disk", sshMetrics.getOrDefault("diskUsage", "N/A"));
            data.put("uptime", sshMetrics.getOrDefault("uptime", "N/A"));
        } catch (Exception e) {
            System.err.println("Erro ao obter métricas SSH em tempo real: " + e.getMessage());
            data.put("cpu", "0%");
            data.put("ram", "0%");
            data.put("disk", "N/A");
            data.put("uptime", "N/A");
        }

        int httpStatus = 500;
        long latency = 0;

        try {
            long startTime = System.currentTimeMillis();
            URL url = new URL("http://192.168.100.10");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            httpStatus = connection.getResponseCode();
            latency = System.currentTimeMillis() - startTime;
            connection.disconnect();
        } catch (Exception e) {
            System.err.println("Aviso: Falha ao contactar o Apache (HTTP): " + e.getMessage());
            latency = 0;
        }

        data.put("httpLatency", latency);
        data.put("httpStatus", httpStatus);
        return data;
    }
}
