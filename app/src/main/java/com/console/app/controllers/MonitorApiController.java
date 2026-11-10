package com.console.app.controllers; // Ajuste para o seu package

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

        // 1. BUSCAR MÉTRICAS REAIS USANDO O MÉTODO getMetrics() DO SEU SshService
        try {
            Map<String, String> sshMetrics = sshService.getMetrics();

            // Extrai o valor do mapa gerado pelo SshService
            data.put("cpu", sshMetrics.getOrDefault("cpu", "0%"));
            data.put("ram", sshMetrics.getOrDefault("ram", "0%"));

        } catch (Exception e) {
            System.err.println("Erro ao obter métricas SSH em tempo real: " + e.getMessage());
            data.put("cpu", "0%");
            data.put("ram", "0%");
        }

        // 2. LATÊNCIA HTTP E CÓDIGO DE STATUS DO APACHE
        int httpStatus = 500;
        long latency = -1;

        try {
            long startTime = System.currentTimeMillis();

            URL url = new URL("http://192.168.100.10");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            httpStatus = connection.getResponseCode();

            long endTime = System.currentTimeMillis();
            latency = endTime - startTime;

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