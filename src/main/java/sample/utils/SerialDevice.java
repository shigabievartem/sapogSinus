package sample.utils;

import javafx.event.Event;
import javafx.scene.control.TextArea;
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sample.objects.ConnectionInfo;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static sample.utils.SapogConst.Events.connectionLost;

public class SerialDevice {
    private static final Logger LOG = LoggerFactory.getLogger(SerialDevice.class);

    private String name;
    private String device;
    private int baudRate, dataBits, stopBits;
    private Parity parity;

    private SerialPort port;

    private long reopenAt = 0;
    private long reopenTimeoutMS = 0;
    private boolean traceWriteFailures = true;

    private SerialState portState = SerialState.NOT_OPEN;

    private TextArea mainConsole = null;

    private boolean readThreadShouldExit = false;

    private Thread readerThread = null;
    private Thread stat2Thread = null;

    public Map<String, Object> getCurrentParamMap() {
        final HashMap<String, Object> integerObjectHashMap = new HashMap<>(currentParamMap);
        return integerObjectHashMap;
    }

    /* Internal values */
    private double voltage = 0.0f;
    private double current = 0.0f;
    private int RPM = 0;
    private double DC = 0.0f;
    private double temp = 0.0f;
    private int limits = 0;
    private int ZCF = 0;
    private String version = "";


    Map<String, Object> currentParamMap = new HashMap<>();

    /**
     * Конструктор для контроллера
     *
     * @param name             - Наименование подключения
     * @param port             - Порт контроллера
     * @param newBaud          - Baud rate
     * @param dataBits         - Data bits
     * @param parity           - Parity
     * @param timeout          - timeout в секундах
     * @param isBootloaderMode - в каком режиме находится контролер: true - в режиме bootloader'a, иначе - false
     */
    public SerialDevice(@NotNull String name, @NotNull String port, int newBaud, int dataBits, @NotNull Parity parity, long timeout, boolean isBootloaderMode) throws IOException {
        // TODO Refactor
        // TODO throw exception
        this.name = name;
        this.device = port;
        this.baudRate = newBaud;
        this.dataBits = dataBits;
        this.stopBits = SerialPort.STOPBITS_1;
        this.parity = parity;
        this.reopenTimeoutMS = timeout * 1000;
        this.traceWriteFailures = true;

        if (!isBootloaderMode) initializeParamMap();

        tryReopen(isBootloaderMode);
    }


    public SerialDevice(@NotNull String name, @NotNull String newDevice) throws IOException {
        // TODO Refactor, inherit from main constructor
        // TODO throw exception
        this(name, newDevice, SerialPort.BAUDRATE_115200, SerialPort.DATABITS_8, Parity.NONE, 1, false);
    }

    /**
     * Инициализация мапы с дефолтными параметрами
     */
    private void initializeParamMap() {
        currentParamMap.put("esc_base", 256);
        currentParamMap.put("esc_index", 0);
        currentParamMap.put("pwm_enable", 0);
        currentParamMap.put("mot_num_poles", 14);
        currentParamMap.put("mot_dc_slope", 5f);
        currentParamMap.put("mot_dc_accel", 0.09f);
        currentParamMap.put("mot_pwm_hz", 20000);
        currentParamMap.put("ctl_dir", 0);
        currentParamMap.put("temp_lim", 100);
        currentParamMap.put("mot_i_max", 20);
        currentParamMap.put("sens_i_scale", 1);
        currentParamMap.put("uavcan_node_id", 0);
        currentParamMap.put("cmd_ttl_ms", 200);
        currentParamMap.put("pwm_max_usec", 2000);
        currentParamMap.put("pwm_min_usec", 1000);
        currentParamMap.put("mot_pwm_blank", 0.500000);
        currentParamMap.put("mot_pwm_dt_ns", 600);
        currentParamMap.put("mot_zc_fails_max", 100);
        currentParamMap.put("rpmctl_i", 0.001000);
        currentParamMap.put("rpmctl_d", 0.000000);
        currentParamMap.put("rpmctl_p", 0.000100);
        currentParamMap.put("mot_stop_thres", 7);
        currentParamMap.put("mot_lpf_freq", 20.000000);
        currentParamMap.put("mot_rpm_min", 1000);
        currentParamMap.put("mot_spup_vramp_t", 3.000000);
        currentParamMap.put("mot_v_spinup", 0.500000);
        currentParamMap.put("mot_v_min", 2.500000);
    }

