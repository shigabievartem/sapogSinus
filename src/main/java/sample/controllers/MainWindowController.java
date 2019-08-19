package sample.controllers;

import com.sun.istack.internal.NotNull;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;
import sample.objects.ButtonImpl;
import sample.objects.ConnectionInfo;
import sample.objects.filters.DecimalFilter;
import sample.objects.filters.IntegerFilter;
import sample.objects.tasks.LoadAllFromBoardTask;
import sample.objects.tasks.SetAllToBoardTask;
import sample.objects.tasks.UpdateConnectionStatusScheduler;
import sample.utils.BackendCaller;
import sample.utils.SapogUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javafx.scene.control.Alert.AlertType.CONFIRMATION;
import static javafx.scene.control.Alert.AlertType.ERROR;
import static sample.objects.ByteCommands.*;
import static sample.utils.SapogConst.*;
import static sample.utils.SapogConst.Events.connectionLost;
import static sample.utils.SapogConst.WindowConfigLocations.defaultConfig;
import static sample.utils.SapogConst.WindowConfigLocations.progressWindowConfigLocation;
import static sample.utils.SapogUtils.*;

public class MainWindowController {

    @FXML
    private Button esc_base_button;
    @FXML
    private TextField esc_base_value;
    @FXML
    private ProgressIndicator esc_base_indicator;

    @FXML
    private Button esc_index_button;
    @FXML
    private TextField esc_index_value;
    @FXML
    private ProgressIndicator esc_index_indicator;

    @FXML
    private Button pwm_enable_button;
    @FXML
    private CheckBox pwm_enable_value;
    @FXML
    private ProgressIndicator pwm_enable_indicator;

    @FXML
    private Button mot_num_poles_button;
    @FXML
    private TextField mot_num_poles_value;
    @FXML
    private ProgressIndicator mot_num_poles_indicator;

    @FXML
    private Button mot_dc_slope_button;
    @FXML
    private TextField mot_dc_slope_value;
    @FXML
    private ProgressIndicator mot_dc_slope_indicator;

    @FXML
    private Button mot_dc_accel_button;
    @FXML
    private TextField mot_dc_accel_value;
    @FXML
    private ProgressIndicator mot_dc_accel_indicator;

    @FXML
    private Button mot_pwm_hz_button;
    @FXML
    private TextField mot_pwm_hz_value;
    @FXML
    private ProgressIndicator mot_pwm_hz_indicator;

    @FXML
    private Button ctl_dir_button;
    @FXML
    private CheckBox ctl_dir_value;
    @FXML
    private ProgressIndicator ctl_dir_indicator;

    @FXML
    private Button temp_lim_button;
    @FXML
    private TextField temp_lim_value;
    @FXML
    private ProgressIndicator temp_lim_indicator;

    @FXML
    private Button mot_i_max_button;
    @FXML
    private TextField mot_i_max_value;
    @FXML
    private ProgressIndicator mot_i_max_indicator;

    @FXML
    private Button sens_i_scale_button;
    @FXML
    private TextField sens_i_scale_value;
    @FXML
    private ProgressIndicator sens_i_scale_indicator;

    @FXML
    private Button set_rpm_button;
    @FXML
    private TextField set_rpm_value;
    @FXML
    private ProgressIndicator set_rpm_indicator;

    @FXML
    private TextArea setup_console;
    @FXML
    private TextField setup_console_text_field;
    //История отправленных комманд в коммандной строке
    private List<String> history = Collections.synchronizedList(new LinkedList<>());
    //"Карретка" истории отправленных комманд
    private int historyPointer;

    @FXML
    private Slider dc_slider;
    @FXML
    private Button dc_arm_button;
    @FXML
    private Button rpm_arm_button;

    @FXML
    private VBox mainElement;

    @FXML
    private Label version_info;
    @FXML
    private Label vol_info;
    @FXML
    private Label amp_info;
    @FXML
    private Label dc_info;
    @FXML
    private Label rpm_info;
    @FXML
    private Circle connection_indicator;


    @FXML
    private ComboBox<String> port_button;

    @FXML
    private Button connect_button;
    @FXML
    private Button disconnect_button;
    @FXML
    private Button reboot_button;
    @FXML
    private Button boot_button;
    @FXML
    private Button beep_button;

    @FXML
    private Button sett_all_to_board;
    @FXML
    private Button load_all_from_board;
    @FXML
    private Button save_config_to_file;
    @FXML
    private Button load_config_from_file;
    @FXML
    private Button load_default_config;

    /**
     * Мапа с кнопками интерфейса, задающими значения параметров
     */
    private Map<String, ButtonImpl> buttons;

    /**
     * Бин в котором обращаемся к бэкэнду
     */
    private final BackendCaller backendCaller = BackendCaller.getInstance();

    /**
     * Стандартное время на выполнение backEnd операции
     */
    private final long defaultTimeOut = 15;

    /**
     * Частота считывания значения слайдера
     */
    private final long sliderFrequency = 200;
    private Double lastSliderValue = 0.0;

