package com.alon.bookstore.book;

import com.alon.bookstore.TestcontainersConfiguration;
import com.alon.bookstore.support.AuthTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static com.alon.bookstore.support.AuthTestClient.uniqueEmail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET /api/books end to end through the real security filter chain:
 * the endpoint requires authentication, and the controller/service wiring
 * correctly surfaces pagination and sorting as a {@code PageResponse}. The
 * underlying filter/sort/pagination logic itself is covered exhaustively at
 * the repository level in {@link BookSpecificationIT}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookFilterApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchBooks_withoutAuthentication_isRejected() throws Exception {
        // formLogin()/httpBasic() aren't configured, so Spring Security falls back to
        // its default AuthenticationEntryPoint (Http403ForbiddenEntryPoint), which is
        // why anonymous access to an authenticated-only route is denied as 403, not 401.
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchBooks_withAuthenticatedSession_returnsPageResponseShape() throws Exception {
        AuthTestClient.Session session = new AuthTestClient(mockMvc, objectMapper)
                .registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books")
                        .param("category", "FANTASY")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "price,asc")
                        .session(session.httpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                // Every returned book must match the requested category, not just the first.
                .andExpect(jsonPath("$.content[*].category", everyItem(equalTo("FANTASY"))))
                .andExpect(jsonPath("$.content[0].price").value(54.90))
                .andExpect(jsonPath("$.content[1].price").value(74.90));
    }

    @Test
    void searchBooks_withNoMatches_returnsEmptyContent() throws Exception {
        AuthTestClient.Session session = new AuthTestClient(mockMvc, objectMapper)
                .registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books")
                        .param("category", "NOT_A_REAL_CATEGORY")
                        .session(session.httpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchBooks_withoutAvailableOnlyParam_includesOutOfStockBooks() throws Exception {
        // Regression coverage: an omitted availableOnly must bind to "no filter", not
        // fail @ModelAttribute binding or silently default to "available only".
        AuthTestClient.Session session = new AuthTestClient(mockMvc, objectMapper)
                .registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "100")
                        .session(session.httpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(50));
    }

    @Test
    void searchBooks_withNonNumericPage_returnsBadRequest() throws Exception {
        AuthTestClient.Session session = new AuthTestClient(mockMvc, objectMapper)
                .registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books")
                        .param("page", "not-a-number")
                        .session(session.httpSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBooks_withNonNumericMinPrice_returnsBadRequest() throws Exception {
        AuthTestClient.Session session = new AuthTestClient(mockMvc, objectMapper)
                .registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books")
                        .param("minPrice", "not-a-price")
                        .session(session.httpSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBooks_withNonNumericSize_returnsBadRequest() throws Exception {
        AuthTestClient.Session session = new AuthTestClient(mockMvc, objectMapper)
                .registerAndLogin(uniqueEmail(), "password123");

        mockMvc.perform(get("/api/books")
                        .param("size", "not-a-number")
                        .session(session.httpSession()))
                .andExpect(status().isBadRequest());
    }
}
