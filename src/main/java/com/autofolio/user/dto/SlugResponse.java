package com.autofolio.user.dto;

/**
 * Resultado da troca de slug, com um access token novo cujo claim {@code slug} já reflete a troca.
 */
public record SlugResponse(String slug, String accessToken, long expiresIn) {
}
