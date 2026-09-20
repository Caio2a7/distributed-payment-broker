package br.ufrn.dpb.ledger.controller;

import br.ufrn.dpb.ledger.dto.LedgerOperationDTO;
import br.ufrn.dpb.ledger.service.LedgerService;

import java.nio.ByteBuffer;

public class LedgerController {
    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public byte[] handleOperation(byte[] payload) {
        LedgerOperationDTO operation = LedgerOperationDTO.fromBytes(payload);

        long newBalance;
        if (operation.operationCode() == 1) {
            newBalance = ledgerService.credit(operation.mid(), operation.amount());
        } else if (operation.operationCode() == 2) {
            newBalance = ledgerService.refund(operation.mid(), operation.amount());
        } else {
            throw new IllegalArgumentException("Unsupported ledger operation: " + operation.operationCode());
        }

        return ByteBuffer.allocate(Long.BYTES).putLong(newBalance).array();
    }
}
