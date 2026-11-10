package com.autofolio.vehicle;

import com.autofolio.config.BusinessProperties;
import com.autofolio.shared.exception.FieldValidationError;
import com.autofolio.shared.exception.RequestValidationException;
import com.autofolio.vehicle.domain.Vehicle;
import com.autofolio.vehicle.domain.VehicleStatus;
import com.autofolio.vehicle.dto.CreateVehicleRequest;
import com.autofolio.vehicle.dto.VehicleResponse;
import com.autofolio.vehicle.repository.VehicleRepository;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de veículos do usuário autenticado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    static final int MIN_YEAR_FAB = 1900;

    private final VehicleRepository vehicleRepository;
    private final Clock clock;
    private final BusinessProperties business;

    /**
     * Cadastra o veículo do usuário, sempre como DRAFT (o vendedor publica depois), depois de
     * validar os anos contra a data atual.
     */
    @Transactional
    public VehicleResponse create(UUID userId, CreateVehicleRequest request) {
        validateYears(userId, request);
        var vehicle = Vehicle.builder()
                .userId(userId)
                .brand(request.brand().strip())
                .model(request.model().strip())
                .yearFab(request.yearFab())
                .yearMod(request.yearMod())
                .km(request.km())
                .color(request.color().strip())
                .engine(request.engine().strip())
                .transmission(request.transmission())
                .fuel(request.fuel())
                .condition(request.condition())
                .askingPrice(request.askingPrice())
                .description(blankToNull(request.description()))
                .status(VehicleStatus.DRAFT)
                .build();
        Vehicle saved;
        try {
            // Flush aqui para que a violação do banco seja logada com o userId (a stacktrace fica
            // no handler global, ADR-001 §1.8)
            saved = vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException e) {
            log.warn("Cadastro de veículo recusado pelo banco (userId={})", userId);
            throw e;
        }
        log.info("Veículo criado (userId={}, vehicleId={})", userId, saved.getId());
        return VehicleResponse.from(saved);
    }

    // yearFab entre 1900 e o ano atual; yearMod é o ano de fabricação ou o seguinte (regra do
    // ano-modelo brasileiro). O ano vem do Clock injetável.
    private void validateYears(UUID userId, CreateVehicleRequest request) {
        // Ano civil no fuso do negócio: o Clock global é UTC e viraria o ano 3h antes no Brasil
        var currentYear = Year.now(clock.withZone(business.zone())).getValue();
        var errors = new ArrayList<FieldValidationError>();
        var yearFab = request.yearFab();
        if (yearFab < MIN_YEAR_FAB || yearFab > currentYear) {
            errors.add(new FieldValidationError("yearFab", "Ano de fabricação deve estar entre "
                    + MIN_YEAR_FAB + " e " + currentYear + "."));
        }
        // Independente da validade do yearFab, para o cliente corrigir os dois de uma vez
        if (request.yearMod() < yearFab || request.yearMod() > yearFab + 1) {
            errors.add(new FieldValidationError("yearMod",
                    "Ano do modelo deve ser o ano de fabricação ou o seguinte."));
        }
        if (!errors.isEmpty()) {
            log.warn("Cadastro de veículo recusado: {} (userId={})",
                    errors.stream().map(FieldValidationError::field).toList(), userId);
            throw new RequestValidationException(List.copyOf(errors));
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
