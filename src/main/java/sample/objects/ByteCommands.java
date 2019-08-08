package sample.objects;

/**
 * Комманды для общения с платой
 * Необходимо использовать в строгом порядке, согласно протоколу и в режиме bootloader'a
 * Подробное описание комманд так же в протоколе
 */
public enum ByteCommands {
    // Команда сообщающая, что контроллеру необходимо начать считывать комманды
    START_BOOT_COMMAND(new byte[]{0x7F}, 1),
    // Подтверждение
    ACK(new byte[]{0x79}),
    // Отрицание
    NACK(new byte[]{0x1F}),
    // Получить полную информацию о плате
    GET(new byte[]{0x00, (byte) 0xFF}, 15),
    // Метод возвращает версию прошивки на плате
    GET_VERSION(new byte[]{0x01, (byte) 0xFE}, 5),
    // Комманды для считывания данных с контроллера
    READ_MEMORY(new byte[]{0x11, (byte) 0xEE}, 1),
    // Комманды для очистки памяти
    ERASE_MEMORY(new byte[]{0x43, (byte) 0xBC}, 1),
    ERASE_MEMORY_GLOBAL_ERASE(new byte[]{(byte) 0xFF, 0x00}, 1),
    // Команды для записи данных на контроллер
    WRITE_UNPROTECT(new byte[]{0x31, (byte) 0xCE}, 1);

    // Байты из которых состоит комманда для контроллера
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
