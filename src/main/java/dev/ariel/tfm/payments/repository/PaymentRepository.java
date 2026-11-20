package dev.ariel.tfm.payments.repository;

import dev.ariel.tfm.payments.api.dto.Payment;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Almacen en memoria de pagos simulados. Los datos son efimeros.
 * Conserva solo los ultimos registros para que las pruebas de carga no agoten la memoria.
 */
@ApplicationScoped
public class PaymentRepository {

    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Payment> payments = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Payment> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    public Payment save(Payment payment) {
        payments.put(payment.getPaymentId(), payment);
        return payment;
    }

    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId));
    }

}