    public ConnectionInfo getConnectionInfo() {
        return new ConnectionInfo(
                getPortState() != SerialState.NOT_OPEN,
                voltage,
                current,
                DC,
                RPM,
                //TODO добавить версию
                "sapog-sinus-v1.02"
        );
    }

    Runnable serialReader = new Runnable() {
        public void run() {
            StringBuilder message = new StringBuilder();
            System.out.println("Starting serialReader thread");
            StringBuilder lastLine = new StringBuilder();
            // Количество раз, когда не удалось считать данные из буфера
            int readEmptyBufferCount = 0;
            while (!readThreadShouldExit && (portState != SerialState.NOT_OPEN)) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                byte buffer[];
                try {
                    if ((buffer = port.readBytes()) != null) {
                        //TODO удалить лог
//                        System.out.println("Bytes from device:");
//                        printBytes(buffer);
                        for (byte b : buffer) {
                            readEmptyBufferCount = 0;
                            //message.append((char) b);
                            lastLine.append((char) b);
                            if (b == '\n') {
                                if (!tryExtractStat2(lastLine.toString())) {
                                    if (!tryExtractParam(lastLine.toString())) {
                                        if (!tryExtractVersion(lastLine.toString())) {
                                            if (!tryExtractCommand(lastLine.toString())) {
                                                logToConsole(lastLine.toString().getBytes());
                                            }
                                        }
                                    }
                                }
                                //message.setLength(0);
                                lastLine.setLength(0);
                            }
                        }
                    } else {
                        // Если мы не получили из буфера ничего 100 раз, то проверим, возможно у нас просто отвалилось соединение
                        if (++readEmptyBufferCount > 150000) {
                            readEmptyBufferCount = 0;
                            System.out.println("Check connection...");
                            if (!isConnected()) {
                                System.out.println("Connection to device lost!");
                                mainConsole.fireEvent(new Event(connectionLost));
                                return;
                            }
                        }

                    }
                } catch (SerialPortException e) {
                    //logToConsole(e.toString().getBytes());
                    System.out.println(format("Exception in serialReader thread: %s", e));
                }
            }
            System.out.println("Closing serialReader thread");
            return;
        }
    };


    Runnable stat2Updater = new Runnable() {
        public void run() {
            System.out.println("Starting stat2Updater thread");
            final byte buffer[] = new String("stat2\r\n").getBytes();
            while (!readThreadShouldExit && (portState != SerialState.NOT_OPEN)) {
                if (Thread.currentThread().isInterrupted() || !Thread.currentThread().isAlive()) {
                    return;
                }
                try {
                    tryWriteBytes(buffer);
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (IOException | InterruptedException e) {
                    System.out.println(format("Exception in stat2Updater thread: %s", e));
                }
            }
            System.out.println("Closing stat2Updater thread");
            return;
        }
    };

    private boolean tryExtractCommand(String s) {
        if (
                (s.indexOf("ch> stat2\r\n") == 0) ||
                        (s.indexOf("ch> cfg list\r\n") == 0) ||
                        (s.indexOf("ch> cfg set ") == 0) ||
                        (s.indexOf("ch> beep\r\n") == 0)
        ) {
            return true;
        }
        return false;
    }

    private boolean tryExtractVersion(String s) {
        int idxKey = s.indexOf("io.px4.sapog");
        if ((idxKey == 0)) { //length of "stat2      \r\n"
            String valStr = s.substring(0, s.length() - 2); //remove \r\n
            version = valStr;
        }
        return false;
    }

    /*
        STAT2<пробел>
        напряжение XX.XX<пробел>
        ток XXX.XX<пробел>
        RPM XXXXX<пробел>
        DC X.XX<пробел>
        температура XXX.XX<пробел>
        лимиты X<пробел>
        ZCF XX<перевод строки>
     */

    private boolean tryExtractStat2(String s) {
        int idxKey = s.indexOf("STAT2");
        if ((idxKey == 0) && (s.length() > 15)) { //length of "stat2      \r\n"
            String valStr = s.substring(idxKey + 6, s.length() - 2); //remove \r\n
            String[] data = valStr.split(" ");
            try {
                voltage = Double.parseDouble(data[0]);
                current = Double.parseDouble(data[1]);
                RPM = Integer.parseInt(data[2]);
                DC = Double.parseDouble(data[3]);
                temp = Double.parseDouble(data[4]);
                limits = Integer.parseInt(data[5]);
                ZCF = Integer.parseInt(data[6]);
                return true;
            } catch (NumberFormatException e) {
                System.out.println(format("Conversion error for stat2 string %s : %s", s, e.toString()));
            }
        }
        return false;
    }

    private boolean tryExtractParam(String s) {
        Iterator it = currentParamMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            String param = (String) pair.getKey();
            Object oldVal = pair.getValue();
            int idxKey = s.indexOf(param);
            int idxBracket = s.indexOf("[");
            int idxEq = s.indexOf("=");
            if (
                    (idxKey == 0) &&
                            (idxEq > param.length()) &&
                            (idxBracket > idxEq + 1)
            ) {
                String valStr = s.substring(idxEq + 1, idxBracket);
                valStr = valStr.replaceAll("\\s", "");
                //valStr = valStr.replaceAll("\\.",",");
                try {
                    if (oldVal instanceof Float) {
                        Float val = Float.parseFloat(valStr);
                        currentParamMap.put(param, val);
                    } else if (oldVal instanceof Boolean) {
                        Boolean val = Boolean.parseBoolean(valStr);
                        currentParamMap.put(param, val);
                    } else if (oldVal instanceof Integer) {
                        Float fval = Float.parseFloat(valStr);
                        Integer val = Math.round(fval);
                        currentParamMap.put(param, val);
                    }
                    return true;
                } catch (NumberFormatException e) {
                    System.out.println(format("Conversion error for parameter %s : %s", param, e.toString()));
                }
            }
        }
        return false;
    }

    public void setConsole(@NotNull TextArea console) {
        mainConsole = console;
    }

    public void tryReopen(boolean isBootloaderMode) throws IOException {
        port = new SerialPort(device);
        int jsscParity = 0;
        switch (parity) {
            case NONE:
                jsscParity = SerialPort.PARITY_NONE;
                break;
            case ODD:
                jsscParity = SerialPort.PARITY_ODD;
                break;
            case EVEN:
                jsscParity = SerialPort.PARITY_EVEN;
                break;
            case MARK:
                jsscParity = SerialPort.PARITY_MARK;
                break;
            case SPACE:
                jsscParity = SerialPort.PARITY_SPACE;
                break;
        }

        try {
            port.openPort();
            port.setParams(baudRate, dataBits, stopBits, jsscParity);
            portState = SerialState.IDLE;
            if (!isBootloaderMode) {
                if (!isConnected()) {
                    this.close();
                    throw new RuntimeException(String.format("Device is not connected to port '%s'!", this.port.getPortName()));
                }
                // Для дебага через консольку (общения с контроллером перенести при загрузке bootloader'a)
                startReaderThread();

                stat2Thread = new Thread(stat2Updater);
                stat2Thread.start();
            }
        } catch (SerialPortException e) {
            LOG.error("Cannot open port {}", getPortSpec());
            this.close();
            throw new IOException(e.toString());
        }

        LOG.info("Device {} is started successfully", getPortSpec());
        reopenAt = 0;
    }

    /**
     * Проверка, действительно ли устройство подключено
     */
    private boolean isConnected() {
        try {
            // Отправим комманду с получением текущей конфигурации устройства
            tryWriteBytes("stat2\r\n".getBytes());
            TimeUnit.MILLISECONDS.sleep(1000);
            // Если устройство отвечает на отправленную команду, значит мы успешно подключены
            byte[] buffer = port.readBytes();
            return buffer != null && buffer.length > 0;
        } catch (IOException | InterruptedException e) {
            System.out.println(format("stat2 command check connection exception: %s", e));
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

        return false;
    }

    public void startReaderThread() {
        readThreadShouldExit = false;
        if (readerThread == null || !readerThread.isAlive()) {
            readerThread = new Thread(serialReader);
            readerThread.start();
        }
    }

    public SerialState getPortState() {
        SerialState portState = this.portState;
        return portState;
    }

    public String getName() {
        return name;
    }

    public String getDevice() {
        return device;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public int getDataBits() {
        return dataBits;
    }

    public int getStopBits() {
        return stopBits;
    }

    public Parity getParity() {
        return parity;
    }

    public String getPortSpec() {
        return device + " " + baudRate + " " + dataBits + parity.name().substring(0, 1) + stopBits;
    }

    private boolean isPortBusy() {
        return (portState == SerialState.PARAM_SAVING) || (portState == SerialState.PARAM_LOADING);
    }

    private void logToConsole(byte[] buffer) {
        if (mainConsole != null) SapogUtils.printDirect(mainConsole, new String(buffer));
    }

    private synchronized void tryWriteBytes(byte[] buffer) throws IOException {
        try {
            // TODO удалить логи
//            System.out.println("Writing bytes to device:");
//            printBytes(buffer);
            boolean success = port.writeBytes(buffer);
            if (success) {
                //logToConsole(buffer);
                portState = SerialState.IDLE;
            } else {
                portState = SerialState.NOT_OPEN;
                readThreadShouldExit = true;
                port.closePort();
                throw new IOException(String.format("Failed to write to %s, closing", getPortSpec()));
            }
        } catch (SerialPortException e) {
            throw new IOException(e.toString());
        }
    }

    public synchronized void sendString(String str) throws IOException {
        sendBytes(str.concat("\r\n").getBytes());
    }

    public synchronized void sendBytes(byte[] bytes) throws IOException {
        if (isPortBusy()) throw new IOException("Port busy with another operation");
        tryWriteBytes(bytes);
    }

    public void saveParam(String fieldName, Object value) throws IOException {
        if (isPortBusy()) throw new IOException("Port busy with another operation");
        portState = SerialState.PARAM_SAVING;
        byte[] buffer;
        if (value instanceof Float) {
            buffer = String.format(Locale.US, "cfg set %s %.2f\r\n", fieldName, value).getBytes();
        } else if (value instanceof Boolean) {
            buffer = String.format("cfg set %s %s\r\n", fieldName, (Boolean) value ? "true" : "false").getBytes();
        } else if (value instanceof Integer) {
            buffer = String.format("cfg set %s %d\r\n", fieldName, value).getBytes();
        } else {
            portState = SerialState.IDLE;
            return; //TODO throw something
        }

        tryWriteBytes(buffer);

        portState = SerialState.PARAM_SAVED;
    }

    private String readLineBlocking() throws IOException {
        StringBuilder message = new StringBuilder();
        try {
            while (true) {
                byte buffer[];
                if ((buffer = port.readBytes()) != null) {
                    for (byte b : buffer) {
                        if (b == '>') {
                            message.setLength(0);
                        } else if ((b == '\r') || (b == '\n')) {
                            return message.toString();
                        } else {
                            message.append((char) b);
                        }
                    }
                }
            }
        } catch (SerialPortException e) {
            throw new IOException(e.toString());
        }
    }

    public Object loadParam(String fieldName) throws IOException {
        if (isPortBusy()) throw new IOException("Port busy with another operation");
        portState = SerialState.PARAM_LOADING;
        byte[] buffer = String.format("cfg get %s\r\n", fieldName).getBytes();
        tryWriteBytes(buffer);
        portState = SerialState.PARAM_LOADED;

        return null;
    }

    public boolean isOpened() {
        return (port == null) || port.isOpened();
    }

    public void close() throws IOException {
        if (port == null) {
            //just do nothing, there is no port
        } else {
            try {
                System.out.println("мы внутри!");
                readThreadShouldExit = true;
                if (readerThread != null) readerThread.interrupt();
                if (stat2Thread != null) stat2Thread.interrupt();
                if (port.isOpened()) {
                    port.closePort();
                }
                portState = SerialState.NOT_OPEN;
            } catch (SerialPortException e) {
                portState = SerialState.EXCEPTION;
                throw new IOException(e.toString());
            }
        }
    }

    public byte[] readData() throws SerialPortException {
        System.out.println("Start reading data from port...");
        byte[] bytes = Objects.requireNonNull(port).readBytes();
//        printBytes(bytes);
        return bytes;
    }

    public byte[] readData(int byteCount, int timeout) throws SerialPortException, SerialPortTimeoutException {
        byte[] bytes = Objects.requireNonNull(port).readBytes(byteCount, timeout);
//        System.out.println("Bytes from device");
//        printBytes(bytes);
        return bytes;
    }
}
