package com.autofolio.vehicle.dto;

import com.autofolio.vehicle.domain.FuelType;
import com.autofolio.vehicle.domain.TransmissionType;
import com.autofolio.vehicle.domain.Vehicle;
import com.autofolio.vehicle.domain.VehicleCondition;
import com.autofolio.vehicle.domain.VehicleStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Veículo completo devolvido pela API, sem o id do dono.
 */
public record VehicleResponse(
        UUID id,
        VehicleStatus status,
        String brand,
        String model,
        int yearFab,
        int yearMod,
        int km,
        String color,
        String engine,
        TransmissionType transmission,
        FuelType fuel,
        VehicleCondition condition,
        BigDecimal askingPrice,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * Converte a entidade na resposta.
     */
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getStatus(), vehicle.getBrand(),
                vehicle.getModel(), vehicle.getYearFab(), vehicle.getYearMod(), vehicle.getKm(),
                vehicle.getColor(), vehicle.getEngine(), vehicle.getTransmission(),
                vehicle.getFuel(), vehicle.getCondition(), vehicle.getAskingPrice(),
                vehicle.getDescription(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}
