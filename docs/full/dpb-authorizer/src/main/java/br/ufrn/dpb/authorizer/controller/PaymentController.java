package br.ufrn.dpb.authorizer.controller;

import br.ufrn.dpb.authorizer.dto.PaymentRequestDTO;
import br.ufrn.dpb.authorizer.dto.PaymentResponseDTO;
import br.ufrn.dpb.authorizer.model.Transaction;
import br.ufrn.dpb.authorizer.service.PaymentService;
import br.ufrn.dpb.middleware.annotation.OnMessage;
import br.ufrn.dpb.middleware.protocol.Message;

public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @OnMessage(Message.Type.REQUEST)
    public byte[] handlePayment(byte[] payload) {
        PaymentRequestDTO request = PaymentRequestDTO.fromBytes(payload);

        Transaction transaction = paymentService.processPayment(
            request.mid(),
            request.operation(),
            request.cardNumber(),
            request.expMonth(),
            request.expYear(),
            request.grossAmount(),
            request.installments()
        );

        PaymentResponseDTO response = new PaymentResponseDTO(
            transaction.getFingerprint(),
            transaction.getStatus(),
            transaction.getLiquidAmount()
        );

        return response.toBytes();
    }
}
