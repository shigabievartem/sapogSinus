package sample.utils;

import javafx.event.Event;
import javafx.event.EventType;
import sample.ApplicationStarter;
import sample.objects.ConnectionInfo;

import java.net.URL;

public interface SapogConst {
    String ERROR_BUTTON_STYLE = "error";
    ConnectionInfo NO_CONNECTION = new ConnectionInfo(false, null, null, null, null);

    class WindowConfigLocations {
        public static final URL mainWindowConfigLocation = ApplicationStarter.class.getResource("/fxml/mainWindow.fxml");
        public static final URL progressWindowConfigLocation = ApplicationStarter.class.getResource("/fxml/progressWindow.fxml");
        public static final URL defaultConfig = ApplicationStarter.class.getResource("/config/defaultConfig.properties");
    }

    class Events {
        /**
         * Событие о потере соединения с контроллером
         */
        public static final EventType<Event> connectionLost = new EventType<>("LostConnectionEvent");
        /**
         * Событие о потере соединения с контроллером
         */
        public static final EventType<Event> closeModalWindow = new EventType<>("CloseModalWindowEvent");
    }
}
