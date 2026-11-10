package com.console.app.controllers;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class MonitorApiController {

    @GetMapping("/api/monitor")
    public Map<String, Object> getMonitorData() {
        Map<String, Object> data = new HashMap<>();

        // Aqui deve usar o seu SshService para buscar os dados reais do Debian.
        // Valores de exemplo para testar:
        data.put("cpu", 15.5);
        data.put("ram", 42.0);

        // Para a Latência HTTP, pode usar o RestTemplate ou o HttpClient nativo do Java para fazer um GET em "http://192.168.100.10" e medir o tempo (System.currentTimeMillis())
        data.put("httpLatency", 8);
        data.put("httpStatus", 200);

        return data;
    }
}