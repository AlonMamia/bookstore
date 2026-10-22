package com.alon.bookstore;

import com.alon.bookstore.book.BookRepository;
import com.alon.bookstore.user.Role;
import com.alon.bookstore.user.RoleRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms every Flyway migration in src/main/resources/db/migration applies
 * cleanly to a brand-new PostgreSQL database (via Testcontainers) and that the
 * seed data it leaves behind matches what the rest of the suite relies on.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlywayMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void allMigrationsApply_withNonePending() {
        // Deliberately not asserting the current version string: that couples this
        // migration-mechanics check to whatever the newest migration happens to be,
        // and would need editing every time one is added even though nothing here
        // actually failed.
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void seedData_matchesWhatMigrationsInserted() {
        assertThat(bookRepository.count()).isEqualTo(50);
        assertThat(roleRepository.findAll()).extracting(Role::getTitle)
                .containsExactlyInAnyOrder("USER", "ADMIN");
    }
}
