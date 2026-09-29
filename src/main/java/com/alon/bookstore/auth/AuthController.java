package com.alon.bookstore.auth;

import com.alon.bookstore.user.Role;
import com.alon.bookstore.user.RoleRepository;
import com.alon.bookstore.user.UserDtoOut;
import com.alon.bookstore.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);

        return ResponseEntity.status(201).build();
    }

    @PostMapping("/login")
    public UserDtoOut login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return
                authService.login(
                        request,
                        httpRequest,
                        httpResponse
        );
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(
            Authentication authentication
    ) {

        String roleTitle = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(authority ->
                        Objects.requireNonNull(authority.getAuthority()).replace("ROLE_", "")
                )
                .orElseThrow();

        Role role = roleRepository.findByTitle(roleTitle)
                .orElseThrow();

        return ResponseEntity.ok(
                new AuthResponse(
                        authentication.getName(),
                        role
                )
        );
    }
}