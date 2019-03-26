package sample.utils;

import javafx.scene.control.TextArea;
import org.jetbrains.annotations.NotNull;
import sample.objects.ConnectionInfo;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static sample.utils.SapogConst.NO_CONNECTION;

public class BackendCaller {


    private TextArea mainConsole;

    public void setMainConsole(@NotNull TextArea mainConsole) {
        this.mainConsole = mainConsole;
    }

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

    public synchronized void tellBackSetNewValue() {
        double i = 0;
        while (i++ < 100000d) {
            System.out.print("hey ho");
        }
//        throw new RuntimeException("asdasd");
    }

    public synchronized Object getCurrentValue(String fieldName) {
        return "";
    }

    public synchronized void setValue(String fieldName, Object value) throws IOException {
        System.out.println(format("Received new value '%s' for field '%s'", value, fieldName));
    }

    public synchronized ConnectionInfo checkConnection() {
        if (connectedPorts.isEmpty()) return NO_CONNECTION; //---

        return new ConnectionInfo(true, 12.3, 15.1, 123.3, 321, "v. 15.1");
    }

    private volatile Set<String> connectedPorts = ConcurrentHashMap.newKeySet();

    public synchronized void connect(String port) throws IOException {
        Objects.requireNonNull(port, "Empty port!");
        connectedPorts.add(port);
    }

    public synchronized void disconnect() throws IOException {
        connectedPorts.clear();
    }

    public Map<String, Object> getCurrentValues() throws IOException {
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
        currentParamMap.put("set_rpm", 900);
        return currentParamMap;
    }

    public String sendCommand(String text) throws IOException {
        System.out.println(format("back receive command '%s'", text));
        return format("command '%s' successfully processed", text);
    }

    public void closeMainWindow() {
        System.out.println("Main window closed, back know about it!");
    }

    public String[] getPortNames() {
//        throw new RuntimeException("asdfdasf");
        return Arrays.asList("port1", "port2", "port3").toArray(new String[0]);
    }
}
