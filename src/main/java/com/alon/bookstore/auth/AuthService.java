package com.alon.bookstore.auth;

import com.alon.bookstore.user.Role;
import com.alon.bookstore.user.RoleRepository;
import com.alon.bookstore.user.User;
import com.alon.bookstore.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthResponse login(
            LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {

        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email(),
                        request.password()
                );

        Authentication authentication =
                authenticationManager.authenticate(authenticationRequest);

        sessionAuthenticationStrategy.onAuthentication(
                authentication,
                httpRequest,
                httpResponse
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        String roleTitle = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(authority ->
                        Objects.requireNonNull(authority.getAuthority())
                                .replace("ROLE_", "")
                )
                .orElseThrow();

        Role role = roleRepository.findByTitle(roleTitle)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Role not found: " + roleTitle
                        )
                );

        log.info("User logged in: {}", authentication.getName());

        return new AuthResponse(
                authentication.getName(),
                role
        );
    }

    public void register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();

        user.setEmail(request.getEmail());

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        Role role = roleRepository.findByTitle(request.getRole())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid role: " + request.getRole()
                        )
                );

        user.setRole(role);

        userRepository.save(user);

        log.info("User registered: {}", user.getEmail());
    }
}