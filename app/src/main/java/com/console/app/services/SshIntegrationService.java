package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SshIntegrationService {

    @Autowired
    private SshService sshService;

    public String executeCommand(String command) {
        return sshService.executeCommand(command);
    }

    public String getCurrentHostname() {
        return sshService.getCurrentHostname();
    }

    public String updateHostname(String newHostname) {
        return sshService.setHostname(newHostname);
    }
}
