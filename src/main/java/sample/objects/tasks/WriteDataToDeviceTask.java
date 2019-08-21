package sample.objects.tasks;

import javafx.concurrent.Task;
import sample.controllers.MainWindowController;
import sample.controllers.ProgressWindowController;
import sample.utils.BackendCaller;
import sample.utils.SapogUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static sample.objects.ByteCommands.*;
import static sample.utils.SapogConst.*;
import static sample.utils.SapogUtils.*;

public class WriteDataToDeviceTask extends Task<Void> {

    private final ProgressWindowController progressWindowController;
    private final MainWindowController mainController;
    private final byte[] fileDataBytes;
    private final AtomicInteger i = new AtomicInteger(1);
    private final int operationCount = 11;


    /**
     * Бин в котором обращаемся к бэкэнду
     */
    private final BackendCaller backendCaller = BackendCaller.getInstance();

    public WriteDataToDeviceTask(ProgressWindowController progressWindowController, MainWindowController mainController, byte[] fileDataBytes) {
        this.progressWindowController = progressWindowController;
        this.mainController = mainController;
        this.fileDataBytes = fileDataBytes;
    }


    @Override
    protected Void call() {
        CompletableFuture.runAsync(() -> mainController.sendCommand("boot"))
                .handle(prepareBiFunction(() -> mainController.disconnect(null), "disconnect from device"))
                .handle(prepareBiFunction(mainController::connectInBootloaderMode, "connect to device in bootloader mode"))
                .handle(prepareBiFunction(this::connectToDevice, "send to initial byte to device"))
                .handle(prepareBiFunction(this::getDeviceVersionAction, "check bootloader version"))
                .handle(prepareBiFunction(this::eraseDeviceAction, "erase data from device"))
                .handle(prepareBiFunction(this::writeDataToDeviceFunction, "write data to device action"))
                .handle(prepareBiFunction(this::checkFlashMemory, "validation installed data"))
                .handle(prepareBiFunction(this::rebootDeviceAction, "execute data from device"))
                .handle(prepareBiFunction(() -> mainController.disconnect(null), null))
                .handle(prepareBiFunction(() -> mainController.connect(null), null));

        return null;
    }

    private <T> BiFunction<? super T, Throwable, Boolean> prepareBiFunction(Supplier<Boolean> command, String operationTitle) {
        return (value, exc) -> {
            try {
                // Если предыдущая операция прошла не успешно, последующие операции делать не надо
                if (exc != null) {
                    printError(progressWindowController.getConsole(), exc);
                    return false;
                }
                if (value instanceof Boolean && !(Boolean) value) return false;
                if (operationTitle != null) print("Executing operation: '%s'", operationTitle);
                return CompletableFuture.supplyAsync(command).get(100, SECONDS);
            } catch (Exception e) {
                printError(progressWindowController.getConsole(), e);
                return false;
            } finally {
                this.updateProgress(i.incrementAndGet(), operationCount);
            }
        };
    }

