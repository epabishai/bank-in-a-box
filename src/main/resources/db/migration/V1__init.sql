CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    email text NOT NULL,
    balance_cents bigint NOT NULL CHECK (balance_cents >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_accounts_email_lower ON accounts (lower(email));

CREATE TABLE transactions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id),
    type text NOT NULL,
    amount_cents bigint NOT NULL CHECK (amount_cents > 0),
    note text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_account_created_at
    ON transactions (account_id, created_at DESC);
