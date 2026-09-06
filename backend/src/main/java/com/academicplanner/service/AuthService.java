package com.academicplanner.service;

import com.academicplanner.dto.auth.AuthResponse;
import com.academicplanner.dto.auth.RegisterRequest;
import com.academicplanner.entity.User;
import com.academicplanner.exception.EmailAlreadyExistsException;
import com.academicplanner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles registration business logic. Login itself is delegated to Spring
 * Security's AuthenticationManager (see AuthController), which uses
 * CustomUserDetailsService + PasswordEncoder under the hood.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);

        return new AuthResponse(saved.getId(), saved.getEmail(), false);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
