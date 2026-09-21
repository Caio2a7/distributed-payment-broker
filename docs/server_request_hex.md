
printf "\x01\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x00\x18\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x02\x00\x00\x00\x00\x00\x00\x00\x84" | nc -u -w 2 127.0.0.1 9090 | xxd -p





| Ordem | Campo | Tipo Java | Decimal no RapidTables | Linha para copiar (Bits) | Hexadecimal resultante | Formato `\x` |
|---|---|---|---|---|---|---|
| 1 | `type` | Byte | `1` | 8-bit | `01` | `\x01` |
| 2 | `requestId` | Long | `1` | 64-bit | `0000000000000001` | `\x00\x00\x00\x00\x00\x00\x00\x01` |
| 3 | `payloadLength` | Int | `23` | 32-bit | `00000017` | `\x00\x00\x00\x17` |
| 4 | `sourceId` | Long | `1` | 64-bit | `0000000000000001` | `\x00\x00\x00\x00\x00\x00\x00\x01` |
| 5 | `destId` | Long | `2` | 64-bit | `0000000000000002` | `\x00\x00\x00\x00\x00\x00\x00\x02` |


cat frame.bin | nc -u -w 2 127.0.0.1 9090 | xxd -p

#pragma endian big

struct Frame{
    char idempotencyKey[16];
    u64 sourceId;
    u64 destId;
    u64 amount;
    u32 payloadLength;
    u8 type;
};

Frame frame @ 0x00;
