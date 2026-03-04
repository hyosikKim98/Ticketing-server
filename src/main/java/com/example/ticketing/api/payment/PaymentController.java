package com.example.ticketing.api.payment;

import com.example.ticketing.api.dto.PaymentRequestCreateRequest;
import com.example.ticketing.api.dto.PaymentRequestCreateResponse;
import com.example.ticketing.application.payment.PaymentApplicationService;
import com.example.ticketing.security.CustomPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentRequestCreateResponse request(@Valid @RequestBody PaymentRequestCreateRequest request,
                                                @AuthenticationPrincipal CustomPrincipal principal) {
        return paymentApplicationService.request(request, principal.userId());
    }
}
