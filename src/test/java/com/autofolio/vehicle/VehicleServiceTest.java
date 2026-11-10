package com.autofolio.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autofolio.config.BusinessProperties;
import com.autofolio.shared.exception.FieldValidationError;
import com.autofolio.shared.exception.RequestValidationException;
import com.autofolio.vehicle.domain.FuelType;
import com.autofolio.vehicle.domain.TransmissionType;
import com.autofolio.vehicle.domain.Vehicle;
import com.autofolio.vehicle.domain.VehicleCondition;
import com.autofolio.vehicle.domain.VehicleStatus;
import com.autofolio.vehicle.dto.CreateVehicleRequest;
import com.autofolio.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - VehicleService")
class VehicleServiceTest {

    // Ano atual fixo em 2026
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private VehicleRepository vehicleRepository;
    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(CLOCK);
    }

    private VehicleService serviceAt(Clock clock) {
        return new VehicleService(vehicleRepository, clock,
                new BusinessProperties(ZoneId.of("America/Sao_Paulo")));
    }

    private static CreateVehicleRequest request(int yearFab, int yearMod) {
        return new CreateVehicleRequest("  Volkswagen ", " Golf GTI", yearFab, yearMod, 45_000,
                " Preto ", "2.0 TSI", TransmissionType.AUTOMATIC, FuelType.GASOLINE,
                VehicleCondition.EXCELLENT, new BigDecimal("89900.00"), "   ");
    }

    private void echoSave() {
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(call -> {
            Vehicle vehicle = call.getArgument(0);
            vehicle.setId(UUID.randomUUID());
            vehicle.setCreatedAt(OffsetDateTime.now());
            vehicle.setUpdatedAt(vehicle.getCreatedAt());
            return vehicle;
        });
    }

    @Test
    @DisplayName("Cria DRAFT do usuário, normaliza textos e loga userId e vehicleId")
    void shouldCreateDraft(CapturedOutput output) {
        echoSave();

        var response = service.create(USER_ID, request(2020, 2021));

        var captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(VehicleStatus.DRAFT);
        assertThat(saved.getBrand()).isEqualTo("Volkswagen");
        assertThat(saved.getModel()).isEqualTo("Golf GTI");
        assertThat(saved.getColor()).isEqualTo("Preto");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getAskingPrice()).isEqualByComparingTo("89900");
        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(VehicleStatus.DRAFT);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.yearMod()).isEqualTo(2021);
        assertThat(output.getOut()).contains("userId=" + USER_ID)
                .contains("vehicleId=" + response.id());
    }

    @Test
    @DisplayName("Mantém a descrição informada, sem espaços nas pontas")
    void shouldKeepDescription() {
        echoSave();
        var withDescription = new CreateVehicleRequest("Fiat", "Uno", 2010, 2010, 0, "Branco",
                "1.0", TransmissionType.MANUAL, FuelType.FLEX, VehicleCondition.GOOD,
                BigDecimal.ONE, "  Único dono  ");

        var response = service.create(USER_ID, withDescription);

        assertThat(response.description()).isEqualTo("Único dono");
    }

    @ParameterizedTest
    @ValueSource(ints = {1900, 2026})
    @DisplayName("yearFab nos limites (1900 e ano atual) é aceito")
    void shouldAcceptYearFabBounds(int yearFab) {
        echoSave();

        assertThatCode(() -> service.create(USER_ID, request(yearFab, yearFab)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2027, 0, -5})
    @DisplayName("yearFab fora de 1900..ano atual: 400 no campo yearFab, sem gravar, com WARN")
    void shouldRejectYearFab(int yearFab, CapturedOutput output) {
        assertThatThrownBy(() -> service.create(USER_ID, request(yearFab, yearFab)))
                .isInstanceOfSatisfying(RequestValidationException.class, e -> {
                    assertThat(e.getErrors()).hasSize(1);
                    assertThat(e.getErrors().getFirst().field()).isEqualTo("yearFab");
                    assertThat(e.getErrors().getFirst().message())
                            .contains("1900").contains("2026");
                });

        verify(vehicleRepository, never()).saveAndFlush(any());
        assertThat(output.getOut()).contains("WARN").contains("userId=" + USER_ID);
    }

    @Test
    @DisplayName("yearMod = fab+1 é aceito, inclusive no ano atual")
    void shouldAcceptNextModelYear() {
        echoSave();

        assertThatCode(() -> service.create(USER_ID, request(2026, 2027)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {2019, 2022})
    @DisplayName("yearMod antes de fab ou depois de fab+1: 400 só no campo yearMod")
    void shouldRejectYearMod(int yearMod) {
        assertThatThrownBy(() -> service.create(USER_ID, request(2020, yearMod)))
                .isInstanceOfSatisfying(RequestValidationException.class, e -> {
                    assertThat(e.getErrors()).hasSize(1);
                    assertThat(e.getErrors().getFirst().field()).isEqualTo("yearMod");
                });
    }

    @Test
    @DisplayName("yearFab e yearMod inválidos são reportados juntos")
    void shouldReportBothYearErrors() {
        assertThatThrownBy(() -> service.create(USER_ID, request(1899, 2020)))
                .isInstanceOfSatisfying(RequestValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(FieldValidationError::field)
                                .containsExactly("yearFab", "yearMod"));
    }

    @Test
    @DisplayName("Violação de integridade do banco: WARN com userId e a exceção segue para o "
            + "handler global")
    void shouldLogAndRethrowIntegrityViolation(CapturedOutput output) {
        var failure = new DataIntegrityViolationException("x");
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.create(USER_ID, request(2020, 2020)))
                .isSameAs(failure);

        assertThat(output.getOut()).contains("recusado pelo banco")
                .contains("userId=" + USER_ID);
    }

    @Test
    @DisplayName("Virada de ano: 31/12 22h em São Paulo já é 01/01 em UTC, e o teto segue 2026")
    void shouldUseBusinessZoneAtNewYear() {
        echoSave();
        // 2027-01-01T01:00Z = 2026-12-31 22:00 em São Paulo
        var newYearService = serviceAt(Clock.fixed(Instant.parse("2027-01-01T01:00:00Z"),
                ZoneOffset.UTC));

        assertThatCode(() -> newYearService.create(USER_ID, request(2026, 2026)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> newYearService.create(USER_ID, request(2027, 2027)))
                .isInstanceOfSatisfying(RequestValidationException.class, e ->
                        assertThat(e.getErrors().getFirst().message()).contains("2026"));
    }

    @Test
    @DisplayName("Depois da meia-noite em São Paulo o teto passa a ser o ano novo")
    void shouldAcceptNewYearAfterLocalMidnight() {
        echoSave();
        // 2027-01-01T03:00Z = 2027-01-01 00:00 em São Paulo
        var newYearService = serviceAt(Clock.fixed(Instant.parse("2027-01-01T03:00:00Z"),
                ZoneOffset.UTC));

        assertThatCode(() -> newYearService.create(USER_ID, request(2027, 2027)))
                .doesNotThrowAnyException();
    }
}
