-- Drop existing transaction-related tables
DROP TABLE IF EXISTS report_executions CASCADE;
DROP TABLE IF EXISTS generated_reports CASCADE;
DROP TABLE IF EXISTS report_templates CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS products CASCADE;

-- Recreate generated_reports and templates since they depend on the old schema implicitly or we just reset them for this massive schema change
CREATE TABLE report_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    group_by_fields TEXT,
    metric_fields TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE generated_reports (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT,
    template_id BIGINT,
    created_by BIGINT,
    filters TEXT,
    created_at TIMESTAMP,
    snapshot_url VARCHAR(255)
);

CREATE TABLE report_executions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT,
    status VARCHAR(255),
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create the new wide transactions table
CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    file_id BIGINT,
    cgst VARCHAR(255),
    igst VARCHAR(255),
    sgst VARCHAR(255),
    utgst VARCHAR(255),
    gst_invoice_no VARCHAR(255),
    irn_status VARCHAR(255),
    irn_date VARCHAR(255),
    irn VARCHAR(255),
    tcs_amount VARCHAR(255),
    part_description VARCHAR(255),
    transporter_name VARCHAR(255),
    lr_date VARCHAR(255),
    lr_number VARCHAR(255),
    cash_discount VARCHAR(255),
    cash_discount_percentage VARCHAR(255),
    commit_flag VARCHAR(255),
    discount_per_part VARCHAR(255),
    discount_per_part_percentage VARCHAR(255),
    weighted_avg VARCHAR(255),
    movement_type VARCHAR(255),
    discount_amount VARCHAR(255),
    other_charges_amount VARCHAR(255),
    sap_order_num VARCHAR(255),
    vat VARCHAR(255),
    transaction_date DATE,
    transaction_number VARCHAR(255),
    status VARCHAR(255),
    challan_quantity VARCHAR(255),
    ware_house_name VARCHAR(255),
    part_number VARCHAR(255),
    recd_qty VARCHAR(255),
    sap_invoice_no VARCHAR(255),
    condition VARCHAR(255),
    tm_account VARCHAR(255),
    tm_acct_type VARCHAR(255),
    additional_tax VARCHAR(255),
    challan_date VARCHAR(255),
    challan_no VARCHAR(255),
    cst VARCHAR(255),
    cst_surcharge VARCHAR(255),
    cst_vat VARCHAR(255),
    division_name VARCHAR(255),
    line_item_invoice_total VARCHAR(255),
    invoice_date VARCHAR(255),
    lst VARCHAR(255),
    lst_surcharge VARCHAR(255),
    net_amount VARCHAR(255),
    octroi VARCHAR(255),
    purchase_order_date VARCHAR(255),
    tm_order_for VARCHAR(255),
    order_no VARCHAR(255),
    order_type VARCHAR(255),
    payer_code VARCHAR(255),
    spares_order_type VARCHAR(255),
    total_tax_amount VARCHAR(255),
    tot VARCHAR(255),
    total_invoice_amount VARCHAR(255),
    vendor_invoice_no VARCHAR(255),
    vendor_name VARCHAR(255),
    
    CONSTRAINT fk_uploaded_file FOREIGN KEY (file_id) REFERENCES uploaded_files(id),
    CONSTRAINT uk_deduplication UNIQUE (ware_house_name, part_number, transaction_date)
);

CREATE SEQUENCE IF NOT EXISTS transaction_seq START 1 INCREMENT 50;
