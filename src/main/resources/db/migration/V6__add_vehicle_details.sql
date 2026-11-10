-- AF-016: dados do anúncio que a V1 não tinha (cor, motor, câmbio, combustível) e restrições
-- de domínio no banco. Nenhum endpoint gravava em vehicles antes desta task, então a tabela está
-- vazia e as colunas NOT NULL entram sem valor de preenchimento.
-- Enums nativos do PostgreSQL, como vehicle_status e vehicle_condition da V1: o mapeamento JPA
-- é o mesmo para os quatro (@JdbcType PostgreSQLEnumJdbcType) e o banco recusa valor fora da lista.
CREATE TYPE transmission_type AS ENUM ('MANUAL', 'AUTOMATIC', 'CVT');
CREATE TYPE fuel_type AS ENUM ('GASOLINE', 'ETHANOL', 'FLEX', 'DIESEL', 'ELECTRIC', 'HYBRID', 'CNG');

ALTER TABLE vehicles
    ADD COLUMN color VARCHAR(50) NOT NULL,
    ADD COLUMN engine VARCHAR(100) NOT NULL,
    ADD COLUMN transmission transmission_type NOT NULL,
    ADD COLUMN fuel fuel_type NOT NULL;

-- Rede de segurança: a validação de verdade é da API (o teto do ano depende da data atual)
ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicles_year_fab CHECK (year_fab >= 1900),
    ADD CONSTRAINT chk_vehicles_year_mod CHECK (year_mod BETWEEN year_fab AND year_fab + 1),
    ADD CONSTRAINT chk_vehicles_km CHECK (km >= 0),
    ADD CONSTRAINT chk_vehicles_price CHECK (price > 0);
