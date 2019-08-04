package sample.objects;

import static sample.utils.SapogUtils.printBytes;

public enum ByteCommands {
    ACK(new byte[]{0x79}),
    NACK(new byte[]{0x1F}),
    GET(new byte[]{0x00, (byte) 0xFF}),
    GET_VERSION(new byte[]{0x01, (byte) 0xFE});

    //
    private byte[] bytes;

    ByteCommands(byte[] code) {
        this.bytes = code;
    }

    public static boolean isAck(byte byteCode) {
        return ACK.bytes[0] == byteCode;
    }

    public byte[] getBytes() {
        System.out.println(String.format("byte command '%s' contain byte code:", this.name()));
        printBytes(bytes);
        return bytes;
    }
}
