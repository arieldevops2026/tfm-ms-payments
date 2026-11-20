package dev.ariel.tfm.payments.util.properties;

import io.smallrye.config.ConfigMapping;

import java.math.BigDecimal;

@ConfigMapping(prefix = "payment")
public interface PaymentProps {

    BigDecimal maxAmount();
}
