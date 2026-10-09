CREATE TABLE IF NOT EXISTS redemption (
    tx_hash             VARCHAR(66) PRIMARY KEY,
    challenge_id        VARCHAR(100) NOT NULL,
    idempotency_key     VARCHAR(100),
    request_fingerprint VARCHAR(200) NOT NULL,
    courier             VARCHAR(100) NOT NULL,
    price               DECIMAL(10, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    days                INT NOT NULL,
    receipt             VARCHAR(1000) NOT NULL,
    redeemed_at         TIMESTAMP NOT NULL
);
