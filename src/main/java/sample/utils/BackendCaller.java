package sample.utils;

import javafx.scene.control.TextArea;
import jssc.SerialPortException;
import org.jetbrains.annotations.NotNull;
import sample.objects.ConnectionInfo;
import sample.utils.callbacks.OnParamLoadListener;
import sample.utils.callbacks.OnParamSaveListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static sample.utils.SapogConst.NO_CONNECTION;

public class BackendCaller {

    private final TextArea mainConsole;

    private BackendCaller(TextArea mainConsole) {
        this.mainConsole = mainConsole;
    }

    /* All things serial */
    private class PSaver implements OnParamSaveListener {
        @Override
        public void OnParamSave() {
            //TODO Implement properly
            System.out.println(format("Parameter saved"));
        }
    }

    private class PLoader implements OnParamLoadListener {
        @Override
        public void OnParamLoad() {
            //TODO Implement properly
            System.out.println(format("Parameter loaded"));
        }
    }

    private SerialDevice serial = null;
    /* EO All things serial */

    private static volatile BackendCaller instance;

    public static BackendCaller getInstance(TextArea mainConsole) {
        BackendCaller localInstance = instance;
        if (localInstance == null) {
            synchronized (BackendCaller.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new BackendCaller(mainConsole);
                }
            }
        }
        return localInstance;
    }

    public static BackendCaller getInstance() {
        return Objects.requireNonNull(instance, "Console not initialized. Call getInstance with console param.");
    }

//    public synchronized void tellBackSetNewValue() {
//        double i = 0;
//        while (i++ < 100000d) {
//            System.out.print("hey ho");
//        }
////        throw new RuntimeException("asdasd");
//    }

    public synchronized Object getCurrentValue(String fieldName) {
        System.out.println(format("Loading value for field '%s'", fieldName));
        try {
            serial.loadParams(fieldName);
        } catch (IOException e) {

        }
        return null;
    }

    public synchronized void setValue(String fieldName, Object value) {
        System.out.println(format("Saving new value '%s' for field '%s'", value, fieldName));
        try {
            serial.saveParam(fieldName, value);
        } catch (SerialPortException e) {
            //TODO check connection and change state
        }
    }

    public synchronized ConnectionInfo checkConnection(String portNum) {
        //TODO rewrite
        if (portNum == null || !connectedPorts.contains(portNum))
            return NO_CONNECTION; //---

        boolean isConnected = (serial != null) && (serial.getPortState() != SerialState.NOT_OPEN);

        return new ConnectionInfo(isConnected, 12.3, 15.1, 123.3, 321);
    }

    private volatile Set<String> connectedPorts = ConcurrentHashMap.newKeySet();

    public synchronized void connect(@NotNull String port) throws SerialPortException {
        Objects.requireNonNull(port, "Empty port!");
        if (serial == null) {
            serial = new SerialDevice("default_port", port);
        } else if (serial.isOpened()) {
            //TODO can not open port when it's already open, this is an error?
        }

        //TODO remove this?
        connectedPorts.add(port);
    }

    //TODO remove port as parameter
    public synchronized void disconnect(String port) {
//        Objects.requireNonNull(port, "Empty port!");
//        connectedPorts.remove(port);
        if (serial == null) {
            //TODO throw exception
        } else try {
            serial.close();
            serial = null;
            System.out.println(format("Port closed"));
        } catch (IOException e) {
            //TODO re-throw up
        }
    }

    public Map<String, Object> getCurrentValues() {
        Map<String, Object> currentParamMap = new HashMap<>();
        currentParamMap.put("esc_base", 256);
        currentParamMap.put("esc_index", 0);
        currentParamMap.put("pwm_enable", null);
        currentParamMap.put("mot_num_poles", 14);
        currentParamMap.put("mot_dc_slope", 5f);
        currentParamMap.put("mot_dc_accel", 0.09f);
        currentParamMap.put("mot_pwm_hz", 20000);
        currentParamMap.put("ctl_dir", false);
        currentParamMap.put("temp_lim", 100);
        currentParamMap.put("mot_i_max", 20);
        currentParamMap.put("sens_i_scale", 1);
        currentParamMap.put("dc_slider", 0.5d);
        currentParamMap.put("set_rpm", 99);
        return currentParamMap;
    }

    public String sendCommand(String text) {
        System.out.println(format("back receive command '%s'", text));
        return format("command '%s' successfully processed", text);
    }
}