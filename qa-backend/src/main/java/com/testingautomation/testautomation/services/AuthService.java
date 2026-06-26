package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.responseDto.LoginResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.entities.User;
import com.testingautomation.testautomation.repositories.userRepos.UserRepository;
import com.testingautomation.testautomation.dto.requestDto.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new GlobalExceptionHandler.BadRequestException("Invalid username or password"));

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token("stub-token-" + UUID.randomUUID())
                .build();
    }

    public List<User> getAllUsers(){
        return userRepository.findAll();
    }

}