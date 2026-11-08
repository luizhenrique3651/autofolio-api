package com.autofolio.user;

import com.autofolio.config.OpenApiConfig;
import com.autofolio.shared.security.CurrentUser;
import com.autofolio.user.dto.ProfileResponse;
import com.autofolio.user.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint do perfil público do usuário autenticado.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Atualiza o perfil.
     */
    @Operation(summary = "Atualizar perfil",
            description = "Substitui bio, WhatsApp, cidade, especialidades e anos de "
                    + "experiência (campo ausente limpa o valor). A foto é enviada em endpoint "
                    + "próprio.")
    @ApiResponse(responseCode = "200", description = "Perfil atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "401",
            description = "Token ausente, inválido, ou conta desativada")
    @PutMapping
    public ResponseEntity<ProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.update(CurrentUser.id(jwt), request));
    }
}
