-- AF-013: soft delete de conta (users.active) e especialidades do perfil.
-- A conta inativa continua ocupando e-mail e slug (as UNIQUE de V1 permanecem) e preserva as FKs
-- ON DELETE RESTRICT de veículos, clientes e fluxo de caixa.
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_profiles ADD COLUMN specialties TEXT[] NOT NULL DEFAULT '{}';
