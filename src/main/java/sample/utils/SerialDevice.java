package sample.utils;

import com.fasterxml.jackson.databind.node.NullNode;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.TextArea;
import sample.objects.ConnectionInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.lang.String.format;

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

    public SerialDevice(@NotNull String name, @NotNull String newDevice, @NotNull int newBaud) throws IOException, SerialPortException {
        // TODO Refactor
        // TODO throw exception
        this.name = name;
        device = newDevice;
        baudRate = newBaud;
        dataBits = 8;
        stopBits = 1;
        parity = Parity.NONE;
        reopenTimeoutMS = 1000;
        traceWriteFailures = true;

        tryReopen();
    }


    public SerialDevice(@NotNull String name, @NotNull String newDevice) throws SerialPortException {
        // TODO Refactor, inherit from main constructor
        // TODO throw exception
        this.name = name;
        device = newDevice;
        baudRate = 115200;
        dataBits = 8;
        stopBits = 1;
        parity = Parity.NONE;
        reopenTimeoutMS = 1000;
        traceWriteFailures = true;

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
        currentParamMap.put("dc_slider", 0.5f);
        currentParamMap.put("set_rpm", 99);
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

        tryReopen();
    }

    public ConnectionInfo getConnectionInfo() {
        return new ConnectionInfo(
                getPortState() != SerialState.NOT_OPEN,
                voltage,
                current,
                DC,
                RPM,
                //TODO добавить версию
                "version"
                );
    }

    Runnable serialReader = new Runnable() {
        public void run() {
            StringBuilder message = new StringBuilder();
            System.out.println("Starting serialReader thread");
            StringBuilder lastLine = new StringBuilder();
            while (!readThreadShouldExit && (portState != SerialState.NOT_OPEN)) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                byte buffer[];
                try {
                    if ((buffer = port.readBytes()) != null) {
                        for (byte b : buffer) {
                            //message.append((char) b);
                            lastLine.append((char) b);
                            if (b == '\n') {
                                if (!tryExtractStat2(lastLine.toString())) {
                                    if (!tryExtractParam(lastLine.toString())) {
                                        if (!tryExtractVersion(lastLine.toString())){
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

    private boolean tryExtractCommand(String s) {
        if (
                    (s.indexOf("ch> stat2\r\n") == 0) ||
                    (s.indexOf("ch> cfg list\r\n") == 0) ||
                    (s.indexOf("ch> cfg set ") == 0)
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
            Map.Entry pair = (Map.Entry)it.next();
            String param = (String)pair.getKey();
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
                valStr = valStr.replaceAll("\\s","");
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

    public void tryReopen() throws SerialPortException {
        port = new SerialPort(device);
        int jsscParity = 0;
        switch (parity) {
            case NONE: jsscParity = SerialPort.PARITY_NONE; break;
            case ODD: jsscParity = SerialPort.PARITY_ODD; break;
            case EVEN: jsscParity = SerialPort.PARITY_EVEN; break;
            case MARK: jsscParity = SerialPort.PARITY_MARK; break;
            case SPACE: jsscParity = SerialPort.PARITY_SPACE; break;
        }

        try {
            port.openPort();
            port.setParams(baudRate, dataBits, stopBits, jsscParity);
            portState = SerialState.IDLE;
            readThreadShouldExit = false;
            readerThread = new Thread(serialReader);
            readerThread.start();
        } catch (SerialPortException e) {
            LOG.error("Cannot open port {}, retrying in {}ms", getPortSpec(), reopenTimeoutMS, e);
            readThreadShouldExit = true;
            //reopenAt = System.currentTimeMillis() + reopenTimeoutMS;
            throw e;
        }

        LOG.info("Device {} is started successfully", getPortSpec());
        reopenAt = 0;
    }

    public SerialState getPortState(){
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

    public void sendAsync(byte[] bytes) throws SerialPortException {
        if (port == null) {
            if (reopenAt < System.currentTimeMillis()) {
                tryReopen();
            }

            // Still failed:
            if (port == null) {
                return;
            }
        }

        boolean success = false;

        try {
            success = port.writeBytes(bytes);

            if (!success) {
                LOG.error("Failed to write to {}", getPortSpec());
            }
        } catch (SerialPortException e) {
            if (traceWriteFailures) {
                LOG.error("Failed to write to {}", getPortSpec(), e);
            } else {
                LOG.error("Failed to write to {}", getPortSpec());
            }
        }

        if (!success) {
            LOG.error("Retrying in {}ms...", reopenTimeoutMS);

            try {
                port.closePort();
            } catch (SerialPortException ignored) {
            }

            port = null;
            reopenAt = System.currentTimeMillis() + reopenTimeoutMS;
        }
    }

    private boolean isPortBusy() {
        return (portState == SerialState.PARAM_SAVING) || (portState == SerialState.PARAM_LOADING);
    }

    private void logToConsole(byte[] buffer) {
        if (mainConsole != null) SapogUtils.printDirect(mainConsole, new String(buffer));
    }

    private void tryWriteBytes(byte[] buffer) throws SerialPortException, IOException {
        try {
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
        }
        catch (SerialPortException e) {
            //TODO do better?
            throw e;
        }
    }

    public void sendString(String str) throws SerialPortException, IOException {
        if (isPortBusy()) throw new IOException("Port busy with another operation");
        tryWriteBytes(str.concat("\r\n").getBytes());
    }

    public void saveParam(String fieldName, Object value) throws SerialPortException, IOException {
        if (isPortBusy()) throw new IOException("Port busy with another operation");
        portState = SerialState.PARAM_SAVING;
        byte[] buffer;
        if (value instanceof Float) {
            buffer = String.format("cfg set %s %.2f\r\n", fieldName, value).getBytes();
        } else if (value instanceof Boolean) {
            buffer = String.format("cfg set %s %s\r\n", fieldName, (Boolean)value?"true":"false").getBytes();
        } else if (value instanceof Integer) {
            buffer = String.format("cfg set %s %d\r\n", fieldName, value).getBytes();
        } else {
            portState = SerialState.IDLE;
            return; //TODO throw something
        }

        tryWriteBytes(buffer);

        portState = SerialState.PARAM_SAVED;
    }

    private String readLineBlocking() throws SerialPortException {
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
        }
        catch (SerialPortException ex) {
            System.out.println(ex);
            System.out.println("serialEvent");
        }
        return "";
    }

    public Object loadParam(String fieldName) throws SerialPortException, IOException {
        if (isPortBusy()) throw new IOException("Port busy with another operation");
        //TODO send param load string
        portState = SerialState.PARAM_LOADING;
        byte[] buffer = String.format("cfg get %s\r\n", fieldName).getBytes();
        tryWriteBytes(buffer);
        portState = SerialState.PARAM_LOADED;

        return null;
    }

    public boolean isOpened() {
        return (port == null) || port.isOpened();
    }

    public void close() throws IOException{
        if (port == null) {
            //TODO throw exception
        } else {
            try {
                readThreadShouldExit = true;
                if (readerThread != null) readerThread.interrupt();
                port.closePort();
                portState = SerialState.NOT_OPEN;
            } catch (SerialPortException e) {
                portState = SerialState.EXCEPTION;
                //TODO re-throw up
            }
        }
    }
}
