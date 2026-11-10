package com.autofolio.vehicle.domain;

/**
 * Combustível, na granularidade do mercado brasileiro (enum {@code fuel_type} do PostgreSQL).
 * {@code FLEX} é distinto de gasolina e etanol porque é o que o anunciante informa; {@code CNG}
 * é o GNV; {@code HYBRID} cobre híbridos de qualquer tipo.
 */
public enum FuelType {
    GASOLINE, ETHANOL, FLEX, DIESEL, ELECTRIC, HYBRID, CNG
}
