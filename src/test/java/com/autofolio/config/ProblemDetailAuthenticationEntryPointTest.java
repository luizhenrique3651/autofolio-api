package com.autofolio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Testes Unitários - ProblemDetailAuthenticationEntryPoint")
class ProblemDetailAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemDetailAuthenticationEntryPoint entryPoint = new ProblemDetailAuthenticationEntryPoint(objectMapper);

    @Test
    @DisplayName("Erro invalid_request do Bearer gera 400 com ProblemDetail de requisição inválida")
    void shouldReturn400ForInvalidRequest() throws Exception {
        var response = new MockHttpServletResponse();
        var error = new BearerTokenError(BearerTokenErrorCodes.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                "malformado", null);

        entryPoint.commence(new MockHttpServletRequest(), response, new OAuth2AuthenticationException(error));

        var json = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("title").asText()).isEqualTo("Requisição Inválida");
    }
}
