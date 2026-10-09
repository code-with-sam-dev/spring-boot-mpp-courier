CREATE TABLE IF NOT EXISTS payment_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    at           TIMESTAMP NOT NULL,
    business_day DATE NOT NULL,
    client       VARCHAR(10) NOT NULL,
    outcome      VARCHAR(20) NOT NULL,
    amount       DECIMAL(24, 6) NOT NULL,
    recipient    VARCHAR(42) NOT NULL,
    challenge_id VARCHAR(100) NOT NULL,
    tx_hash      VARCHAR(66),
    detail       VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS payment_operation (
    request_key      VARCHAR(200) PRIMARY KEY,
    state            VARCHAR(20) NOT NULL,
    www_authenticate VARCHAR(2000) NOT NULL,
    tx_hash          VARCHAR(66) NOT NULL,
    raw_transaction  VARCHAR(2000) NOT NULL,
    body             VARCHAR(2000),
    receipt          VARCHAR(1000),
    updated_at       TIMESTAMP NOT NULL
);
