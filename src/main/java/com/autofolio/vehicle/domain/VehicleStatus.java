package com.autofolio.vehicle.domain;

/**
 * Situação do veículo no estoque (enum {@code vehicle_status} do PostgreSQL).
 */
public enum VehicleStatus {
    DRAFT, FOR_SALE, RESERVED, SOLD
}
