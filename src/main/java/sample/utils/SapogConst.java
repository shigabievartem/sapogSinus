package sample.utils;

import javafx.event.Event;
import javafx.event.EventType;
import sample.ApplicationStarter;
import sample.objects.ConnectionInfo;

import java.net.URL;

public interface SapogConst {
    String ERROR_BUTTON_STYLE = "error";
    ConnectionInfo NO_CONNECTION = new ConnectionInfo(false, null, null, null, null, null);

    /* Байт первой странички Flash памяти */
    int FLASH_MEMORY_START_PAGE_BYTE = 0x08000000;
    /* Шаг между страницами для flash памяти */
    int FLASH_MEMORY_PAGE_STEP = 0x00000800;
    /* Размер Flash памяти */
    int FLASH_SIZE = 0x40000;
    /* Размер памяти одной страницы flash памяти */
    int FLASH_PAGE_SIZE = 0x800;
    /* Максимальное количество страниц во Flash памяти */
    int FLASH_MAX_PAGE_COUNT = FLASH_SIZE/FLASH_PAGE_SIZE;
    /* Кол-во байт для считывания 0<N<256 в 10чной СИ */
    int DEFAULT_BYTE_COUNT_TO_READ = 255;
    /* Кол-во байт для записи */
    int DEFAULT_BYTE_COUNT_TO_WRITE = 256;

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
