package com.example.lms.controller;

import com.example.lms.dto.request.BookRequest;
import com.example.lms.dto.response.BookResponse;
import com.example.lms.entity.BookStatus;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.DuplicateResourceException;
import com.example.lms.exception.GlobalExceptionHandler;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.service.BookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web layer in isolation.
 *
 * <p>Built with standalone MockMvc rather than {@code @WebMvcTest}, so no Spring
 * context starts and the tests run in milliseconds. The trade-off is honest:
 * security filters and method-level {@code @PreAuthorize} are not exercised here,
 * so these prove the request mapping, the response envelope and the status codes
 * — not the authorisation rules. Those need the full context.
 */
@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    @Mock
    private BookService bookService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(bookService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private BookResponse sample() {
        return new BookResponse(1L, "9780132350884", "Clean Code",
                1L, "Robert C. Martin", 1L, "Programming",
                "Prentice Hall", "1st", "English", "A-12-03", 2008,
                4, 3, 0, 1, new BigDecimal("45.00"), BookStatus.ACTIVE, true);
    }

    @Test
    @DisplayName("a book is returned inside the standard envelope")
    void getReturnsEnvelope() throws Exception {
        when(bookService.get(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isbn").value("9780132350884"))
                .andExpect(jsonPath("$.data.availableCopies").value(3))
                .andExpect(jsonPath("$.data.available").value(true))
                // Never present on a response: entities are not serialised, so
                // version and audit columns cannot leak.
                .andExpect(jsonPath("$.data.version").doesNotExist());
    }

    @Test
    @DisplayName("creating returns 201 with a Location header")
    void createReturns201WithLocation() throws Exception {
        when(bookService.create(any(BookRequest.class))).thenReturn(sample());

        BookRequest request = new BookRequest("9780132350884", "Clean Code", 1L, 1L,
                "Prentice Hall", "1st", "English", "A-12-03", 2008, 4,
                new BigDecimal("45.00"), null);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/1"));
    }

    @Test
    @DisplayName("an unknown id is 404, not an empty 200")
    void unknownIdIs404() throws Exception {
        when(bookService.get(anyLong()))
                .thenThrow(ResourceNotFoundException.of("book", 99));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a duplicate ISBN is 409")
    void duplicateIs409() throws Exception {
        when(bookService.create(any()))
                .thenThrow(new DuplicateResourceException("A book with that ISBN exists"));

        BookRequest request = new BookRequest("9780132350884", "Clean Code", 1L, 1L,
                null, null, null, null, 2008, 1, null, null);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("a business rule violation is 422, not 400")
    void businessRuleIs422() throws Exception {
        // Nothing about the request is malformed; the operation is not permitted
        // in the current state. That distinction is the point of the two codes.
        doThrow(new BusinessRuleException("2 copies are on loan"))
                .when(bookService).delete(1L);

        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("an invalid body reports every failing field at once")
    void validationReportsAllFields() throws Exception {
        // A blank ISBN fails the pattern and a negative copy count fails @Min.
        // Returning only the first turns a form with two mistakes into two round
        // trips.
        String body = """
                          {
                          "isbn": "not-an-isbn",
                          "title": "X",
                           "authorId": 1,
                            "categoryId": 1,
                             "totalCopies": -3
                        }""";


        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("a non-numeric id is 400 with a readable message")
    void badPathVariableIs400() throws Exception {
        mockMvc.perform(get("/api/books/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("page size is capped so a caller cannot request the whole catalogue")
    void pageSizeIsCapped() throws Exception {
        Page<BookResponse> page = new PageImpl<>(List.of(sample()));
        when(bookService.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/books").param("size", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }
}
