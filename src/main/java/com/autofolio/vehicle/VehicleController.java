package com.autofolio.vehicle;

import com.autofolio.config.OpenApiConfig;
import com.autofolio.shared.security.CurrentUser;
import com.autofolio.vehicle.dto.CreateVehicleRequest;
import com.autofolio.vehicle.dto.VehicleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints de veículos do usuário autenticado. O dono vem do {@code sub} do JWT.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * Cadastra um veículo.
     */
    @Operation(summary = "Cadastrar veículo",
            description = "Cria o veículo do usuário autenticado com status DRAFT (o vendedor "
                    + "publica depois). Qualquer status enviado no corpo é ignorado. yearFab entre "
                    + "1900 e o ano atual; yearMod igual a yearFab ou o seguinte; askingPrice > 0.")
    @ApiResponse(responseCode = "201",
            description = "Veículo criado, com id, status e timestamps; Location aponta para o "
                    + "recurso")
    @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente ou inválido")
    @ApiResponse(responseCode = "401",
            description = "Token ausente, inválido, ou conta desativada")
    @PostMapping
    public ResponseEntity<VehicleResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVehicleRequest request) {
        var created = vehicleService.create(CurrentUser.id(jwt), request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
