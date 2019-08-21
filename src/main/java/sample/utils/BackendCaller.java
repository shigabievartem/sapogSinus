package sample.utils;

import javafx.scene.control.TextArea;
import jssc.SerialNativeInterface;
import jssc.SerialPortList;
import org.jetbrains.annotations.NotNull;
import sample.objects.ConnectionInfo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
        Objects.requireNonNull(port, "Empty port!");
        if ((serial == null) || !serial.isOpened()) {
            serial = new SerialDevice("default_port", port);
            serial.setConsole(mainConsole);
        } else if (serial.isOpened()) {
            //TODO can not open port when it's already open, this is an error?
        }
    }

    public synchronized void disconnect() throws IOException {
        if (serial == null) {
            //TODO throw exception?
        } else {
            serial.close();
            serial = null;
        }
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

    public void closeMainWindow() {
        if (serial != null) {
            try {
                serial.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Main window closed, back know about it!");
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
}