package com.academicplanner.security;

import com.academicplanner.entity.User;
import com.academicplanner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a User from the database and adapts it into Spring Security's
 * UserDetails contract. Email is used as the authentication username.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    public static final String ROLE_STUDENT = "ROLE_STUDENT";

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(ROLE_STUDENT)))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
