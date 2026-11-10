package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DebianMetricsService {

    @Autowired
    private SshService sshService;

    public Map<String, String> getMetrics() {
        return sshService.getMetrics();
    }
}
