package com.yazlab.kargo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {


    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }


    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";
    }


    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }


    @GetMapping("/user")
    public String showUserPage() {
        return "user";
    }


    @GetMapping("/admin")
    public String showAdminPage() {
        return "admin";
    }
}