package br.ufrn.dpb.server.controller;

import br.ufrn.dpb.server.dto.TransferRequestDTO;
import br.ufrn.dpb.server.dto.TransferResponseDTO;
import br.ufrn.dpb.server.model.Transaction;
import br.ufrn.dpb.server.model.UdpMessageHandler;
import br.ufrn.dpb.server.network.protocol.MessageType;
import br.ufrn.dpb.server.network.protocol.Packet;
import br.ufrn.dpb.server.service.PaymentService;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class PaymentController implements UdpMessageHandler {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public byte[] onMessage(byte[] data, InetAddress senderAddress, int port) {
        long requestId = 0L;

        try {
            Packet requestPacket = Packet.fromBytes(data, data.length);
            requestId = requestPacket.getRequestId();

            if (requestPacket.getType() != MessageType.TRANSFER_REQUEST) {
                throw new IllegalArgumentException("Tipo de mensagem nao suportado: " + requestPacket.getType());
            }

            TransferRequestDTO requestDTO = TransferRequestDTO.fromBytes(requestPacket.getPayload());
            Transaction transaction = paymentService.ProcessTransaction(
                    requestDTO.sourceId(),
                    requestDTO.destId(),
                    requestDTO.amount()
            );
            System.out.println("Requisição recebida:\n| SourceId: "+requestDTO.sourceId()+"\n| DestinationId: "+requestDTO.destId()+"\n| Amount: "+requestDTO.amount());

            TransferResponseDTO responseDTO = new TransferResponseDTO(transaction.getId(), BigDecimal.ZERO);
            Packet responsePacket = new Packet(MessageType.TRANSFER_RESPONSE, requestId, responseDTO.toBytes());
            return responsePacket.toBytes();

        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro no processamento";
            byte[] errorPayload = errorMessage.getBytes(StandardCharsets.UTF_8);
            Packet errorPacket = new Packet(MessageType.ERROR, requestId, errorPayload);
            return errorPacket.toBytes();
        }
    }
}
