DROP TABLE IF EXISTS transactions CASCADE;

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    code VARCHAR(100),
    created_at TIMESTAMP
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    part_number VARCHAR(100) NOT NULL UNIQUE,
    part_name VARCHAR(255),
    category VARCHAR(100)
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT REFERENCES uploaded_files(id),
    transaction_date DATE,
    invoice_number VARCHAR(255),
    location_id BIGINT REFERENCES locations(id),
    product_id BIGINT REFERENCES products(id),
    sale_amount DECIMAL(15, 2),
    purchase_amount DECIMAL(15, 2),
    expense_amount DECIMAL(15, 2),
    quantity DECIMAL(15, 2),
    tax_amount DECIMAL(15, 2),
    created_at TIMESTAMP,
    UNIQUE (location_id, product_id, transaction_date, invoice_number)
);

CREATE SEQUENCE IF NOT EXISTS transaction_seq INCREMENT BY 50 START WITH 1;
