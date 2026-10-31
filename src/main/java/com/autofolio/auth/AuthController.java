package com.autofolio.auth;

import com.autofolio.auth.dto.AuthResponse;
import com.autofolio.auth.dto.ForgotPasswordRequest;
import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.LoginResponse;
import com.autofolio.auth.dto.RefreshRequest;
import com.autofolio.auth.dto.RefreshResponse;
import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.auth.dto.ResetPasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsável pelos endpoints de autenticação e registro.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint para cadastro de um novo vendedor.
     */
    @Operation(summary = "Cadastro de novo vendedor", description = "Cria usuário e perfil e retorna os tokens de acesso.")
    @ApiResponse(responseCode = "201", description = "Vendedor cadastrado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "E-mail ou slug já em uso")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        var response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint de login por e-mail e senha.
     */
    @Operation(summary = "Login por e-mail e senha", description = "Retorna JWT de acesso (1h) e refresh token opaco (30 dias).")
    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Renova o access token sem rotacionar o refresh token.
     */
    @Operation(summary = "Renovação do access token", description = "Recebe um refresh token válido e retorna novo JWT de acesso (1h). O refresh token não é rotacionado.")
    @ApiResponse(responseCode = "200", description = "Access token renovado")
    @ApiResponse(responseCode = "400", description = "Corpo inválido")
    @ApiResponse(responseCode = "401", description = "Refresh token inexistente, revogado ou expirado")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /**
     * Solicita o e-mail de redefinição de senha.
     */
    @Operation(summary = "Recuperação de senha", description = "Se o e-mail estiver cadastrado, envia um link de redefinição válido por 1 hora. Responde sempre 202, exista o e-mail ou não.")
    @ApiResponse(responseCode = "202", description = "Solicitação aceita (independe de o e-mail existir)")
    @ApiResponse(responseCode = "400", description = "E-mail ausente ou malformado")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    /**
     * Redefine a senha com o token de recuperação.
     */
    @Operation(summary = "Redefinição de senha", description = "Recebe o token enviado por e-mail e a nova senha (mínimo 8 caracteres, 1 número e 1 letra maiúscula). O token é de uso único e todos os refresh tokens do usuário são revogados.")
    @ApiResponse(responseCode = "200", description = "Senha redefinida com sucesso")
    @ApiResponse(responseCode = "400", description = "Token inválido, expirado ou já utilizado (resposta idêntica), ou nova senha fora da política")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
