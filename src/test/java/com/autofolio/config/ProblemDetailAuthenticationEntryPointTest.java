package com.autofolio.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.autofolio.auth.security.AccountLookupUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@DisplayName("Testes Unitários - ProblemDetailAuthenticationEntryPoint")
class ProblemDetailAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemDetailAuthenticationEntryPoint entryPoint =
            new ProblemDetailAuthenticationEntryPoint(objectMapper);

    @Test
    @DisplayName("Erro invalid_request do Bearer gera 400 com ProblemDetail de requisição inválida")
    void shouldReturn400ForInvalidRequest() throws Exception {
        var response = new MockHttpServletResponse();
        var error = new BearerTokenError(BearerTokenErrorCodes.INVALID_REQUEST,
                HttpStatus.BAD_REQUEST,
                "malformado", null);

        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        entryPoint.commence(request, response,
                new OAuth2AuthenticationException(error));

        var json = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("title").asText()).isEqualTo("Requisição Inválida");
        assertThat(json.get("error").asText()).isEqualTo("Bad Request");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/users/me");
    }

    @Test
    @DisplayName("path do 401 não inclui a query string")
    void pathOmitsQueryString() throws Exception {
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setQueryString("token=segredo");

        entryPoint.commence(request, response,
                new OAuth2AuthenticationException("invalid_token"));

        var raw = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(raw).get("path").asText())
                .isEqualTo("/api/v1/users/me");
        assertThat(raw).doesNotContain("segredo");
    }

    @Test
    @DisplayName("401: instance como nos demais erros e Content-Type sem charset, corpo em UTF-8")
    void shouldIncludeInstanceAndPlainContentType() throws Exception {
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");

        entryPoint.commence(request, response,
                new OAuth2AuthenticationException("invalid_token"));

        var json = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.get("instance").asText()).isEqualTo("/api/v1/users/me");
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(json.get("title").asText()).isEqualTo("Não Autorizado");
    }

    @Test
    @DisplayName("URI inválida: o 401 sai sem instance, mas com path")
    void shouldOmitInstanceWhenUriIsInvalid() throws Exception {
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("GET", "/api/v1/a b");

        entryPoint.commence(request, response,
                new OAuth2AuthenticationException("invalid_token"));

        var json = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(json.has("instance")).isFalse();
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/a b");
    }

    @Test
    @DisplayName("Falha de infraestrutura na autenticação gera 503 ProblemDetail, não 401")
    void shouldReturn503WhenAccountLookupIsUnavailable() throws Exception {
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("GET", "/api/v1/vehicles");
        var cause = new AccountLookupUnavailableException(new RuntimeException("banco fora"));

        entryPoint.commence(request, response,
                new InvalidBearerTokenException(cause.getMessage(), cause));

        var raw = response.getContentAsString(StandardCharsets.UTF_8);
        var json = objectMapper.readTree(raw);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(json.get("status").asInt()).isEqualTo(503);
        assertThat(json.get("title").asText()).isEqualTo("Serviço Indisponível");
        assertThat(json.get("type").asText())
                .isEqualTo("https://autofolio.com/errors/service-unavailable");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/vehicles");
        assertThat(raw).doesNotContain("banco fora");
    }
}
