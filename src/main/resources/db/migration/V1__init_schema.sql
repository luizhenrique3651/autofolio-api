-- ENUMs
CREATE TYPE vehicle_status AS ENUM ('DRAFT', 'FOR_SALE', 'RESERVED', 'SOLD');
CREATE TYPE vehicle_condition AS ENUM ('EXCELLENT', 'GOOD', 'REGULAR');
CREATE TYPE payment_type AS ENUM ('CASH', 'FINANCED', 'TRADE');
CREATE TYPE cashflow_type AS ENUM ('INCOME', 'EXPENSE');
CREATE TYPE cashflow_category AS ENUM ('PURCHASE', 'PART', 'MECHANIC', 'DOCUMENTATION', 'FREIGHT', 'SALE', 'OTHERS');

-- Tables

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- User Profiles Table (1:1 with users)
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    bio TEXT,
    whatsapp VARCHAR(20),
    city VARCHAR(100),
    photo_url VARCHAR(500),
    exp_years INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_user_profile FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Vehicles Table
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    brand VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year_fab INTEGER NOT NULL,
    year_mod INTEGER NOT NULL,
    km INTEGER NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    description TEXT,
    status vehicle_status DEFAULT 'DRAFT',
    condition vehicle_condition NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_vehicle_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Vehicle Photos Table
CREATE TABLE vehicle_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL,
    url VARCHAR(500) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_photo_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE
);

-- Clients Table
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    whatsapp VARCHAR(20),
    city VARCHAR(100),
    email VARCHAR(255),
    notes TEXT,
    anonymized BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_client_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Sales Table (1:1 with vehicles)
CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL UNIQUE,
    client_id UUID,
    final_price DECIMAL(15, 2) NOT NULL,
    payment_type payment_type NOT NULL,
    sale_date DATE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_sale_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL
);

-- Cash Flow Entries Table
CREATE TABLE cash_flow_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    vehicle_id UUID,
    type cashflow_type NOT NULL,
    category cashflow_category NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    entry_date DATE NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_cashflow_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cashflow_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE RESTRICT
);

-- Indexes for Performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_slug ON users(slug);
CREATE INDEX idx_vehicles_user_id ON vehicles(user_id);
CREATE INDEX idx_vehicles_status ON vehicles(status);
CREATE INDEX idx_vehicle_photos_vehicle_id ON vehicle_photos(vehicle_id);
CREATE INDEX idx_clients_user_id ON clients(user_id);
CREATE INDEX idx_sales_vehicle_id ON sales(vehicle_id);
CREATE INDEX idx_cashflow_user_id ON cash_flow_entries(user_id);
CREATE INDEX idx_cashflow_vehicle_id ON cash_flow_entries(vehicle_id);
