package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.SignedTransfer;
import com.stripe.mpp.Challenge;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** One row per paid request, so a lost answer is resumed rather than paid for again. */
@Repository
public class PaymentOperationStore {

    private final JdbcClient db;
    private final Clock clock;

    public PaymentOperationStore(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Optional<PaymentOperation> find(String requestKey) {
        return db.sql("SELECT * FROM payment_operation WHERE request_key = ?")
                .param(requestKey)
                .query((rs, row) -> new PaymentOperation(
                        rs.getString("request_key"),
                        OperationState.valueOf(rs.getString("state")),
                        rs.getString("www_authenticate"),
                        rs.getString("tx_hash"),
                        rs.getString("raw_transaction"),
                        rs.getString("body"),
                        rs.getString("receipt")))
                .optional();
    }

    public PaymentOperation startPayment(String requestKey, Challenge challenge,
            SignedTransfer transfer) {
        db.sql("""
                INSERT INTO payment_operation (request_key, state, www_authenticate,
                    tx_hash, raw_transaction, updated_at)
                VALUES (?, 'PENDING_PAYMENT', ?, ?, ?, ?)""")
                .params(requestKey, challenge.toWwwAuthenticate(), transfer.hash(),
                        transfer.rawTransaction(), now())
                .update();
        return find(requestKey).orElseThrow();
    }

    public void markBroadcast(String requestKey) {
        move(requestKey, OperationState.BROADCAST);
    }

    public void markUndelivered(String requestKey) {
        move(requestKey, OperationState.UNDELIVERED);
    }

    public void markRedeemed(String requestKey, String body, String receipt) {
        db.sql("""
                UPDATE payment_operation SET state = 'REDEEMED', body = ?, receipt = ?,
                    updated_at = ? WHERE request_key = ?""")
                .params(body, receipt, now(), requestKey)
                .update();
    }

    private void move(String requestKey, OperationState state) {
        db.sql("""
                UPDATE payment_operation SET state = ?, updated_at = ?
                WHERE request_key = ?""")
                .params(state.name(), now(), requestKey)
                .update();
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }
}
