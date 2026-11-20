package dev.ariel.tfm.payments.api.resource;

import dev.ariel.tfm.payments.api.dto.AuthorizePaymentRequest;
import dev.ariel.tfm.payments.api.dto.Payment;
import dev.ariel.tfm.payments.api.service.PaymentService;
import dev.ariel.tfm.payments.api.service.RequestService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

/**
 * Resource para operaciones de pagos simulados
 */
@Slf4j
@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PaymentResource {

    private final PaymentService service;
    private final RequestService requestService;

    /**
     * Endpoint para autorizar un pago simulado
     */
    @POST
    @Path("/authorize")
    @RunOnVirtualThread
    public Payment authorize(@Context HttpServerRequest context, @NotNull @Valid AuthorizePaymentRequest request) {
        requestService.onInitialProcess(context, request);

        Payment payment = service.authorize(request);

        requestService.onFinalProcess(payment);
        return payment;
    }

    /**
     * Endpoint para obtener un pago por su id
     */
    @GET
    @Path("/get-by-id/{id}")
    @RunOnVirtualThread
    public Payment getById(@Context HttpServerRequest context, @PathParam("id") String id) {
        requestService.onInitialProcess(context, id);

        Payment payment = service.getPaymentById(id);

        requestService.onFinalProcess(payment);
        return payment;
    }

}
