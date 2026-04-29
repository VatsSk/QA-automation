package com.testingautomation.testautomation.controllers.authController;


import com.testingautomation.testautomation.dto.responseDto.LoginResponse;
import com.testingautomation.testautomation.dto.requestDto.LoginRequest;
import com.testingautomation.testautomation.entities.User;
import com.testingautomation.testautomation.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        System.out.println("trying to login "+ request);
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }
}
