package com.autofolio.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.auth.PasswordResetMailer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - POST /vehicles (Postgres via Testcontainers)")
class VehicleIT extends AbstractPostgresIT {

    private static final String PASSWORD = "SenhaForte1";
    private static final String[] FUELS = {"GASOLINE", "ETHANOL", "FLEX", "DIESEL", "ELECTRIC",
        "HYBRID", "CNG"};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private PasswordResetMailer mailer;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        // vehicles tem FK RESTRICT para users: limpa antes dos usuários, e ao fim para não
        // quebrar os outros ITs que apagam users no mesmo Postgres
        jdbc.update("DELETE FROM vehicles");
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode register(String email, String slug) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Vendedor", "email", email,
                        "slug", slug, "password", PASSWORD)))).andReturn();
        return json(result);
    }

    private MvcResult create(String token, Map<String, Object> body) throws Exception {
        var request = post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static Map<String, Object> validBody() {
        var body = new HashMap<String, Object>();
        body.put("brand", "Volkswagen");
        body.put("model", "Golf GTI");
        body.put("yearFab", 2020);
        body.put("yearMod", 2021);
        body.put("km", 45_000);
        body.put("color", "Preto");
        body.put("engine", "2.0 TSI");
        body.put("transmission", "AUTOMATIC");
        body.put("fuel", "FLEX");
        body.put("condition", "EXCELLENT");
        body.put("askingPrice", 89900.50);
        return body;
    }

    private long vehicleCount() {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT count(*) FROM vehicles", Long.class));
    }

    @Test
    @DisplayName("201: grava DRAFT do usuário do JWT, ignora status/userId do corpo e devolve "
            + "o veículo completo")
    void shouldCreateDraftOwnedByCaller() throws Exception {
        var auth = register("a@teste.com", "vendedor-a");
        var other = register("b@teste.com", "vendedor-b");
        var body = validBody();
        body.put("status", "SOLD");
        body.put("userId", other.get("user").get("id").asText());
        body.put("description", "Revisado");

        var result = create(auth.get("accessToken").asText(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var response = json(result);
        assertThat(result.getResponse().getHeader("Location"))
                .endsWith("/api/v1/vehicles/" + response.get("id").asText());
        assertThat(response.get("id").asText()).isNotBlank();
        assertThat(response.get("status").asText()).isEqualTo("DRAFT");
        assertThat(response.get("askingPrice").decimalValue()).isEqualByComparingTo("89900.50");
        assertThat(response.get("transmission").asText()).isEqualTo("AUTOMATIC");
        assertThat(response.get("fuel").asText()).isEqualTo("FLEX");
        assertThat(response.get("condition").asText()).isEqualTo("EXCELLENT");
        assertThat(response.get("createdAt").asText()).isNotBlank();
        assertThat(response.get("updatedAt").asText()).isNotBlank();
        assertThat(response.has("userId")).isFalse();

        var row = jdbc.queryForMap("SELECT * FROM vehicles");
        assertThat(row.get("id").toString()).isEqualTo(response.get("id").asText());
        assertThat(row.get("user_id").toString())
                .isEqualTo(auth.get("user").get("id").asText());
        assertThat(row.get("status")).hasToString("DRAFT");
        assertThat(row.get("price")).hasToString("89900.50");
        assertThat(row.get("year_mod")).isEqualTo(2021);
        assertThat(row.get("color")).isEqualTo("Preto");
        assertThat(row.get("engine")).isEqualTo("2.0 TSI");
        assertThat(row.get("description")).isEqualTo("Revisado");
        assertThat(vehicleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Todos os valores dos enums novos são aceitos pelo banco")
    void shouldAcceptEveryEnumValue() throws Exception {
        var token = register("a@teste.com", "vendedor-a").get("accessToken").asText();
        for (var transmission : new String[] {"MANUAL", "AUTOMATIC", "CVT"}) {
            for (var fuel : FUELS) {
                var body = validBody();
                body.put("transmission", transmission);
                body.put("fuel", fuel);
                assertThat(create(token, body).getResponse().getStatus()).isEqualTo(201);
            }
        }
        assertThat(vehicleCount()).isEqualTo(21);
    }

    @Test
    @DisplayName("Sem token ou com token inválido: 401; conta desativada: 401 e nada é gravado")
    void shouldRequireActiveAuthentication() throws Exception {
        assertThat(create(null, validBody()).getResponse().getStatus()).isEqualTo(401);
        assertThat(create("lixo", validBody()).getResponse().getStatus()).isEqualTo(401);

        var token = register("a@teste.com", "vendedor-a").get("accessToken").asText();
        jdbc.update("UPDATE users SET active = false");
        var rejected = create(token, validBody());

        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(rejected).get("detail").asText())
                .isEqualTo("Autenticação necessária ou token inválido.");
        assertThat(vehicleCount()).isZero();
    }

    @Test
    @DisplayName("400 de validação: anos, preço, campos ausentes; contrato de erro e nada gravado")
    void shouldRejectInvalidData() throws Exception {
        var token = register("a@teste.com", "vendedor-a").get("accessToken").asText();

        var nextYear = Year.now().getValue() + 1;
        var cases = new Object[][] {
            {"yearFab", 1899, "yearFab"},
            {"yearFab", nextYear, "yearFab"},
            {"yearMod", 2022, "yearMod"},
            {"askingPrice", 0, "askingPrice"},
            {"askingPrice", -10, "askingPrice"},
            {"km", -1, "km"},
            {"brand", "", "brand"},
        };
        for (var c : cases) {
            var body = validBody();
            body.put((String) c[0], c[1]);

            var result = create(token, body);

            assertThat(result.getResponse().getStatus()).as(c[0] + "=" + c[1]).isEqualTo(400);
            assertThat(result.getResponse().getContentType())
                    .startsWith("application/problem+json");
            var error = json(result);
            assertThat(error.get("errors").get(0).get("field").asText()).isEqualTo(c[2]);
            assertThat(error.get("errors").get(0).get("message").asText()).isNotBlank();
            assertThat(error.has("requestId")).isTrue();
        }

        var missing = create(token, new HashMap<>());
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(missing).get("errors")).hasSize(11);
        assertThat(vehicleCount()).isZero();
    }

    @Test
    @DisplayName("yearFab no ano atual e yearMod no ano seguinte: aceitos")
    void shouldAcceptCurrentYear() throws Exception {
        var token = register("a@teste.com", "vendedor-a").get("accessToken").asText();
        var body = validBody();
        body.put("yearFab", Year.now().getValue());
        body.put("yearMod", Year.now().getValue() + 1);

        assertThat(create(token, body).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Banco recusa dados fora do domínio mesmo sem passar pela API (CHECK da V6)")
    void shouldEnforceCheckConstraints() throws Exception {
        register("a@teste.com", "vendedor-a");
        var userId = jdbc.queryForObject("SELECT id FROM users", UUID.class);
        var insert = "INSERT INTO vehicles (user_id, brand, model, year_fab, year_mod, km, "
                + "price, condition, color, engine, transmission, fuel) VALUES "
                + "(?, 'B', 'M', ?, ?, ?, ?, 'GOOD', 'c', 'e', 'MANUAL', 'FLEX')";

        assertThat(jdbc.update(insert, userId, 2020, 2020, 0, 1)).isEqualTo(1);
        for (var bad : new Object[][] {
            {1899, 1899, 0, 1}, {2020, 2022, 0, 1}, {2020, 2019, 0, 1},
            {2020, 2020, -1, 1}, {2020, 2020, 0, 0}}) {
            assertThatThrownBy(() -> jdbc.update(insert, userId, bad[0], bad[1], bad[2], bad[3]))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
