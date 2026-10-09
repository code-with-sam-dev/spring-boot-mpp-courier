package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.TempoNetwork;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Every payment decision and every step after it, kept in a table. Stores the
 * transaction hash, never the Authorization credential: that is a bearer token.
 */
@Service
public class PaymentAuditService {

    private final JdbcClient db;
    private final Clock clock;

    public PaymentAuditService(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public void record(String client, AuditOutcome outcome, ChargeOffer offer,
            String txHash, String detail) {
        db.sql("""
                INSERT INTO payment_audit (at, business_day, client, outcome, amount,
                    recipient, challenge_id, tx_hash, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(Timestamp.from(clock.instant()), LocalDate.now(clock), client,
                        outcome.name(), offer.amount(), offer.recipient(),
                        offer.challengeId(), txHash, detail)
                .update();
    }

    /** What the policy approved today. Refusals and unsafe payments do not count. */
    public BigDecimal spentToday() {
        return db.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM payment_audit
                WHERE outcome = 'APPROVED' AND business_day = ?""")
                .param(LocalDate.now(clock))
                .query(BigDecimal.class)
                .single();
    }

    public List<PaymentAuditEntry> entries() {
        return db.sql("SELECT * FROM payment_audit ORDER BY id")
                .query((rs, row) -> new PaymentAuditEntry(
                        rs.getLong("id"),
                        rs.getTimestamp("at").toInstant(),
                        rs.getString("client"),
                        AuditOutcome.valueOf(rs.getString("outcome")),
                        rs.getBigDecimal("amount"),
                        rs.getString("recipient"),
                        rs.getString("challenge_id"),
                        rs.getString("tx_hash"),
                        explorerUrl(rs.getString("tx_hash")),
                        rs.getString("detail")))
                .list();
    }

    private static String explorerUrl(String txHash) {
        return txHash == null ? null : TempoNetwork.MODERATO.transactionUrl(txHash);
    }
}
