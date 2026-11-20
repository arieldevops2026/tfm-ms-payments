package dev.ariel.tfm.payments.api.service;

import dev.ariel.tfm.payments.api.dto.AuthorizePaymentRequest;
import dev.ariel.tfm.payments.api.dto.Payment;
import dev.ariel.tfm.payments.repository.PaymentRepository;
import dev.ariel.tfm.payments.util.enums.PaymentStatus;
import dev.ariel.tfm.payments.util.fault.FaultInjector;
import dev.ariel.tfm.payments.util.properties.PaymentProps;
import dev.ariel.tfm.payments.util.utils.GeneralUtils;
import dev.ariel.tfm.payments.util.utils.MDCHelper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PaymentService {

    private static final String REASON_AMOUNT_LIMIT = "AMOUNT_LIMIT_EXCEEDED";

    private final PaymentRepository repository;
    private final PaymentProps paymentProps;
    private final FaultInjector faultInjector;

    /**
     * Autoriza un pago simulado. Se aprueba si el importe no supera el monto maximo configurado.
     */
    public Payment authorize(AuthorizePaymentRequest request) {
        faultInjector.apply();
        MDCHelper.setAccount(GeneralUtils.coalesce(request.getCustomerId()));
        log.info("Autorizando pago simulado, orderId: {}, monto: {} {}", request.getOrderId(),
            request.getAmount().getAmount(), request.getAmount().getCurrency());

        boolean approved = request.getAmount().getAmount().compareTo(paymentProps.maxAmount()) <= 0;

        Payment payment = repository.save(Payment.builder()
            .paymentId(UUID.randomUUID().toString())
            .orderId(request.getOrderId())
            .customerId(request.getCustomerId())
            .amount(request.getAmount())
            .paymentMethod(request.getPaymentMethod())
            .status(approved ? PaymentStatus.APPROVED.name() : PaymentStatus.REJECTED.name())
            .reason(approved ? null : REASON_AMOUNT_LIMIT)
            .processedAt(Instant.now())
            .build());

        log.info("Pago procesado, paymentId: {}, estado: {}", payment.getPaymentId(), payment.getStatus());
        return payment;
    }

    public Payment getPaymentById(String paymentId) {
        faultInjector.apply();
        log.info("Obteniendo pago con id: {}", paymentId);

        return repository.findById(paymentId)
            .orElseThrow(() -> {
                log.warn("Pago no encontrado, id: {}", paymentId);
                return new NotFoundException("Pago no encontrado: " + paymentId);
            });
    }

}
