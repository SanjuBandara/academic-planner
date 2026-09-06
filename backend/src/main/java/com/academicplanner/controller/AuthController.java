package com.academicplanner.controller;

import com.academicplanner.dto.auth.*;
import com.academicplanner.entity.User;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, 
                          org.springframework.security.authentication.AuthenticationManager authenticationManager,
                          UserRepository userRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {

        Authentication authRequest =
                new UsernamePasswordAuthenticationToken(
                        AuthService.normalizeEmail(request.email()),
                        request.password()
                );

        // Delegates to CustomUserDetailsService + BCryptPasswordEncoder.
        // Throws BadCredentialsException on failure -> handled by GlobalExceptionHandler.
        Authentication authResult = authenticationManager.authenticate(authRequest);

        // Persist authentication into a new HTTP session (session fixation safe)
        // and ensure the session cookie / CSRF cookie are written to the response.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        User user = userRepository.findByEmail(authResult.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(new AuthResponse(user.getId(), user.getEmail(), true));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CurrentUserResponse.unauthenticated());
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(new CurrentUserResponse(true, user.getId(), user.getEmail()));
    }

    // Note: POST /api/auth/logout is handled directly by Spring Security's
    // LogoutFilter configured in SecurityConfig.java.
}
