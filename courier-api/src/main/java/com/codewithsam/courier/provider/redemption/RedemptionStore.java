package com.codewithsam.courier.provider.redemption;

import com.codewithsam.courier.provider.quote.QuoteResponse;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RedemptionStore {

    private final JdbcClient db;

    public RedemptionStore(JdbcClient db) {
        this.db = db;
    }

    public void save(Redemption r) {
        db.sql("""
                INSERT INTO redemption (tx_hash, challenge_id, idempotency_key,
                    request_fingerprint, courier, price, currency, days, receipt,
                    redeemed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(r.txHash(), r.challengeId(), r.idempotencyKey(),
                        r.requestFingerprint(), r.quote().courier(), r.quote().price(),
                        r.quote().currency(), r.quote().days(), r.receipt(),
                        Timestamp.from(r.redeemedAt()))
                .update();
    }

    public Optional<Redemption> findByTxHash(String txHash) {
        return db.sql("SELECT * FROM redemption WHERE tx_hash = ?")
                .param(txHash)
                .query((rs, row) -> new Redemption(
                        rs.getString("tx_hash"),
                        rs.getString("challenge_id"),
                        rs.getString("idempotency_key"),
                        rs.getString("request_fingerprint"),
                        new QuoteResponse(rs.getString("courier"),
                                rs.getBigDecimal("price"), rs.getString("currency"),
                                rs.getInt("days")),
                        rs.getString("receipt"),
                        rs.getTimestamp("redeemed_at").toInstant()))
                .optional();
    }
}
