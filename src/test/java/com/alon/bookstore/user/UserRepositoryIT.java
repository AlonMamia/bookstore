package com.alon.bookstore.user;

import com.alon.bookstore.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shares a container/schema with BookSpecificationIT, BookRepositoryIT and
 * FlywayMigrationIT (see .claude/rules/testing.md) - every row written here is
 * deleted afterward. A random email per test avoids collisions between runs, but
 * doesn't by itself stop rows from leaking into another suite's assertions, so
 * cleanup still has to happen explicitly.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private final List<Long> savedUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userRepository.deleteAllByIdInBatch(savedUserIds);
        savedUserIds.clear();
    }

    @Test
    void savingASecondUserWithTheSameEmail_violatesTheUniqueConstraint() {
        Role role = roleRepository.findByTitle("USER").orElseThrow();
        String email = "dup-" + UUID.randomUUID() + "@example.test";
        track(userRepository.saveAndFlush(newUser(email, role)));

        // saveAndFlush (not save) forces the INSERT - and the unique-constraint
        // check - to happen inside this call, instead of being deferred to whenever
        // the persistence context would otherwise flush.
        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser(email, role)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailAndFindByEmail_reflectPersistedState() {
        Role role = roleRepository.findByTitle("USER").orElseThrow();
        String email = "lookup-" + UUID.randomUUID() + "@example.test";

        assertThat(userRepository.existsByEmail(email)).isFalse();
        assertThat(userRepository.findByEmail(email)).isEmpty();

        track(userRepository.saveAndFlush(newUser(email, role)));

        assertThat(userRepository.existsByEmail(email)).isTrue();
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    private void track(User saved) {
        savedUserIds.add(saved.getId());
    }

    private User newUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        return user;
    }
}
