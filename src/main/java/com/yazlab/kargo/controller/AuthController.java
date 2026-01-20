package com.yazlab.kargo.controller;

import com.yazlab.kargo.entity.User;
import com.yazlab.kargo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;


    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User userData) {
        Map<String, String> response = new HashMap<>();


        if (userRepository.findByEmail(userData.getEmail()) != null) {
            response.put("message", "Bu e-posta adresi zaten kullanılıyor!");
            return ResponseEntity.badRequest().body(response);
        }


        if (userData.getRole() == null || userData.getRole().isEmpty()) {
            userData.setRole("USER");
        }

        userRepository.save(userData);


        response.put("message", "Kayıt Başarılı! Giriş yapabilirsiniz.");
        return ResponseEntity.ok(response);
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        Map<String, Object> response = new HashMap<>();


        User user = userRepository.findByEmail(email);


        if (user != null && user.getPassword().equals(password)) {
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("role", user.getRole());
            response.put("message", "Giriş Başarılı!");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "E-posta veya şifre hatalı!");
            return ResponseEntity.status(401).body(response);
        }
    }
}