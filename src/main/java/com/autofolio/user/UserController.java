package com.autofolio.user;

import com.autofolio.config.OpenApiConfig;
import com.autofolio.shared.security.CurrentUser;
import com.autofolio.user.dto.ChangePasswordRequest;
import com.autofolio.user.dto.DeleteAccountRequest;
import com.autofolio.user.dto.SlugResponse;
import com.autofolio.user.dto.UpdateSlugRequest;
import com.autofolio.user.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints do usuário autenticado sobre a própria conta. O usuário vem do {@code sub} do JWT.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class UserController {

    private final UserService userService;

    /**
     * Dados do usuário autenticado com o perfil.
     */
    @Operation(summary = "Dados do usuário autenticado",
            description = "Retorna os dados do usuário e o perfil, sem o hash da senha.")
    @ApiResponse(responseCode = "200", description = "Usuário e perfil")
    @ApiResponse(responseCode = "401",
            description = "Token ausente, inválido, ou conta desativada")
    @GetMapping
    public ResponseEntity<UserMeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.me(CurrentUser.id(jwt)));
    }

    /**
     * Troca o slug do portfólio.
     */
    @Operation(summary = "Alterar slug do portfólio",
            description = "Valida formato (^[a-z0-9-]+$) e unicidade. Devolve um access token "
                    + "novo com o claim slug atualizado; o token anterior segue válido até "
                    + "expirar, com o slug antigo no claim (o servidor sempre lê o slug do "
                    + "banco).")
    @ApiResponse(responseCode = "200", description = "Slug alterado")
    @ApiResponse(responseCode = "400", description = "Slug fora do formato")
    @ApiResponse(responseCode = "401",
            description = "Token ausente, inválido, ou conta desativada")
    @ApiResponse(responseCode = "409", description = "Slug já em uso")
    @PatchMapping("/slug")
    public ResponseEntity<SlugResponse> updateSlug(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSlugRequest request) {
        return ResponseEntity.ok(userService.updateSlug(CurrentUser.id(jwt), request));
    }

    /**
     * Troca a senha.
     */
    @Operation(summary = "Alterar senha",
            description = "Exige a senha atual. A nova segue a política (8+ caracteres, número, "
                    + "maiúscula). Revoga todos os refresh tokens: é preciso fazer login de "
                    + "novo. O access token atual vale até expirar.")
    @ApiResponse(responseCode = "204", description = "Senha alterada")
    @ApiResponse(responseCode = "400", description = "Nova senha fora da política")
    @ApiResponse(responseCode = "401",
            description = "Senha atual incorreta, token inválido ou conta desativada")
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CurrentUser.id(jwt), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Desativa a conta (soft delete).
     */
    @Operation(summary = "Desativar conta",
            description = "Exige confirmação de senha. Marca a conta como inativa e revoga os "
                    + "refresh tokens. E-mail e slug continuam reservados.")
    @ApiResponse(responseCode = "204", description = "Conta desativada")
    @ApiResponse(responseCode = "401",
            description = "Senha incorreta, token inválido ou conta já desativada")
    @DeleteMapping
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteAccountRequest request) {
        userService.deactivate(CurrentUser.id(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
