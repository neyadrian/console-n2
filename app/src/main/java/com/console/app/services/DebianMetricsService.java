package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class DebianMetricsService {

    @Autowired
    private SshIntegrationService sshService;

    public Map<String, String> getMetrics() {
        Map<String, String> metrics = new HashMap<>();

        try {
            // 1. Uptime (Remove a palavra "up" do início)
            String uptime = sshService.executeCommand("uptime -p").replace("up ", "");
            metrics.put("uptime", uptime.isEmpty() ? "N/A" : uptime);

            // 2. Disco (Comando df -h na pasta /srv/portal/arquivos, filtrando pelo AWK para pegar Usado / Total)
            String disco = sshService.executeCommand("df -h /srv/portal/arquivos | awk 'NR==2 {print $3 \" / \" $2}'");
            metrics.put("disco", disco.isEmpty() ? "N/A" : disco);

            // 3. Memória (Comando free -m, convertido para GB pelo AWK)
            String memoria = sshService.executeCommand("free -m | awk 'NR==2 {printf \"%.1f / %.1f GB\", $3/1024.0, $2/1024.0}'");
            metrics.put("memoria", memoria.isEmpty() ? "N/A" : memoria);

            // 4. CPU (Comando vmstat 1 2. Pega o valor "id" (idle/livre) e subtrai de 100 para saber o uso)
            String cpuIdleStr = sshService.executeCommand("vmstat 1 2 | tail -1 | awk '{print $15}'");
            int cpuUsage = 100 - Integer.parseInt(cpuIdleStr.trim());
            metrics.put("cpu", cpuUsage + "%");

        } catch (Exception e) {
            metrics.put("uptime", "Erro");
            metrics.put("disco", "Erro");
            metrics.put("memoria", "Erro");
            metrics.put("cpu", "Erro");
        }

        return metrics;
    }
}
