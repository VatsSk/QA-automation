package com.testingautomation.testautomation.controllers.authController;


import com.testingautomation.testautomation.controllers.runController.RunController;
import com.testingautomation.testautomation.dto.responseDto.LoginResponse;
import com.testingautomation.testautomation.dto.requestDto.LoginRequest;
import com.testingautomation.testautomation.entities.User;
import com.testingautomation.testautomation.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        System.out.println("trying to login "+ request);
        logger.info("Login requested for {}",request);

        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }
}
