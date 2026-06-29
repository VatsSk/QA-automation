package com.testingautomation.testautomation.controllers.userController;

import com.testingautomation.testautomation.entities.User;
import com.testingautomation.testautomation.repositories.userRepos.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/{username}/extension")
    public ResponseEntity<Map<String, String>> getExtensionId(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(Map.of("extensionId", user.getExtensionId() != null ? user.getExtensionId() : "")))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{username}/extension")
    public ResponseEntity<Map<String, String>> updateExtensionId(@PathVariable String username, @RequestBody Map<String, String> body) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setExtensionId(body.get("extensionId"));
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of("extensionId", user.getExtensionId()));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
