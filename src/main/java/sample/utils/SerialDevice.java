package sample.utils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sample.utils.callbacks.OnParamLoadListener;
import sample.utils.callbacks.OnParamSaveListener;

import java.io.IOException;

public class SerialDevice {
    private static final Logger LOG = LoggerFactory.getLogger(SerialDevice.class);
    public static Config CONFIG_DEFAULTS = ConfigFactory.parseResources(SerialDevice.class, "/serial-defaults.conf");

    private String name;
    private String device;
    private int baudRate, dataBits, stopBits;
    private Parity parity;

    private SerialPort port;

    private long reopenAt = 0;
    private long reopenTimeoutMS = 0;
    private boolean traceWriteFailures = true;

    private SerialState portState = SerialState.NOT_OPEN;
    private OnParamSaveListener psaveListener = null;
    private OnParamLoadListener ploadListener = null;

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

        tryReopen();
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
        } catch (SerialPortException e) {
            LOG.error("Cannot open port {}, retrying in {}ms", getPortSpec(), reopenTimeoutMS, e);
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
        return (portState == SerialState.PARAM_SAVING) || (portState == SerialState.PARAMS_LOADING);
    }

    public void saveParam(String fieldName, Object value) throws SerialPortException {
        if (!isPortBusy()) {
            //TODO send param save string
            portState = SerialState.PARAM_SAVING;
            byte[] buffer;
            if (value instanceof Float) {
                buffer = String.format("cfg set %s %.2f\n", fieldName, (Float)value).getBytes();
            } else if (value instanceof Boolean) {
                buffer = String.format("cfg set %s %s\n", fieldName, (Boolean)value?"true":"false").getBytes();
            } else if (value instanceof Integer) {
                buffer = String.format("cfg set %s %df\n", fieldName, (Integer)value).getBytes();
            } else return; //TODO throw something
            port.writeBytes(buffer);
        } else {
            //TODO throw something
        }
    }

    public Object loadParams(String fieldName) throws IOException {
        if (!isPortBusy()) {
            //TODO send param load string
            portState = SerialState.PARAMS_LOADING;
        } else {
            //TODO throw IOException
        }
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
                port.closePort();
                portState = SerialState.NOT_OPEN;
            } catch (SerialPortException e) {
                portState = SerialState.EXCEPTION;
                //TODO re-throw up
            }
        }
    }
}
