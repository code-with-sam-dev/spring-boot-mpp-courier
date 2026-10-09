package com.codewithsam.courier.provider.payment;

import com.stripe.mpp.Challenge;
import com.stripe.mpp.error.PaymentException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every payment failure becomes RFC 9457 Problem Details. A 402 always carries a
 * fresh challenge, so the client can pay again if it decides to.
 */
@RestControllerAdvice
class PaymentProblemAdvice {

    private static final Logger log = LoggerFactory.getLogger(PaymentProblemAdvice.class);

    private final PaymentGate paymentGate;

    PaymentProblemAdvice(PaymentGate paymentGate) {
        this.paymentGate = paymentGate;
    }

    @ExceptionHandler(PaymentException.class)
    ResponseEntity<Map<String, Object>> paymentProblem(PaymentException problem) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(problem.getHttpStatus())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        String challengeId = null;
        if (problem.getHttpStatus() == 402) {
            Challenge retry = paymentGate.freshChallenge();
            response.header(HttpHeaders.WWW_AUTHENTICATE, retry.toWwwAuthenticate());
            challengeId = retry.id();
            log.info("402 {}: {}, challenge {}", problem.getTitle(), problem.getMessage(),
                    challengeId);
        }
        return response.body(problem.toProblemDetails(challengeId));
    }
}
