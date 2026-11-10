package com.autofolio.user;

import com.autofolio.config.CloudinaryProperties;
import com.autofolio.shared.exception.ExceptionSummary;
import com.autofolio.shared.exception.MediaStorageException;
import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Envia a foto de perfil para o Cloudinary (pasta {@code profiles/}, recorte 400x400).
 *
 * <p>O {@code public_id} é aleatório: o upload anterior do usuário permanece no Cloudinary
 * (cleanup futuro) e a URL não revela o id do usuário. Com id determinístico, o
 * {@code overwrite} apagaria a foto anterior e duas requisições concorrentes disputariam o
 * mesmo asset. O upload é I/O externo lento, então roda com timeouts explícitos
 * (ADR-001 §4.2) e fora de qualquer transação (§4.1).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfilePhotoStorage {

    static final String FOLDER = "profiles";
    static final int SIZE = 400;
    private static final String MESSAGE =
            "Não foi possível enviar a imagem agora. Tente novamente em instantes.";

    private final Cloudinary cloudinary;

    private final CloudinaryProperties properties;

    /**
     * Faz o upload e devolve a URL https da imagem já transformada.
     *
     * @throws MediaStorageException se o Cloudinary falhar, estourar o timeout ou não devolver URL
     */
    public String upload(UUID userId, byte[] image) {
        // Os timeouts do SDK são em milissegundos e precisam ser Integer
        // ObjectUtils.asMap devolve Map cru; o cast é seguro (chaves String, valores Object)
        @SuppressWarnings("unchecked")
        Map<String, Object> options = ObjectUtils.asMap(
                "folder", FOLDER,
                "public_id", UUID.randomUUID().toString(),
                "resource_type", "image",
                "transformation", new Transformation<>().crop("fill").width(SIZE).height(SIZE),
                "connect_timeout", (int) properties.connectTimeout().toMillis(),
                "connection_request_timeout", (int) properties.connectTimeout().toMillis(),
                "timeout", (int) properties.readTimeout().toMillis());
        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(image, options);
        } catch (IOException | RuntimeException e) {
            // Sem a mensagem da exceção e sem credenciais; a stacktrace fica em DEBUG
            log.error("Falha no upload da foto de perfil (userId={}): {}", userId,
                    ExceptionSummary.describe(e));
            log.debug("Stacktrace do upload da foto (userId={})", userId, e);
            throw new MediaStorageException(MESSAGE, e);
        }
        if (result.get("secure_url") instanceof String url && url.startsWith("https://")) {
            return url;
        }
        log.error("Upload de foto sem URL https na resposta (userId={})", userId);
        throw new MediaStorageException(MESSAGE, null);
    }
}
