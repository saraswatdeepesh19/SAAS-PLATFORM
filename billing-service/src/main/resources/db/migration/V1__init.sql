CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tenant_plans (
    tenant_id UUID PRIMARY KEY,
    tenant_name VARCHAR(150) NOT NULL,
    billing_email VARCHAR(200) NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR'
);

CREATE TABLE usage_monthly_snapshot (
    tenant_id UUID NOT NULL,
    period CHAR(7) NOT NULL,
    total_seconds BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, period)
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    invoice_number VARCHAR(30) NOT NULL UNIQUE,
    period CHAR(7) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, period)
);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    description VARCHAR(200) NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(12,4) NOT NULL,
    amount NUMERIC(12,2) NOT NULL
);