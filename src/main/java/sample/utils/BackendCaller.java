package sample.utils;

import javafx.scene.control.TextArea;
import jssc.SerialPortException;
import org.jetbrains.annotations.NotNull;
import sample.objects.ConnectionInfo;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
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
            serial.loadParam(fieldName);
        } catch (IOException | SerialPortException e) {

        }
        return null;
    }

    public synchronized void setValue(String fieldName, Object value) {
        System.out.println(format("Saving new value '%s' for field '%s'", value, fieldName));
        try {
            serial.saveParam(fieldName, value);
        } catch (IOException | SerialPortException e) {
            //TODO check connection and change state
        }
    }

    public synchronized ConnectionInfo checkConnection() {
        return (serial == null) ? NO_CONNECTION : serial.getConnectionInfo();
    }

    public synchronized void connect(@NotNull String port) throws IOException {
        Objects.requireNonNull(port, "Empty port!");
        if ((serial == null) || !serial.isOpened()) {
            try {
                serial = new SerialDevice("default_port", port);
                serial.setConsole(mainConsole);
            } catch (SerialPortException e) {
                throw new IOException(e);
            }
        } else if (serial.isOpened()) {
            //TODO can not open port when it's already open, this is an error?
        }
    }

    //TODO remove port as parameter
    public synchronized void disconnect(String port) {
        if (serial == null) {
            //TODO throw exception
        } else try {
            serial.close();
            serial = null;
            System.out.println("Port closed");
        } catch (IOException e) {
            //TODO re-throw up
        }
    }

    //TODO throw IOException
    public Map<String, Object> getCurrentValues() {
        if (serial != null) {
            try {
                sendCommand("cfg list");
                TimeUnit.MILLISECONDS.sleep(2000);
                return serial.getCurrentParamMap();
            } catch (InterruptedException e) {
                return null;
                //TODO return smth meaningful
            }
        }
        return null;
    }


    /***
     * Метод отправки команды
     * @param text - команда отправленная из консоли
     * @return - ответ, который будет распечатан в консоли. Если строка пустая или null в консоль ничего не попадёт
     */
    //TODO throw IOException
    public String sendCommand(String text) {
        try {
            serial.sendString(text);
        } catch (SerialPortException | IOException e) {
            //TODO throw new IOException(e.toString());
        }
        return "";
    }

    public void closeMainWindow() {
        //TODO доработать необходимую логику
        if (serial != null) {
            try {
                serial.close();
                //TimeUnit.MILLISECONDS.sleep(1000);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Main window closed, back know about it!");

    }
}