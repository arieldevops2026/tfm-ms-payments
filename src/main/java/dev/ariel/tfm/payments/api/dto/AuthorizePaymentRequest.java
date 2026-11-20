package dev.ariel.tfm.payments.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para solicitud de autorizacion de un pago simulado
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizePaymentRequest {

    @NotBlank(message = "orderId is required")
    @JsonProperty("orderId")
    private String orderId;

    @NotBlank(message = "customerId is required")
    @JsonProperty("customerId")
    private String customerId;

    @NotNull(message = "amount is required")
    @Valid
    @JsonProperty("amount")
    private AmountObj amount;

    @NotBlank(message = "paymentMethod is required")
    @JsonProperty("paymentMethod")
    private String paymentMethod;
}