    /**
     * Частота проверки соединения
     */
    private final long checkConnectionFrequency = 2 * 1000;

    /**
     * Время ожидания, после завершения выполнения задания с установкой или загрузкой параметров, после которого окно закроется
     */
    private long waitModalWindowBeforeCloseTime = 2 * 1000;

    @FXML
    void initialize() {
        initializeButtons();

        applyFieldFilters();

        recalculateButtonsAvailability(false);

        startReadDCValues();

        // Обработаем нажатие кнопок в текстовомм поле консоли
        setup_console_text_field.addEventHandler(KeyEvent.KEY_RELEASED, consoleTextFieldEventHandler);

        // Обработаем событие потери соединения
        mainElement.sceneProperty().addListener(lostConnectionListener);

        // Обработаем событие закрытия основного окна
        mainElement.sceneProperty().addListener(closeWindowListener);

        backendCaller.setMainConsole(setup_console);
    }

    /**
     * Метод пытается подключиться к контроллеру
     */
    @FXML
    public void connect(ActionEvent event) {
        CompletableFuture.runAsync(connectAction).handle(connectionHandler);
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void disconnect(ActionEvent event) {
        CompletableFuture.runAsync(disconnectAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Перезагружает контроллер
     */
    @FXML
    public void reboot(ActionEvent event) {
        CompletableFuture.runAsync(rebootAction).exceptionally(defaultExceptionHandler);
    }

    private final Runnable updateConnectionStatusAction = this::updateConnectionStatus;

    private final Consumer<IOException> IOExceptionHandler = e -> {
        e.printStackTrace();
        updateConnectionStatusAction.run();
    };

    private final static int deviceAnswerTimeout = 30000;

    // TODO переделать с использованием спринга
    private final Consumer<byte[]> sendBytesAction = bytes -> {
        String serverAnswer;
        try {
            serverAnswer = CompletableFuture.supplyAsync(() ->
            {
                try {
                    return backendCaller.sendCommand(bytes);
                } catch (IOException e) {
                    IOExceptionHandler.accept(e);
                    return null;
                }
            }).exceptionally((e) -> {
                e.printStackTrace();
                return null;
            }).get(defaultTimeOut, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            serverAnswer = null;
        }
        if (!isBlankOrNull(serverAnswer)) print(serverAnswer);
    };

    /* Комманда для начала взаимодействия с платой */
    private final Supplier<Boolean> connectToDeviceCommand = () -> {
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Проверяем версию, установленную на устройстве
        sendBytesAction.accept(START_BOOT_COMMAND.getBytes());
        return isAck(backendCaller.readDataFromDevice(START_BOOT_COMMAND.getExpectedBytesCount(), deviceAnswerTimeout)[0]);
    };

    /* Считываем версию драйвера с платы */
    private final Supplier<Boolean> getDeviceVersionAction = () -> {
        // Проверяем версию, установленную на устройстве
        sendBytesAction.accept(GET_VERSION.getBytes());
        return checkBootloaderVersion(backendCaller.readDataFromDevice(GET_VERSION.getExpectedBytesCount(), deviceAnswerTimeout));
    };

    /* Считываем данные с flash памяти */
    private final Supplier<Boolean> readFlashMemory = () -> {
        Map<Integer, byte[]> flashMemoryData = new HashMap<>();
        for (int i = 0; i < FLASH_MAX_PAGE_COUNT; i++) {
            System.out.println(format("read data from page [%s]", i));
            // Проверяем версию, установленную на устройстве
            sendBytesAction.accept(READ_MEMORY.getBytes());
            if (!isAck(backendCaller.readDataFromDevice(READ_MEMORY.getExpectedBytesCount(), deviceAnswerTimeout)[0]))
                return false;

            try {
                flashMemoryData.put(i, readPageMemory(i));
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }

        System.out.println("Data successfully read from flash memory!");

        flashMemoryData.forEach((pageNum, bytesArray) -> {
            StringBuilder dataFromPage = new StringBuilder(format("page %s", pageNum));
            for (byte b : bytesArray) {
                dataFromPage.append(format(" 0x%02X", b));
            }
            System.out.println(dataFromPage);
        });

        return true;
    };

    private byte[] readPageMemory(int pageNum) {
        // Номер страницы рассчитывается из рассчета начальной страницы + шаг страницы * на номер текущей страницы
        sendBytesAction.accept(convertPageNumToBytesAndCheckSum(FLASH_MEMORY_START_PAGE_BYTE + pageNum * FLASH_MEMORY_PAGE_STEP));

        if (!isAck(backendCaller.readDataFromDevice(1, deviceAnswerTimeout)[0]))
            throw new RuntimeException(format("Can't read data from page [%s]", pageNum));

        sendBytesAction.accept(
                new byte[]{
                        (byte) DEFAULT_BYTE_COUNT_TO_READ,
                        xorBytes(new byte[]{(byte) DEFAULT_BYTE_COUNT_TO_READ, (byte) 0xFF})
                }
        );
        if (!isAck(backendCaller.readDataFromDevice(1, deviceAnswerTimeout)[0]))
            throw new RuntimeException(format("Wrong bytes count [%s]", DEFAULT_BYTE_COUNT_TO_READ));

        return backendCaller.readDataFromDevice(DEFAULT_BYTE_COUNT_TO_READ + 1, deviceAnswerTimeout);
    }

    /* Глобальная очистка памяти flash памяти(удаление драйвера) */
    private final Supplier<Boolean> eraseDeviceAction = () -> {
        sendBytesAction.accept(ERASE_MEMORY.getBytes());
        if (!isAck(backendCaller.readDataFromDevice(ERASE_MEMORY.getExpectedBytesCount(), deviceAnswerTimeout)[0]))
            return false;
        // Удалим все области памяти, а не конкретные страницы
        sendBytesAction.accept(ERASE_MEMORY_GLOBAL_ERASE.getBytes());
        return isAck(backendCaller.readDataFromDevice(ERASE_MEMORY_GLOBAL_ERASE.getExpectedBytesCount(), deviceAnswerTimeout)[0]);
    };

    /**
     * Запись драйвера на контроллер
     */
    private final Function<File, Boolean> writeDataToDeviceFunction = (file) -> {
        try {
            byte[] fileDataBytes = parseFileToBytesArray(file);
            if (fileDataBytes.length > FLASH_SIZE)
                throw new RuntimeException(format("File size [%s] > flash memory size [%s]", fileDataBytes.length, FLASH_SIZE));

            // Дополним байты из файла пустыми значениями, чтобы дальше просто записать их в память
            byte[] byteArrayToWriteInFlash = new byte[FLASH_SIZE];
            System.arraycopy(fileDataBytes, 0, byteArrayToWriteInFlash, 0, fileDataBytes.length);

            for (int pageNum = 0; pageNum < FLASH_MAX_PAGE_COUNT; pageNum++) {
                System.out.println(format("Write data to page[%s]", pageNum));
                for (int time = 0; time < WRITE_FLASH_TIMES_TO_REPEAT; time++) {

                    // Проверим, а не закончили ли мы запись драйвера на контроллер
                    if (fileDataBytes.length < calculateStartBytePosition(pageNum, time))
                        return true;

                    sendBytesAction.accept(WRITE_MEMORY.getBytes());
                    if (!isAck(backendCaller.readDataFromDevice(WRITE_MEMORY.getExpectedBytesCount(), deviceAnswerTimeout)[0]))
                        return false;

                    writePageMemory(pageNum, time, byteArrayToWriteInFlash);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    };

    /**
     * Проверяем корректность подготовительных к записи операций, если что-то пошло не так, то включаем консольный режим
     */
    private final Consumer<Boolean> checkPrepareToWriteOperations = (isSuccess) -> {
        if (isSuccess) {
            return;
        }

        activateConsole();
    };

    private final UpdateConnectionStatusScheduler updateConnectionStatusScheduler = new UpdateConnectionStatusScheduler();

    private final Consumer<ConnectionInfo> updateConnectionInfo = info -> {
        updateStatusBar(info);
        recalculateButtonsAvailability(info.isConnected());
        if (!info.isConnected()) updateConnectionStatusScheduler.stop();
    };

    private final Runnable disconnectAction = () -> {
        updateConnectionInfo.accept(NO_CONNECTION);
        try {
            backendCaller.disconnect();
        } catch (IOException e) {
            IOExceptionHandler.accept(e);
        }
        System.out.println(format("Controller manually disconnected from port '%s'", getPort()));
    };

    /**
     * Проверяем корректность операции записи на устройство, если что-то пошло не так, то включаем консольный режим
     */
    private final Consumer<Boolean> checkWriteToControllerOperations = (isSuccess) -> {
        if (isSuccess) {
            SapogUtils.alert(
                    mainElement.getScene().getWindow(),
                    CONFIRMATION,
                    "Installation driver status",
                    null,
                    format("Driver successfully installed. Reboot your device! %s Continue working with the console", System.lineSeparator()),
                    (isOk) -> {
                        if (isOk) {
                            activateConsole();
                        } else {
                            disconnectAction.run();
                        }
                    }
            );
            return;
        }

        activateConsole();
    };

    private void activateConsole() {
        setup_console_text_field.setDisable(false);
        backendCaller.activateConsole();
    }

    private final Consumer<File> writeDataToDevice = (file) -> {
        CompletableFuture.supplyAsync(() -> writeDataToDeviceFunction.apply(file))
                .thenAccept(checkWriteToControllerOperations);
    };

    private void writePageMemory(int pageNum, int time, byte[] bytesToWrite) {
        // TODO надо объединить методы записи и чтения, ибо они очень похожи
        sendBytesAction.accept(convertPageNumToBytesAndCheckSum(FLASH_MEMORY_START_PAGE_BYTE + pageNum * FLASH_MEMORY_PAGE_STEP + time * DEFAULT_BYTE_COUNT_TO_WRITE));

        if (!isAck(backendCaller.readDataFromDevice(1, deviceAnswerTimeout)[0]))
            throw new RuntimeException(format("Can't write data to page [%s]", pageNum));

        sendBytesAction.accept(prepareWriteByteArray(pageNum, time, bytesToWrite));

        if (!isAck(backendCaller.readDataFromDevice(1, deviceAnswerTimeout)[0]))
            throw new RuntimeException(format("Can't write data to page [%s]. Bad checksum.", pageNum));
    }

    /**
     * Записываем все байты которые у нас есть, остальные просто заполняем 0xFF
     */
    private byte[] prepareWriteByteArray(int pageNum, int time, byte[] bytesToWrite) {
        // +2 тк: 1 место под передаваемое кол-во бай, 2 место под чек-сумму
        byte[] resultByteArray = new byte[DEFAULT_BYTE_COUNT_TO_WRITE + 2];
        // Кол-во байтов для контроллера (макс 255)
        resultByteArray[0] = (byte) DEFAULT_BYTE_COUNT_TO_WRITE - 1;
        //
        // ТК мы ранее дозаполнили массив пустыми байтами, то у байт в массиве хватит, чтобы заполнить всю flash память
        System.arraycopy(bytesToWrite, calculateStartBytePosition(pageNum, time), resultByteArray, 1, DEFAULT_BYTE_COUNT_TO_WRITE);

        byte checkSum = xorBytes(resultByteArray);
        resultByteArray[resultByteArray.length - 1] = checkSum;
        return resultByteArray;
    }

    /**
     * Рассчёт стартовой позиции байта в массиве с которой необходимо продолжить запись
     */
    private int calculateStartBytePosition(int pageNum, int time) {
        return pageNum * WRITE_FLASH_TIMES_TO_REPEAT * DEFAULT_BYTE_COUNT_TO_WRITE + time * DEFAULT_BYTE_COUNT_TO_WRITE;
    }

    private <T> BiFunction<? super T, Throwable, Boolean> prepareBiFunction(Supplier<Boolean> command) {
        return (value, exc) -> {
            // Если предыдущая операция прошла не успешно, последующие операции делать не надо
            if (exc != null) {
                exc.printStackTrace();
                return false;
            }
            if (value instanceof Boolean && !(Boolean) value) return false;
            try {
                return CompletableFuture.supplyAsync(command).get(100, SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        };
    }

    private <T> BiFunction<? super T, Throwable, Boolean> prepareBiFunction(Runnable command) {
        return prepareBiFunction(() -> {
            command.run();
            return true;
        });
    }

    /**
     * Вводит в режим boot loader'a
     */
    @FXML
    public void boot(ActionEvent event) {
        CompletableFuture.runAsync(bootAction)
                .exceptionally(defaultExceptionHandler)
                .thenRun(disconnectAction)
                .thenRun(connectInBootloaderMode)
                .handle(prepareBiFunction(connectToDeviceCommand))
                .handle(prepareBiFunction(getDeviceVersionAction))
                .handle(prepareBiFunction(readFlashMemory))
                .handle(prepareBiFunction(eraseDeviceAction))
                // Продолжение цикла операций продолжается в действии загрузки драйвера на контроллер
                .handle(prepareBiFunction(() -> bootDriverAction.accept(event)))
                .thenAccept(checkPrepareToWriteOperations);
    }

    /**
     * Отправляет команду beep
     */
    @FXML
    public void beep(ActionEvent event) {
        CompletableFuture.runAsync(beepAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Передает параметр dc arm
     */
    @FXML
    public void dcArmAction(ActionEvent event) {
        CompletableFuture.runAsync(dcArmAction).exceptionally(defaultExceptionHandler)
                .thenRun(() -> setSliderValueImpl(dc_slider, true));
    }

    /**
     * Передает параметр rpm arm
     */
    @FXML
    public void rpmArmAction(ActionEvent event) {
        CompletableFuture.runAsync(rpmArmAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод загружает текущую конфигурацию с платы и устанавливает значения на форму
     */
    @FXML
    public void loadAllFromBoard(ActionEvent event) {
        loadAllFromBoardImpl(((Button) event.getTarget()).getScene().getWindow());
    }

    /**
     * Метод собирает параметры с формы и устанавливает их на плату
     */
    @FXML
    public void setAllToBoard(ActionEvent event) {
        CompletableFuture.runAsync(() -> setAllToBoardAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод сохраняет конфиг в файл
     */
    @FXML
    public void saveConfigToFile(ActionEvent event) {
        CompletableFuture.runAsync(() -> saveConfigToFileAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод загружает конфиг из файла
     */
    @FXML
    public void loadConfigFromFile(ActionEvent event) {
        CompletableFuture.runAsync(() -> loadConfigFromFileAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод загружает дефолтную конфигурацию
     */
    @FXML
    public void loadDefaultConfig(ActionEvent event) {
        CompletableFuture.runAsync(loadDefaultConfigAction).exceptionally(defaultExceptionHandler);
    }


    private Consumer<ButtonImpl> setButtonAction = (buttonImpl) -> {
        Object currentValue = buttonImpl.getValue();
        String variableName = buttonImpl.getFieldName();

        System.out.println(format("%s setting value: %s", variableName, currentValue));
        try {
            backendCaller.setValue(variableName, currentValue);
        } catch (IOException e) {
            IOExceptionHandler.accept(e);
        }
    };

    private Consumer<ButtonImpl> setRpmButtonAction = (buttonImpl) -> {
        try {
            backendCaller.sendCommand(format("rpm %d", (Integer) buttonImpl.getValue()));
        } catch (IOException e) {
            IOExceptionHandler.accept(e);
        }
    };

    private final Consumer<ActionEvent> setValueAction = (event -> {
        Objects.requireNonNull(event, "Empty event!");
        if (event.getTarget() == null || event.getTarget().getClass() != Button.class) {
            System.out.println("Bad button");
            return; //---
        }

        Button target = (Button) Objects.requireNonNull(event.getTarget(), "Target element cannot be empty!");

        boolean isSetRpmButton = target == set_rpm_button;
        if (isSetRpmButton && isBlankOrNull((rpm_info.getText()))) return; //---

        setButtonValueImpl(buttons.get(target.getId()), isSetRpmButton ? setRpmButtonAction : setButtonAction);
    });

    /**
     * Обработчик нажатия на кнопку "Установить значение"
     */
    @FXML
    public void setValue(ActionEvent event) {
        CompletableFuture.runAsync(() -> setValueAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    private final EventHandler<Event> connectionLostEventHandler = (conLostEvent) -> {
        try {
            SapogUtils.alert(mainElement.getScene().getWindow(), Alert.AlertType.WARNING,
                    "Connection lost...", null, "Do you want to reconnect?",
                    (isOk) -> {
                        if (isOk) connect(null);
                    },
                    new ButtonType("Reconnect", ButtonType.OK.getButtonData()),
                    ButtonType.CANCEL
            );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    };

    private final ChangeListener<? super Scene> lostConnectionListener = (observable, oldScene, newScene) -> {
        if (oldScene == null && newScene != null) {
            newScene.windowProperty().addListener((observableWindow, oldWindow, newWindow) -> {
                if (oldWindow == null && newWindow != null) {
                    newWindow.setOnShowing((e) -> mainElement.addEventHandler(connectionLost, connectionLostEventHandler));
                }
            });
        }
    };

    private final Timer dcValueReader = new Timer();

    private final ChangeListener<? super Scene> closeWindowListener = (observable, oldScene, newScene) -> {
        if (oldScene == null && newScene != null) {
            newScene.windowProperty().addListener((observableWindow, oldWindow, newWindow) -> {
                if (oldWindow == null && newWindow != null) {
                    newWindow.setOnCloseRequest((windowEvent) -> {
                        backendCaller.closeMainWindow();
                        updateConnectionStatusScheduler.stopScheduler();
                        dcValueReader.cancel();
                        ((Stage) windowEvent.getTarget()).close();
                    });
                }
            });
        }
    };

    private final Supplier<ConnectionInfo> checkConnectionAction = backendCaller::checkConnection;

    private final Runnable updateConnectionStatusTask = () -> {
        ConnectionInfo info;
        try {
            info = CompletableFuture.supplyAsync(checkConnectionAction).get(defaultTimeOut, SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            info = NO_CONNECTION;
        }

        if (!info.isConnected()) {
            mainElement.fireEvent(new Event(connectionLost));
        }

        updateConnectionInfo.accept(info);
    };

    private final Runnable connectAction = () -> {
        String currentPort = getPort();
//         При необходимости можно добавить введёный порт при успешном коннекте
//        if (!port_button.getItems().contains(currentPort)) port_button.getItems().add(currentPort);
        try {
            backendCaller.connect(currentPort);
            System.out.println(format("Successfully connected to port: '%s'", getPort()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    };

    private final Runnable connectInBootloaderMode = () -> {
        String currentPort = getPort();
//         При необходимости можно добавить введёный порт при успешном коннекте
//        if (!port_button.getItems().contains(currentPort)) port_button.getItems().add(currentPort);
        try {
            backendCaller.connectInBootloaderMode(currentPort);
            System.out.println(format("Successfully connected to port: '%s' in bootloader mode", getPort()));

            port_button.setDisable(true);
            connect_button.setDisable(true);
            save_config_to_file.setDisable(true);
            load_config_from_file.setDisable(true);
            load_default_config.setDisable(true);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    };

    private final Consumer<String> sendCommandAction = text -> {
        String serverAnswer;
        try {
            serverAnswer = CompletableFuture.supplyAsync(() ->
            {
                try {
                    return backendCaller.sendCommand(text);
                } catch (IOException e) {
                    IOExceptionHandler.accept(e);
                    return null;
                }
            }).exceptionally((e) -> {
                e.printStackTrace();
                return null;
            }).get(defaultTimeOut, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            serverAnswer = null;
        }
        if (!isBlankOrNull(serverAnswer)) print(serverAnswer);
    };

    /**
     * Пытается распарсить 16-ричное представление бита из полученной строки, чтобы отправить несколько байтов,
     * просто перечислите их через запятую
     */
    private final Consumer<String> sendBytesFromConsoleAction = text -> {
        try {
            String[] codeArray = text.split(" ");
            byte[] byteArray = new byte[codeArray.length];
            for (int i = 0; i < codeArray.length; i++) {
                byteArray[i] = (byte) Integer.decode(codeArray[i]).intValue();
            }
            sendBytesAction.accept(byteArray);
        } catch (Exception ex) {
            System.out.println("Parse byte from input string error.");
            ex.printStackTrace();
        }
    };

    private final Runnable rebootAction = () -> sendCommandAction.accept("reboot");
    private final Runnable beepAction = () -> sendCommandAction.accept("beep");
    private final Runnable bootAction = () -> sendCommandAction.accept("boot");
    private final Runnable dcArmAction = () -> sendCommandAction.accept("dc arm");
    private final Runnable rpmArmAction = () -> sendCommandAction.accept("rpm arm");
    private final Function<Throwable, ? extends Void> defaultExceptionHandler = ex -> {
        ex.printStackTrace();
        return null;
    };

    /**
     * Метод проверяет версию bootloader'a, а заодно и позволяет убедиться, что мы находимся в нужном режиме
     *
     * @param dataFromDevice - ответ с контроллера
     * @return - true, если версия bootloader'a определенна, в противном случае - false
     */
    private boolean checkBootloaderVersion(byte[] dataFromDevice) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> {
                        if (dataFromDevice == null) return false;
                        System.out.println("Check received data:");
                        printBytes(dataFromDevice);
                        if (dataFromDevice.length != 5) return false;
                        if (!isAck(dataFromDevice[0])) return false;
                        String message = String.format("Bootloader version: %02X", dataFromDevice[1]);
                        System.out.println(new StringBuilder(message).insert(message.length() - 1, "."));
                        return true;
                    }
            ).get(defaultTimeOut, SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private final Consumer<ProgressWindowController> progressBarLoadAction = (c) -> {
        try {
            LoadAllFromBoardTask task = new LoadAllFromBoardTask(c, buttons);
            c.getProgress_bar().progressProperty().bind(task.progressProperty());
            CompletableFuture.runAsync(task).thenRun(() -> {
                setLabel(c.getLabel(), "All parameters loaded from board.");
                try {
                    Thread.sleep(waitModalWindowBeforeCloseTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                c.closeWindow();
            });
        } catch (IOException e) {
            IOExceptionHandler.accept(e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    private final Consumer<ProgressWindowController> progressBarSaveAction = (c) -> {
        try {
            SetAllToBoardTask task = new SetAllToBoardTask(c, buttons);
            c.getProgress_bar().progressProperty().bind(task.progressProperty());
            CompletableFuture.runAsync(task).thenRun(() -> {
                setLabel(c.getLabel(), "All parameters set to board.");
                try {
                    Thread.sleep(waitModalWindowBeforeCloseTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                c.closeWindow();
            });
        } catch (IOException e) {
            IOExceptionHandler.accept(e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    private final Consumer<File> saveConfigToFileActionConsumer = f -> savePropertiesToFile(prepareCurrentValuesConfig(), f);

    private final Consumer<Window> loadAllFromBoardAction = window -> showModalWindow("Loading parameters...", progressWindowConfigLocation,
            window, progressBarLoadAction);

    private final Consumer<ActionEvent> setAllToBoardAction = event -> showModalWindow("Setting parameters...", progressWindowConfigLocation,
            ((Button) event.getTarget()).getScene().getWindow(), progressBarSaveAction);

    private final Consumer<ActionEvent> saveConfigToFileAction = event -> showPropertiesFile(true,
            ((Button) event.getTarget()).getScene().getWindow(), saveConfigToFileActionConsumer);

    private final Consumer<File> parsePropertiesFromFile = file -> {
        Properties props = parsePropertiesFile(file);
        buttons.forEach((k, v) -> {
            if (k.equalsIgnoreCase(set_rpm_button.getId())) return; //---
            String configValue = props.getProperty(v.getFieldName());

            if (!isBlankOrNull(configValue)) v.setValue(configValue);
            else System.out.println(format("No value for field '%s'...", k));
        });
    };

    private final Consumer<ActionEvent> loadConfigFromFileAction = event -> showPropertiesFile(false,
            ((Button) event.getTarget()).getScene().getWindow(), parsePropertiesFromFile);

    private final Consumer<ActionEvent> bootDriverAction = event -> showDriverFile(false,
            ((Button) event.getTarget()).getScene().getWindow(), writeDataToDevice);

    private final BiFunction<Void, Throwable, Void> connectionHandler = (voidValue, exception) -> {
        if (exception != null) {
            SapogUtils.alert(
                    mainElement.getScene().getWindow(),
                    ERROR,
                    "Connection error",
                    null,
                    getSimpleErrorMessage(exception),
                    null
            );
        } else {
            updateConnectionStatusAction.run();

            CompletableFuture.runAsync(() -> loadAllFromBoardAction.accept(
                    mainElement.getScene().getWindow())).exceptionally(defaultExceptionHandler);
        }
        return null;
    };

    private final Runnable loadDefaultConfigAction = () -> {
        try {
            parsePropertiesFromFile.accept(new File(defaultConfig.toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    };

    private final EventHandler<KeyEvent> consoleTextFieldEventHandler = keyEvent -> {
        switch (keyEvent.getCode()) {
            case ENTER:
                String text = setup_console_text_field.getText();
                history.add(text);
                historyPointer++;
                if (keyEvent.isAltDown()) {
                    sendBytesFromConsoleAction.accept(text);
                } else {
                    sendCommandAction.accept(text);
                }
                setup_console_text_field.clear();
                break;
            case UP:
                if (historyPointer == 0) {
                    break;
                }
                historyPointer--;
                runInMainThread(() -> {
                    setup_console_text_field.setText(history.get(historyPointer));
                    setup_console_text_field.selectAll();
                });
                break;
            case DOWN:
                if (historyPointer == history.size() - 1) {
                    break;
                }
                historyPointer++;
                runInMainThread(() -> {
                    setup_console_text_field.setText(history.get(historyPointer));
                    setup_console_text_field.selectAll();
                });
                break;
            default:
                break;
        }
    };

    private void applyFieldFilters() {
        esc_base_value.setTextFormatter(new TextFormatter(new IntegerFilter(true, 0, 2047)));
        esc_index_value.setTextFormatter(new TextFormatter(new IntegerFilter(true, 0, 31)));
        mot_num_poles_value.setTextFormatter(new TextFormatter(new IntegerFilter(true, 2, 100)));
        mot_dc_slope_value.setTextFormatter(new TextFormatter(new DecimalFilter(true, 0.1f, 20f)));
        mot_dc_accel_value.setTextFormatter(new TextFormatter(new DecimalFilter(true, 0.001f, 0.5f)));
        mot_pwm_hz_value.setTextFormatter(new TextFormatter(new IntegerFilter(true, 2000, 75000)));
        temp_lim_value.setTextFormatter(new TextFormatter(new IntegerFilter(true, 90, 150)));
        mot_i_max_value.setTextFormatter(new TextFormatter(new DecimalFilter(true, 1f, 60f)));
        sens_i_scale_value.setTextFormatter(new TextFormatter(new DecimalFilter(true, 0f, 1000000f)));
        set_rpm_value.setTextFormatter(new TextFormatter(new IntegerFilter(true, 900, 5000)));
    }

    /**
     * Соберем все кнопки с изменяемым значением в одну кололекцию
     */
    private void initializeButtons() {
        String[] portNames = new String[0];
        try {
            portNames = CompletableFuture.supplyAsync(backendCaller::getPortNames).get(defaultTimeOut, SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        port_button.getItems().addAll(portNames);
        if (!port_button.getItems().isEmpty()) port_button.getSelectionModel().selectFirst();

        buttons = new ConcurrentHashMap<>();
        buttons.put(esc_base_button.getId(), new ButtonImpl(esc_base_button, esc_base_value, esc_base_indicator));
        buttons.put(esc_index_button.getId(), new ButtonImpl(esc_index_button, esc_index_value, esc_index_indicator));
        buttons.put(pwm_enable_button.getId(), new ButtonImpl(pwm_enable_button, pwm_enable_value, pwm_enable_indicator));
        buttons.put(mot_num_poles_button.getId(), new ButtonImpl(mot_num_poles_button, mot_num_poles_value, mot_num_poles_indicator));
        buttons.put(mot_dc_slope_button.getId(), new ButtonImpl(mot_dc_slope_button, mot_dc_slope_value, mot_dc_slope_indicator));
        buttons.put(mot_dc_accel_button.getId(), new ButtonImpl(mot_dc_accel_button, mot_dc_accel_value, mot_dc_accel_indicator));
        buttons.put(mot_pwm_hz_button.getId(), new ButtonImpl(mot_pwm_hz_button, mot_pwm_hz_value, mot_pwm_hz_indicator));
        buttons.put(ctl_dir_button.getId(), new ButtonImpl(ctl_dir_button, ctl_dir_value, ctl_dir_indicator));
        buttons.put(temp_lim_button.getId(), new ButtonImpl(temp_lim_button, temp_lim_value, temp_lim_indicator));
        buttons.put(mot_i_max_button.getId(), new ButtonImpl(mot_i_max_button, mot_i_max_value, mot_i_max_indicator));
        buttons.put(sens_i_scale_button.getId(), new ButtonImpl(sens_i_scale_button, sens_i_scale_value, sens_i_scale_indicator));
        buttons.put(set_rpm_button.getId(), new ButtonImpl(set_rpm_button, set_rpm_value, set_rpm_indicator));
    }

    /**
     * Реализация передачи измененного параметра со слайдера на бэк
     */
    private void setSliderValueImpl(Slider slider, boolean isForce) {
        CompletableFuture.runAsync(() -> {
            if (isBlankOrNull((dc_info.getText()))) return; //---

            Double currentValue = roundDoubleValue(slider.getValue());
            if (lastSliderValue != null && (!isForce && lastSliderValue.equals(currentValue))) return; //---

            String variableName = slider.getId();

            try {
                System.out.println(format("%s changing value: '%s' -> '%s'", variableName, lastSliderValue, currentValue));

                slider.setDisable(true);
                backendCaller.sendCommand(format("dc %s", formatDecimalValue(currentValue)));
                lastSliderValue = currentValue;
                System.out.println("successfully set value");
            } catch (IOException e) {
                IOExceptionHandler.accept(e);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                slider.setDisable(false);
            }
        }).exceptionally(defaultExceptionHandler);
    }

    private void setButtonValueImpl(ButtonImpl buttonImpl, Consumer<ButtonImpl> setButtonValueAction) {
        try {
            buttonImpl.getIndicator().setVisible(true);
            buttonImpl.getButton().setDisable(true);
            setButtonValueAction.accept(buttonImpl);
            System.out.println("successfully set value");
        } finally {
            buttonImpl.getIndicator().setVisible(false);
            buttonImpl.getButton().setDisable(false);
        }
    }

    /**
     * Метод возвращает текущий выбранный порт
     */
    private String getPort() {
        String port = null;
        try {
            port = port_button.getValue();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return port;
    }

    private final Consumer<ConnectionInfo> updateStatusBarAction = info -> {
        version_info.setText(isBlankOrNull(info.getVersion()) ? "" : info.getVersion());
        vol_info.setText(isBlankOrNull(info.getVoltage()) ? "" : format("%s v", info.getVoltage()));
        amp_info.setText(isBlankOrNull(info.getAmperage()) ? "" : format("%s A", info.getAmperage()));
        dc_info.setText(isBlankOrNull(info.getDc()) ? "" : format("%s DC", info.getDc()));
        rpm_info.setText(isBlankOrNull(info.getRpm()) ? "" : format("%s RPM", info.getRpm()));
        connection_indicator.setFill(info.isConnected() ? Color.valueOf("2c9a24") : Color.RED);
    };

    /**
     * Метод обновляет статус бар приложения
     */
    private void updateStatusBar(@NotNull ConnectionInfo info) {
        runInMainThread(() -> runSaveAction(() -> updateStatusBarAction.accept(info)));
    }

    /**
     * Метод проверяет коннект, в случае разрыва коннекта открывается модальное окно с оповещением
     */
    private void updateConnectionStatus() {
        CompletableFuture.runAsync(() -> updateConnectionStatusScheduler.start(updateConnectionStatusTask, 0, checkConnectionFrequency));
    }

    /**
     * Метод печатает строку в консоль
     */
    private void print(String text, Object... objects) {
        SapogUtils.print(setup_console, text, objects);
    }

    /**
     * Метод распечатывает stackTrace ошибки
     */
    private Void printError(Throwable throwableElement) {
        return SapogUtils.printError(setup_console, throwableElement);
    }

    /**
     * Обёртка для выполнения безопасного действия
     */
    private void runSaveAction(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Метод блокирует элементы, которые не должны использоваться без активного соединения
     */
    private void recalculateButtonsAvailability(boolean hasConnection) {
        buttons.forEach((name, buttonImpl) -> {
            buttonImpl.getButton().setDisable(!hasConnection);
            buttonImpl.getFieldImpl().setDisable(!hasConnection);
        });

        port_button.setDisable(hasConnection);

        connect_button.setDisable(hasConnection);
        disconnect_button.setDisable(!hasConnection);
        reboot_button.setDisable(!hasConnection);
        boot_button.setDisable(!hasConnection);
        beep_button.setDisable(!hasConnection);

        sett_all_to_board.setDisable(!hasConnection);
        load_all_from_board.setDisable(!hasConnection);

        setup_console_text_field.setDisable(!hasConnection);

        dc_slider.setDisable(!hasConnection);
        dc_arm_button.setDisable(!hasConnection);
        rpm_arm_button.setDisable(!hasConnection);
    }

    private Properties prepareCurrentValuesConfig() {
        Properties props = new Properties();
        buttons.forEach((k, v) -> {
            if (k.equalsIgnoreCase(set_rpm_button.getId())) return; //---
            props.setProperty(v.getFieldName(), String.valueOf(v.getValue()));
        });
        return props;
    }

    private void startReadDCValues() {
        dcValueReader.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                setSliderValueImpl(dc_slider, false);
            }
        }, 0, sliderFrequency);
    }

    private void loadAllFromBoardImpl(Window window) {
        CompletableFuture.runAsync(() -> loadAllFromBoardAction.accept(window)).exceptionally(defaultExceptionHandler);
    }
}