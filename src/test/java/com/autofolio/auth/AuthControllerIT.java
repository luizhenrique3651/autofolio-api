package com.autofolio.auth;

import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - AuthController")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Limpa o banco local antes de cada teste para garantir isolamento.
        // Requer que o container do PostgreSQL (Task AF-003) esteja rodando.
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve registrar um novo vendedor via API e retornar 201 Created")
    void shouldRegisterNewSellerViaApiSuccessfully() throws Exception {
        var request = new RegisterRequest("Vendedor Teste", "vendedor@teste.com", "vendedor-teste", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(request.email()))
                .andExpect(jsonPath("$.user.slug").value(request.slug()));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando os dados de entrada forem inválidos")
    void shouldReturn400WhenInputIsInvalid() throws Exception {
        var request = new RegisterRequest("", "email-invalido", "Slug Com Maiuscula", "short");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição Inválida"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("Deve retornar 409 Conflict quando o e-mail já estiver em uso")
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        var existingRequest = new RegisterRequest("Existente", "duplicado@teste.com", "slug1", "password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        var newRequest = new RegisterRequest("Novo", "duplicado@teste.com", "slug2", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado no sistema."));
    }
}
