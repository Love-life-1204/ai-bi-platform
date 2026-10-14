package com.aibi.platform.identity.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private static final int BCRYPT_COST = 12;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(BCRYPT_COST);
    private final String dummyHash = passwordEncoder.encode("dummy-password-never-used");

    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    public void performDummyMatch(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyHash);
    }
}
