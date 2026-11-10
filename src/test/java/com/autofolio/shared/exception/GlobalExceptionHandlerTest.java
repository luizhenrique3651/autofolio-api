package com.autofolio.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.SQLException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("Testes Unitários - GlobalExceptionHandler (um teste por mapeamento)")
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    record Body(@NotBlank String name, @Size(max = 3) String code) {
    }

    @RestController
    static class ProbeController {
        @PostMapping("/probe/validate")
        void validate(@Valid @RequestBody Body body) {
        }

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new EntityNotFoundException("Veículo não encontrado.");
        }

        @GetMapping("/probe/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("SQL secreto: Key (email)=(a@b.com)",
                    new org.hibernate.exception.ConstraintViolationException("x",
                            new SQLException("y"), "uk_user_profiles_user_id"));
        }

        @GetMapping("/probe/constraint")
        void constraint() {
            throw new ConstraintViolationException(
                    VALIDATOR.validate(new Body("", "abcd")));
        }

        @GetMapping("/probe/proxy-frame")
        void proxyFrame() {
            var ex = new DataIntegrityViolationException("x");
            ex.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.autofolio.auth.AuthService$$SpringCGLIB$$0", "register",
                        "<generated>", -1),
                new StackTraceElement("com.autofolio.auth.AuthService", "register",
                        "AuthService.java", 42)});
            throw ex;
        }

        @GetMapping("/probe/param")
        void param(@RequestParam("q") @Size(min = 3, message = "muito curto") String q) {
        }

        @PostMapping("/probe/object")
        void object(@Valid @RequestBody Body body,
                @RequestParam("q") @Size(min = 3) String q) {
        }

        @GetMapping("/probe/not-writable")
        void notWritable() {
            throw new HttpMessageNotWritableException("falha ao escrever");
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("segredo interno");
        }

        @PostMapping("/probe/upload")
        void upload(@RequestPart("file") MultipartFile file) {
        }
    }

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();
    private static final LocalValidatorFactoryBean MVC_VALIDATOR = new LocalValidatorFactoryBean();

    private MockMvc mockMvc;

    @BeforeAll
    static void initValidator() {
        MVC_VALIDATOR.afterPropertiesSet();
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    }

    @AfterEach
    void restoreLogLevel() {
        handlerLogger().setLevel(null);
    }

    @AfterAll
    static void closeValidators() {
        MVC_VALIDATOR.destroy();
        VALIDATOR_FACTORY.close();
    }

    @BeforeEach
    void setUp() {
        // Nível fixo: o logging da suíte pode ligar o DEBUG conforme o profile carregado antes
        handlerLogger().setLevel(Level.INFO);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        var handler = new GlobalExceptionHandler();
        handler.setMessageSource(messages);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(handler).setValidator(MVC_VALIDATOR).build();
    }

    @Test
    @DisplayName("400 de validação: errors é lista de {field, message}; traz error e path")
    void validationErrorsAreObjects() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"abcd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falha na validação dos campos."))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/probe/validate"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='code')].message").isNotEmpty());
    }

    @Test
    @DisplayName("ConstraintViolationException real vira 400 com field (último nó) e message")
    void constraintViolationIs400() throws Exception {
        mockMvc.perform(get("/probe/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='code')].message").isNotEmpty());
    }

    @Test
    @DisplayName("Validação de parâmetro simples: field é o nome do parâmetro")
    void handlerMethodValidationSimpleParam() throws Exception {
        mockMvc.perform(get("/probe/param").param("q", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("q"))
                .andExpect(jsonPath("$.errors[0].message").value("muito curto"));
    }

    @Test
    @DisplayName("Validação com @Valid em objeto: field é o campo real, não o parâmetro")
    void handlerMethodValidationObject() throws Exception {
        mockMvc.perform(post("/probe/object").param("q", "ab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"abcd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='code')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='body')]").isEmpty());
    }

    @Test
    @DisplayName("EntityNotFoundException vira 404")
    void entityNotFoundIs404() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Veículo não encontrado."))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.type").value("https://autofolio.com/errors/not-found"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException: 409 genérico; WARN com constraint e frame,"
            + " sem o SQL")
    void dataIntegrityIs409Generic(CapturedOutput output) throws Exception {
        var body = mockMvc.perform(get("/probe/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("uk_user_profiles").doesNotContain("SQL");
        // Só a linha de WARN: o DEBUG (ligado conforme o logging da suíte) leva a stacktrace
        var warn = output.getOut().lines().filter(l -> l.contains("WARN"))
                .collect(Collectors.joining("\n"));
        assertThat(warn)
                .contains("constraint=uk_user_profiles_user_id")
                .contains("GlobalExceptionHandlerTest$ProbeController.integrity:")
                .doesNotContain("SQL secreto").doesNotContain("a@b.com");
    }

    @Test
    @DisplayName("origem ignora frames de proxy ($$) e sem linha (-1)")
    void originFrameSkipsProxyAndNoLine(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/proxy-frame")).andExpect(status().isConflict());

        assertThat(output.getOut()).contains("origem=com.autofolio.auth.AuthService.register:42")
                .doesNotContain("$$");
    }

    @Test
    @DisplayName("Catch-all: loga ERROR com stacktrace, uma vez, e responde 500 genérico")
    void catchAllLogsStacktraceOnce(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erro interno — tente novamente."));

        assertThat(output.getOut()).contains("ERROR").contains("Erro inesperado")
                .contains("IllegalStateException: segredo interno").contains("\tat ");
        assertThat(output.getOut().split("Erro inesperado", -1)).hasSize(2);
    }

    @Test
    @DisplayName("5xx do Spring MVC é logado em ERROR com stacktrace, uma vez")
    void springServerErrorIsLoggedOnce(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/not-writable"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));

        assertThat(output.getOut()).contains("ERROR").contains("Erro do servidor (500)")
                .contains("HttpMessageNotWritableException");
        assertThat(output.getOut().split("Erro do servidor", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Exceção genérica vira 500 com detail padrão, sem vazar a mensagem")
    void genericIs500() throws Exception {
        var body = mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erro interno — tente novamente."))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("segredo interno");
    }

    @Test
    @DisplayName("path não inclui a query string (pode trazer token)")
    void pathOmitsQueryString() throws Exception {
        var body = mockMvc.perform(get("/probe/boom?token=segredo"))
                .andExpect(jsonPath("$.path").value("/probe/boom"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("segredo").doesNotContain("token=");
    }

    @Test
    @DisplayName("Erros do Spring saem em português (parte ausente e tipo não suportado)")
    void springDetailsArePortuguese() throws Exception {
        mockMvc.perform(post("/probe/upload").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("A parte obrigatória 'file' da requisição está ausente."));
        mockMvc.perform(post("/probe/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail")
                        .value("O tipo de conteúdo da requisição não é suportado."));
    }
}
