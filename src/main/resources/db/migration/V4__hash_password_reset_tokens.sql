-- AF-012: o token de redefinição passa a ser guardado como hash SHA-256 (hex, 64 chars); o e-mail leva o token cru.
-- Tokens pendentes continuam válidos: o valor existente é convertido para o hash correspondente.
ALTER TABLE password_reset_tokens RENAME COLUMN token TO token_hash;
UPDATE password_reset_tokens SET token_hash = encode(sha256(convert_to(token_hash, 'UTF8')), 'hex');
