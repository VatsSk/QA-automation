package com.testingautomation.testautomation.dto.responseDto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String userId;
    private String username;
    private String role;
    /** Stub token for local dev — replace with real JWT when auth is added */
    private String token;
}