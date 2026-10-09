package com.codewithsam.courier.provider.misbehaviour;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class LostResponseAdvice {

    /** A 500 with no receipt: the client cannot tell whether it was charged. */
    @ExceptionHandler(SimulatedLostResponseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ProblemDetail lost(SimulatedLostResponseException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "upstream connection reset");
    }
}
