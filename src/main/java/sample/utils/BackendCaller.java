package sample.utils;

import javafx.scene.control.TextArea;
import jssc.SerialNativeInterface;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPortTimeoutException;
import org.jetbrains.annotations.NotNull;
import sample.objects.ConnectionInfo;
import sample.objects.exceptions.OperationTimeOutException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static jssc.SerialPort.BAUDRATE_115200;
import static jssc.SerialPort.DATABITS_8;
import static sample.utils.SapogConst.NO_CONNECTION;

public class BackendCaller {

    private TextArea mainConsole;

    public void setMainConsole(@NotNull TextArea mainConsole) {
        this.mainConsole = mainConsole;
    }

    private SerialDevice serial = null;
    /* EO All things serial */

    private static volatile BackendCaller instance;

    public static BackendCaller getInstance() {
        BackendCaller localInstance = instance;
        if (localInstance == null) {
            synchronized (BackendCaller.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new BackendCaller();
                }
            }
        }
        return localInstance;
    }

    public synchronized Object getCurrentValue(String fieldName) throws IOException {
        //System.out.println(format("Loading value for field '%s'", fieldName));
        serial.loadParam(fieldName);
        return null;
    }

    public synchronized void setValue(String fieldName, Object value) throws IOException {
        //System.out.println(format("Saving new value '%s' for field '%s'", value, fieldName));
        serial.saveParam(fieldName, value);
    }

    public synchronized ConnectionInfo checkConnection() {
        return (serial == null) ? NO_CONNECTION : serial.getConnectionInfo();
    }

    public synchronized void connect(@NotNull String port) throws IOException {
        connectImpl(port, new SerialDevice("default_port", port));
    }

    /**
     * Коннект к контроллеру в режиме bootloader'a
     * <p>
     * Отдельный метод с рассчетом на то, что возможно параметры подключения будут браться из интерфейса
     */
    public synchronized void connectInBootloaderMode(@NotNull String port) throws IOException {
        connectImpl(port, new SerialDevice("bootloader_mode", port, BAUDRATE_115200, DATABITS_8, Parity.EVEN, 10, true));
    }

    private void connectImpl(String port, SerialDevice device) {
        Objects.requireNonNull(port, "Empty port!");
        if ((serial == null) || !serial.isOpened()) {
            serial = device;
            serial.setConsole(mainConsole);
        } else {
            System.out.println(format("Port '%s' is already open!", port));
        }

    }

    public synchronized void activateConsole() {
        if (serial != null && serial.isOpened())
            serial.startReaderThread();
    }

    public Map<String, Object> getCurrentValues() throws IOException {
        if (serial != null) {
            try {
                sendCommand("cfg list");
                TimeUnit.MILLISECONDS.sleep(2000);
                return serial.getCurrentParamMap();
            } catch (InterruptedException e) {
                return null;
                //TODO return smth meaningful?
            }
        }
        return null;
    }

    /***
     * Метод отправки команды
     * @param text - команда отправленная из консоли
     * @return - ответ, который будет распечатан в консоли. Если строка пустая или null в консоль ничего не попадёт
     */
    public String sendCommand(String text) throws IOException {
        serial.sendString(text);
        return "";
    }

    public String sendCommand(byte[] bytes) throws IOException {
        serial.sendBytes(bytes);
        return "";
    }

    public void closeSerial() {
        if (serial != null) {
            try {
                serial.close();
                System.out.println("Serial device was successfully disconnected!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String[] getPortNames() {
        String[] portNames;
        if (SerialNativeInterface.getOsType() == SerialNativeInterface.OS_MAC_OS_X) {
            // for MAC OS default pattern of jssc library is too restrictive
            portNames = SerialPortList.getPortNames("/dev/", Pattern.compile("tty\\..*"));
        } else {
            portNames = SerialPortList.getPortNames();
        }
        return Arrays.asList(portNames).toArray(new String[0]);
    }

    /**
     * @param byteCount - Минимальное кол-во байт для начала чтения с платы данных
     * @param timeout   - Максимальное время ожидания
     * @return - Данные, считанные с платы
     */
    public byte[] readDataFromDevice(int byteCount, int timeout) {
        try {
            return Objects.requireNonNull(serial).readData(byteCount, timeout);
        } catch (SerialPortException e) {
            System.err.println("Error when read data from device");
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (SerialPortTimeoutException e) {
            System.err.println("Read data operation time is out.");
            e.printStackTrace();
            throw new OperationTimeOutException(e);
        }
    }
}