package com.alon.bookstore.auth;

import com.alon.bookstore.user.Role;
import com.alon.bookstore.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomUserDetails is the class that actually turns a persisted Role into a Spring
 * Security authority - these assertions belong here, not in
 * CustomUserDetailsServiceTest, which only covers lookup-by-email.
 */
class CustomUserDetailsTest {

    @Test
    void getAuthorities_exposesRoleUser_asRoleUserAuthority() {
        Role role = new Role();
        role.setId(1L);
        role.setTitle("USER");

        User user = new User();
        user.setEmail("user@example.test");
        user.setRole(role);

        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void getAuthorities_exposesRoleAdmin_asRoleAdminAuthority() {
        Role role = new Role();
        role.setId(2L);
        role.setTitle("ADMIN");

        User user = new User();
        user.setEmail("admin@example.test");
        user.setRole(role);

        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }
}
