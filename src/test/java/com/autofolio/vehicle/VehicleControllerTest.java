package com.autofolio.vehicle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autofolio.config.SecurityConfig;
import com.autofolio.shared.exception.FieldValidationError;
import com.autofolio.shared.exception.GlobalExceptionHandler;
import com.autofolio.shared.exception.RequestValidationException;
import com.autofolio.vehicle.domain.FuelType;
import com.autofolio.vehicle.domain.TransmissionType;
import com.autofolio.vehicle.domain.VehicleCondition;
import com.autofolio.vehicle.domain.VehicleStatus;
import com.autofolio.vehicle.dto.CreateVehicleRequest;
import com.autofolio.vehicle.dto.VehicleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(VehicleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Testes de Controller - POST /vehicles")
class VehicleControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VehicleService vehicleService;
    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor auth() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
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
        body.put("fuel", "GASOLINE");
        body.put("condition", "EXCELLENT");
        body.put("askingPrice", 89900.50);
        return body;
    }

    private ResultActions send(Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/vehicles").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("201 com o veículo completo e o userId do JWT")
    void shouldCreate() throws Exception {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        when(vehicleService.create(eq(USER_ID), any(CreateVehicleRequest.class)))
                .thenReturn(new VehicleResponse(id, VehicleStatus.DRAFT, "Volkswagen", "Golf GTI",
                        2020, 2021, 45_000, "Preto", "2.0 TSI", TransmissionType.AUTOMATIC,
                        FuelType.GASOLINE, VehicleCondition.EXCELLENT,
                        new BigDecimal("89900.50"), null, now, now));

        send(validBody())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        Matchers.endsWith("/api/v1/vehicles/" + id)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.askingPrice").value(89900.50))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.userId").doesNotExist());
        verify(vehicleService).create(eq(USER_ID), any(CreateVehicleRequest.class));
    }

    @Test
    @DisplayName("Sem token: 401 ProblemDetail e o service não é chamado")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(PROBLEM_JSON));
        verifyNoInteractions(vehicleService);
    }

    @Test
    @DisplayName("Campos obrigatórios ausentes: 400 com errors {field, message} para cada um")
    void shouldRejectMissingFields() throws Exception {
        send(Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(11))
                .andExpect(jsonPath("$.errors[?(@.field=='brand')].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='askingPrice')].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='transmission')].message").isNotEmpty());
        verifyNoInteractions(vehicleService);
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("askingPrice", 0), Arguments.of("askingPrice", -1),
                Arguments.of("askingPrice", 10.123),
                Arguments.of("km", -1), Arguments.of("brand", "   "),
                Arguments.of("color", "x".repeat(51)),
                Arguments.of("description", "x".repeat(5001)));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidFields")
    @DisplayName("Valor fora das regras do DTO: 400 com errors no campo")
    void shouldRejectInvalidFields(String field, Object value) throws Exception {
        var body = validBody();
        body.put(field, value);

        send(body).andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(vehicleService);
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"fuel,WATER", "condition,PERFECT", "transmission,1", "fuel,0"})
    @DisplayName("Enum inválido ou número no lugar do enum: 400 com o campo e valores aceitos")
    void shouldRejectInvalidEnum(String field, String value) throws Exception {
        var body = validBody();
        // Número no JSON (não string) para provar que ordinal não é aceito
        body.put(field, value.matches("\\d+") ? Integer.valueOf(value) : value);

        send(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(Matchers.startsWith("Valor inválido. Valores aceitos:")));
        verifyNoInteractions(vehicleService);
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"km,1.5", "yearFab,2020.9", "askingPrice,abc"})
    @DisplayName("Decimal onde se espera inteiro não é truncado: 400")
    void shouldRejectFloatForInteger(String field, String value) throws Exception {
        var body = validBody();
        body.put(field, value.equals("abc") ? value : Double.valueOf(value));

        send(body).andExpect(status().isBadRequest());
        verifyNoInteractions(vehicleService);
    }

    @Test
    @DisplayName("Regra de ano do service vira 400 com errors do campo")
    void shouldMapServiceValidation() throws Exception {
        when(vehicleService.create(eq(USER_ID), any(CreateVehicleRequest.class)))
                .thenThrow(new RequestValidationException(
                        List.of(new FieldValidationError("yearFab", "fora da faixa"))));

        send(validBody())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("yearFab"));
    }
}
