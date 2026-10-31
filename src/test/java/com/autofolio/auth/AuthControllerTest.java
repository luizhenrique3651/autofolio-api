package com.autofolio.auth;

import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.LoginResponse;
import com.autofolio.config.SecurityConfig;
import com.autofolio.shared.exception.GlobalExceptionHandler;
import com.autofolio.shared.exception.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Testes de Controller - POST /api/v1/auth/login")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Deve retornar 200 com accessToken, refreshToken e expiresIn")
    void shouldLogin() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("jwt", "refresh", 3600));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("a@b.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("Deve retornar 401 com mensagem genérica em credenciais inválidas")
    void shouldReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("a@b.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas."));
    }

    @Test
    @DisplayName("Deve retornar 400 quando o corpo é inválido")
    void shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rota protegida sem token deve retornar 401")
    void protectedRouteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rota protegida com Bearer inválido deve retornar 401")
    void protectedRouteWithInvalidBearerReturns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("invalid"));

        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login com Bearer inválido no header não deve retornar 401 (rota pública ignora o token)")
    void loginWithInvalidBearerIsIgnored() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("invalid"));
        when(authService.login(any())).thenReturn(new LoginResponse("jwt", "refresh", 3600));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer expirado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("a@b.com", "password123"))))
                .andExpect(status().isOk());
    }
}
