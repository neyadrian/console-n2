package com.console.app.controllers;

import com.console.app.services.DebianMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MonitorApiController {

    @Autowired
    private DebianMetricsService debianMetricsService;

    @GetMapping("/api/monitor")
    public Map<String, Object> getMonitorData(@RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        if (force) {
            return debianMetricsService.forcarAtualizacao();
        }
        return debianMetricsService.getMetricsData();
    }
}
