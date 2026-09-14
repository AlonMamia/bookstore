package com.alon.bookstore.auth;

import com.alon.bookstore.user.Role;

public record AuthResponse(
        String email,
        Role role
) {}