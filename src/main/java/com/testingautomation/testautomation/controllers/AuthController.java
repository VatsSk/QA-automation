package com.testingautomation.testautomation.controllers;


import com.testingautomation.testautomation.dto.responseDto.LoginResponse;
import com.testingautomation.testautomation.requestDto.LoginRequest;
import com.testingautomation.testautomation.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
