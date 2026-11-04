package com.console.app.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/login")
    public String login() {
        return "login"; // Aponta para o ficheiro login.html
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard() {
        return "dashboard"; // Aponta para o ficheiro dashboard.html (que faremos a seguir)
    }
}
