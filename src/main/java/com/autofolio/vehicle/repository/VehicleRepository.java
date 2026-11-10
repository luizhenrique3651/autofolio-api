package com.autofolio.vehicle.repository;

import com.autofolio.vehicle.domain.Vehicle;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório dos veículos.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
}