    private <T> BiFunction<? super T, Throwable, Boolean> prepareBiFunction(Runnable command, String operationTitle) {
        return prepareBiFunction(() -> {
            try {
                CompletableFuture.runAsync(command).get(100, SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return true;
        }, operationTitle);
    }

    /* Комманда для начала взаимодействия с платой */
    private Boolean connectToDevice() {
        // Проверяем версию, установленную на устройстве
        mainController.sendBytes(START_BOOT_COMMAND.getBytes());
        return isAck(backendCaller.readDataFromDevice(START_BOOT_COMMAND.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT)[0]);
    }

    /* Считываем версию драйвера с платы */
    private Boolean getDeviceVersionAction() {
        // Проверяем версию, установленную на устройстве
        mainController.sendBytes(GET_VERSION.getBytes());
        return checkBootloaderVersion(backendCaller.readDataFromDevice(GET_VERSION.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT));
    }

    /**
     * Метод проверяет версию bootloader'a, а заодно и позволяет убедиться, что мы находимся в нужном режиме
     *
     * @param dataFromDevice - ответ с контроллера
     * @return - true, если версия bootloader'a определенна, в противном случае - false
     */
    private boolean checkBootloaderVersion(byte[] dataFromDevice) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> {
                        if (dataFromDevice == null) return false;
                        printBytes(dataFromDevice);
                        if (dataFromDevice.length != 5) return false;
                        if (!isAck(dataFromDevice[0])) return false;
                        String message = String.format("Bootloader version: %02X", dataFromDevice[1]);
                        print(new StringBuilder(message).insert(message.length() - 1, ".").toString());
                        return true;
                    }
            ).get(DEFAULT_TIME_OUT, SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void print(String text, Object... objects) {
        SapogUtils.print(progressWindowController.getConsole(), text, objects);
    }

    /* Глобальная очистка памяти flash памяти(удаление драйвера) */
    private Boolean eraseDeviceAction() {
        mainController.sendBytes(ERASE_MEMORY.getBytes());
        if (!isAck(backendCaller.readDataFromDevice(ERASE_MEMORY.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT)[0]))
            return false;
        // Удалим все области памяти, а не конкретные страницы
        mainController.sendBytes(ERASE_MEMORY_GLOBAL_ERASE.getBytes());
        return isAck(backendCaller.readDataFromDevice(ERASE_MEMORY_GLOBAL_ERASE.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT)[0]);
    }


    /**
     * Запись драйвера на контроллер
     */
    private Boolean writeDataToDeviceFunction() {
        try {

            // Дополним байты из файла пустыми значениями, чтобы дальше просто записать их в память
            byte[] byteArrayToWriteInFlash = new byte[FLASH_SIZE];
            System.arraycopy(fileDataBytes, 0, byteArrayToWriteInFlash, 0, fileDataBytes.length);

            for (int pageNum = 0; pageNum < FLASH_MAX_PAGE_COUNT; pageNum++) {
                System.out.println(format("Write data to page[%s]", pageNum));
                for (int time = 0; time < WRITE_FLASH_TIMES_TO_REPEAT; time++) {

                    // Проверим, а не закончили ли мы запись драйвера на контроллер
                    if (fileDataBytes.length < calculateStartBytePosition(pageNum, time))
                        return true;

                    mainController.sendBytes(WRITE_MEMORY.getBytes());
                    if (!isAck(backendCaller.readDataFromDevice(WRITE_MEMORY.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT)[0]))
                        return false;

                    writePageMemory(pageNum, time, byteArrayToWriteInFlash);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Рассчёт стартовой позиции байта в массиве с которой необходимо продолжить запись
     */
    private int calculateStartBytePosition(int pageNum, int time) {
        return pageNum * WRITE_FLASH_TIMES_TO_REPEAT * DEFAULT_BYTE_COUNT_TO_WRITE + time * DEFAULT_BYTE_COUNT_TO_WRITE;
    }

    /**
     * Рассчёт стартовой позиции байта в массиве с которой необходимо продолжить запись
     */
    private int calculateCurrentFlashMemoryPosition(int pageNum, int time) {
        return FLASH_MEMORY_START_PAGE_BYTE + pageNum * FLASH_MEMORY_PAGE_STEP + time * DEFAULT_BYTE_COUNT_TO_WRITE;
    }

    private void writePageMemory(int pageNum, int time, byte[] bytesToWrite) {
        // TODO надо объединить методы записи и чтения, ибо они очень похожи
        mainController.sendBytes(convertPageNumToBytesAndCheckSum(calculateCurrentFlashMemoryPosition(pageNum, time)));

        if (!isAck(backendCaller.readDataFromDevice(1, DEVICE_ANSWER_TIMEOUT)[0]))
            throw new RuntimeException(format("Can't write data to page [%s]", pageNum));

        mainController.sendBytes(prepareWriteByteArray(pageNum, time, bytesToWrite));

        if (!isAck(backendCaller.readDataFromDevice(1, DEVICE_ANSWER_TIMEOUT)[0]))
            throw new RuntimeException(format("Can't write data to page [%s]. Bad checksum.", pageNum));
    }

    /**
     * Записываем все байты которые у нас есть, остальные просто заполняем 0xFF
     */
    private byte[] prepareWriteByteArray(int pageNum, int time, byte[] bytesToWrite) {
        // +2 тк: 1 место под передаваемое кол-во бай, 2 место под чек-сумму
        byte[] resultByteArray = new byte[DEFAULT_BYTE_COUNT_TO_WRITE + 2];
        // Кол-во байтов для контроллера (макс 255)
        resultByteArray[0] = (byte) DEFAULT_BYTE_COUNT_TO_WRITE - 1;
        //
        // ТК мы ранее дозаполнили массив пустыми байтами, то у байт в массиве хватит, чтобы заполнить всю flash память
        System.arraycopy(bytesToWrite, calculateStartBytePosition(pageNum, time), resultByteArray, 1, DEFAULT_BYTE_COUNT_TO_WRITE);

        byte checkSum = xorBytes(resultByteArray);
        resultByteArray[resultByteArray.length - 1] = checkSum;
        return resultByteArray;
    }

    /**
     * Выполняем команду GO и перезагружаем устройство
     */
    private Boolean rebootDeviceAction() {
        byte[] goBytes = GO.getBytes();
        // Проверяем версию, установленную на устройстве
        mainController.sendBytes(goBytes);

        if (!isAck(backendCaller.readDataFromDevice(GO.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT)[0]))
            throw new RuntimeException(format("Controller decline request 0x%02X+0x%02X", goBytes[0], goBytes[1]));

        mainController.sendBytes(convertPageNumToBytesAndCheckSum(FLASH_MEMORY_START_PAGE_BYTE));

        if (!isAck(backendCaller.readDataFromDevice(1, DEVICE_ANSWER_TIMEOUT)[0]))
            throw new RuntimeException(format("Can't execute application from start address 0x%08X", FLASH_MEMORY_START_PAGE_BYTE));

        return true;
    }

    /* Считываем данные с flash памяти */
    private boolean checkFlashMemory() {
        byte[] readResultArray = new byte[FLASH_SIZE];
        for (int i = 0; i < FLASH_MAX_PAGE_COUNT; i++) {
            System.out.println(format("read data from page [%s]", i));
            for (int time = 0; time < WRITE_FLASH_TIMES_TO_REPEAT; time++) {

                // Проверяем версию, установленную на устройстве
                mainController.sendBytes(READ_MEMORY.getBytes());
                if (!isAck(backendCaller.readDataFromDevice(READ_MEMORY.getExpectedBytesCount(), DEVICE_ANSWER_TIMEOUT)[0]))
                    return false;

                try {
                    byte[] dataFromFlash = readPageMemory(i, time);
                    System.out.println(format("read data size: %s", dataFromFlash.length));
                    System.arraycopy(dataFromFlash, 0, readResultArray, calculateStartBytePosition(i, time), dataFromFlash.length);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        }
        System.out.println("Data successfully read from flash memory!");

        for (int i = 0; i < fileDataBytes.length; i++) {
            if (readResultArray[i] != fileDataBytes[i]) {
                print(format("[%s] bytes not equals! Actual = '0x%02X'; Expected = '0x%02X'",
                        i, readResultArray[i], fileDataBytes[i]));
                return false;
            }
        }

        print("Driver successfully installed!");
        return true;
    }

    private byte[] readPageMemory(int pageNum, int time) {
        // Номер страницы рассчитывается из рассчета начальной страницы + шаг страницы * на номер текущей страницы
        mainController.sendBytes(convertPageNumToBytesAndCheckSum(calculateCurrentFlashMemoryPosition(pageNum, time)));

        if (!isAck(backendCaller.readDataFromDevice(1, DEVICE_ANSWER_TIMEOUT)[0]))
            throw new RuntimeException(format("Can't read data from page [%s]", pageNum));

        mainController.sendBytes(
                new byte[]{
                        (byte) DEFAULT_BYTE_COUNT_TO_READ,
                        xorBytes(new byte[]{(byte) DEFAULT_BYTE_COUNT_TO_READ, (byte) 0xFF})
                }
        );
        if (!isAck(backendCaller.readDataFromDevice(1, DEVICE_ANSWER_TIMEOUT)[0]))
            throw new RuntimeException(format("Wrong bytes count [%s]", DEFAULT_BYTE_COUNT_TO_READ));

        return backendCaller.readDataFromDevice(DEFAULT_BYTE_COUNT_TO_READ + 1, DEVICE_ANSWER_TIMEOUT);
    }
}
