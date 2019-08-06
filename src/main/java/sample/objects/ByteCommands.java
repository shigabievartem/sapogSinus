package sample.objects;

public enum ByteCommands {
    START_BOOT_COMMAND(new byte[]{0x7F}, 1),
    ACK(new byte[]{0x79}),
    NACK(new byte[]{0x1F}),
    GET(new byte[]{0x00, (byte) 0xFF}, 15),
    GET_VERSION(new byte[]{0x01, (byte) 0xFE}, 5);

    //
    private byte[] bytes;
    /**
     * Кол-во байт ожидаемых в случае успешного ответа
     * 0 - значит команда не ожидает ответа
     */
    private int expectedBytesCount;

    ByteCommands(byte[] code) {
        this(code, 0);
    }

    ByteCommands(byte[] code, int bytesCount) {
        this.bytes = code;
        this.expectedBytesCount = bytesCount;
    }

    public static boolean isAck(byte byteCode) {
        return ACK.bytes[0] == byteCode;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getExpectedBytesCount() {
        return expectedBytesCount;
    }
}
