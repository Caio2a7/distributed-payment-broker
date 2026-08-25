\x01
\x00\x00\x00\x00\x00\x00\x00\x01
\x00\x00\x00\x19
\x00\x00\x00\x00\x00\x00\x00\x01
\x00\x00\x00\x00\x00\x00\x00\x02
\x00\x00\x00\x00
\x00\x02
\x03\xE7

 printf "\x01\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x00\x17\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x02\x00\x00\x00\x00\x00\x01\x32" | nc -u -w 2 127.0.0.1 8080 | xxd -p


| Ordem | Campo | Tipo Java | Decimal no RapidTables | Linha para copiar (Bits) | Hexadecimal resultante | Formato `\x` |
|---|---|---|---|---|---|---|
| 1 | `type` | Byte | `1` | 8-bit | `01` | `\x01` |
| 2 | `requestId` | Long | `1` | 64-bit | `0000000000000001` | `\x00\x00\x00\x00\x00\x00\x00\x01` |
| 3 | `payloadLength` | Int | `23` | 32-bit | `00000017` | `\x00\x00\x00\x17` |
| 4 | `sourceId` | Long | `1` | 64-bit | `0000000000000001` | `\x00\x00\x00\x00\x00\x00\x00\x01` |
| 5 | `destId` | Long | `2` | 64-bit | `0000000000000002` | `\x00\x00\x00\x00\x00\x00\x00\x02` |
| 6 | `scale` | Int | `0` | 32-bit | `00000000` | `\x00\x00\x00\x00` |
| 7 | `amountLength` | Short | `1` | 16-bit | `0001` | `\x00\x01` |
| 8 | `amountBytes` | BigInteger | `50` | 8-bit | `32` | `\x32` |
