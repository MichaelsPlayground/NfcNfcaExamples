# NFC NFCA Examples

This app reads NCFA data from a NFC-Tag of type **NXP NTAG21x** only.

```plaintext
    <uses-permission android:name="android.permission.NFC" />
    <uses-permission android:name="android.permission.VIBRATE" />
```

Data sheet 

NTAG213/215/216 NFC Forum Type 2 Tag compliant IC with 144/504/888 bytes  
user memory

Rev. 3.2 — 2 June 2015 Product data sheet

https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf

User memory is in pages 04 (04x) up to 225 (E0x) = 222 pages of 4 bytes of data

NTAG21x command overview
```plaintext
All available commands for NTAG21x are shown in Table 22. Table 22. Command overview
 
Command[1]
ISO/IEC 14443
NFC FORUM
Command code (hexadecimal)

Request
REQA
SENS_REQ
26h (7 bit)

Wake-up
WUPA
ALL_REQ
52h (7 bit)

Anticollision CL1
Anticollision CL1
SDD_REQ CL1
93h 20h

Select CL1
Select CL1
SEL_REQ CL1
93h 70h

Anticollision CL2
Anticollision CL2
SDD_REQ CL2
95h 20h

Select CL2
Select CL2
SEL_REQ CL2
95h 70h

Halt
HLTA
SLP_REQ
50h 00h

GET_VERSION[2]
-
-
60h

READ
-
READ
30h

FAST_READ[2]
-
-
3Ah

WRITE
-
WRITE
A2h

COMP_WRITE
-
-
A0h

READ_CNT[2]
-
-
39h

PWD_AUTH[2]
-
-
1Bh

READ_SIG[2]
-
-
3Ch

NTAG213_215_216
Product data sheet COMPANY PUBLIC
[1] Unless otherwise specified, all commands use the coding and framing as described in Ref. 1.
[2] This command is new in NTAG21x compared to NTAG203.
```

NTAG ACK and NAK - NTAG uses a 4 bit ACK / NAK as shown in Table 23. Table 23. ACK and NAK values
```plaintext
Code (4-bit)
ACK/NAK

Ah
Acknowledge (ACK)

0h
NAK for invalid argument (i.e. invalid page address)

1h
NAK for parity or CRC error

4h
NAK for invalid authentication counter overflow

5h
NAK for EEPROM write error
```

GET_VERSION command
```plaintext
Name
Code
Description
Length

Cmd
60h
Get product version
1 byte

CRC
-
CRC according to Ref. 1
2 bytes

Data
-
Product version information
8 bytes

NAK
see Table 23
see Section 9.3
4-bit
```

GET_VERSION response for NTAG213, NTAG215 and NTAG216
```plaintext
Byte no.
Description
NTAG213
NTAG215
NTAG216
Interpretation

0
fixed Header
00h
00h
00h

1
vendor ID
04h
04h
04h
NXP Semiconductors

2
product type
04h
04h
04h
NTAG

3
product subtype
02h
02h
02h
50 pF

4
major product version
01h
01h
01h

1

5
minor product version
00h
00h
00h
V0

6
storage size
0Fh
11h
13h
see following information

7
protocol type
03h
03h
03h
ISO/IEC 14443-3 compliant
```

READ
The READ command requires a start page address, and returns the 16 bytes of four NTAG21x pages. 
For example, if address (Addr) is 03h then pages 03h, 04h, 05h, 06h are returned. Special 
conditions apply if the READ command address is near the end of the accessible memory area. 
The special conditions also apply if at least part of the addressed pages is within a password 
protected area. For details on those cases and the command structure refer to Figure 16 and 
Table 29.
```plaintext
In the initial state of NTAG21x, all memory pages are allowed as Addr parameter to the READ command.
• page address 00h to 2Ch for NTAG213 
• page address 00h to 86h for NTAG215 
• page address 00h to E6h for NTAG216
Addressing a memory page beyond the limits above results in a NAK response from NTAG21x.
READ command Name Code Description Length
Cmd 30h read four pages 1 byte 
Addr - start page address 1 byte
CRC - CRC according to Ref. 1 2 bytes
Data - Data content of the addressed pages 16 bytes
NAK see Table 23 see Section 9.3 4-bit
```

FAST_READ
The FAST_READ command requires a start page address and an end page address and returns the 
all n*4 bytes of the addressed pages. For example if the start address is 03h and the end 
address is 07h then pages 03h, 04h, 05h, 06h and 07h are returned. If the addressed page is 
outside of accessible area, NTAG21x replies a NAK. For details on those cases and the command 
structure, refer to Figure 17 and Table 31.
```plaintext
FAST_READ command Name Code Description Length
Cmd 3Ah read multiple pages 1 byte 
StartAddr - start page address 1 byte
EndAddr - end page address 1 byte
CRC - CRC according to Ref. 1 2 bytes
Data - data content of the addressed pages n*4 bytes
NAK see Table 23 see Section 9.3 4-bit

In the initial state of NTAG21x, all memory pages are allowed as StartAddr parameter to the 
FAST_READ command.
• page address 00h to 2Ch for NTAG213 
• page address 00h to 86h for NTAG215 
• page address 00h to E6h for NTAG216
Addressing a memory page beyond the limits above results in a NAK response from NTAG21x.

The EndAddr parameter must be equal to or higher than the StartAddr.
```

WRITE
The WRITE command requires a block address, and writes 4 bytes of data into the addressed 
NTAG21x page. The WRITE command is shown in Figure 18 and Table 33.
```plaintext
WRITE command Name Code Description Length
Cmd A2h write one page 1 byte 
Addr - page address 1 byte
CRC - CRC according to Ref. 1 2 bytes
Data - data 4 bytes
NAK see Table 23 see Section 9.3 4-bit
```

READ_SIG
The READ_SIG command returns an IC specific, 32-byte ECC signature, to verify 
NXP Semiconductors as the silicon vendor. The signature is programmed at chip production 
and cannot be changed afterwards. The command structure is shown in Figure 23 and Table 41.
```plaintext
READ_SIG command
Name Code Description Length
Cmd 3Ch read ECC signature 1 byte
Addr 00h RFU, is set to 00h 1 byte
CRC - CRC according to Ref. 1 2 bytes
Signature - ECC signature 32 bytes
NAK see Table 23 see Section 9.3 4 bit
```




