package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.responseDto.LoginResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.model.User;
import com.testingautomation.testautomation.repo.UserRepository;
import com.testingautomation.testautomation.requestDto.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Stub authentication service.
 * - No real JWT — returns a UUID as a placeholder token.
 * - Replace with Spring Security + JJWT when auth is needed.
 * - For now any existing user can log in with any password (dev mode).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;


    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();

        User user = userRepository.findByUsername(username).orElse(null);

        if (user != null) {
            log.info("Stub login for existing user '{}'", user.getUsername());

            return LoginResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .token("stub-token-" + UUID.randomUUID())
                    .build();
        }

        // Demo fallback: allow any username even if not found in DB
        log.info("Demo login for non-existing user '{}'", username);

        return LoginResponse.builder()
                .userId("demo-" + UUID.randomUUID())
                .username(username)
                .role("DEMO_USER")
                .token("stub-token-" + UUID.randomUUID())
                .build();
    }

//    public LoginResponse login(LoginRequest request) {
//        User user = userRepository.findByUsername(request.getUsername())
//                .orElseThrow(() -> new GlobalExceptionHandler.BadRequestException("Invalid username or password"));
//
//        // Stub: skip password verification for now
//        log.info("Stub login for user '{}'", user.getUsername());
//
//        return LoginResponse.builder()
//                .userId(user.getId())
//                .username(user.getUsername())
//                .role(user.getRole())
//                .token("stub-token-" + UUID.randomUUID())
//                .build();
//    }

    public User getOrCreateStubUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = User.builder()
                    .username(username)
                    .password("stub")
                    .role("USER")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            return userRepository.save(u);
        });
    }
}