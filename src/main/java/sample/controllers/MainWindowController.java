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
import jssc.SerialPortException;
import sample.objects.ButtonImpl;
import sample.objects.ConnectionInfo;
import sample.objects.filters.DecimalFilter;
import sample.objects.filters.IntegerFilter;
import sample.objects.tasks.LoadAllFromBoardTask;
import sample.objects.tasks.SetAllToBoardTask;
import sample.objects.tasks.UpdateConnectionStatusTimer;
import sample.utils.BackendCaller;
import sample.utils.SapogUtils;

import java.io.File;
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
import static javafx.scene.control.Alert.AlertType.ERROR;
import static sample.utils.SapogConst.Events.connectionLost;
import static sample.utils.SapogConst.NO_CONNECTION;
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

    /**
     * Мапа с кнопками интерфейса
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
    private final long sliderFrequency = 2 * 1000;
    private Double lastSliderValue = 0.0;

    /**
     * Частота проверки соединения
     */
    private final long checkConnectionFrequency = 2 * 1000;

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
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void reboot(ActionEvent event) {
        CompletableFuture.runAsync(rebootAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void boot(ActionEvent event) {
        CompletableFuture.runAsync(bootAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void beep(ActionEvent event) {
        CompletableFuture.runAsync(beepAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void dcArmAction(ActionEvent event) {
        CompletableFuture.runAsync(dcArmAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void rpmArmAction(ActionEvent event) {
        CompletableFuture.runAsync(rpmArmAction).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void loadAllFromBoard(ActionEvent event) {
        loadAllFromBoardImpl(((Button) event.getTarget()).getScene().getWindow());
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void setAllToBoard(ActionEvent event) {
        CompletableFuture.runAsync(() -> setAllToBoardAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void saveConfigToFile(ActionEvent event) {
        CompletableFuture.runAsync(() -> saveConfigToFileAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void loadConfigFromFile(ActionEvent event) {
        CompletableFuture.runAsync(() -> loadConfigFromFileAction.accept(event)).exceptionally(defaultExceptionHandler);
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void loadDefaultConfig(ActionEvent event) {
        CompletableFuture.runAsync(loadDefaultConfigAction).exceptionally(defaultExceptionHandler);
    }


    private Consumer<ButtonImpl> setButtonAction = (buttonImpl) -> {
        Object currentValue = buttonImpl.getValue();
        String variableName = buttonImpl.getFieldName();

        System.out.println(format("%s setting value: %s", variableName, currentValue));
        backendCaller.setValue(variableName, currentValue);
    };
    private Consumer<ButtonImpl> setRpmButtonAction = (buttonImpl) -> backendCaller.sendCommand(format("rpm %d", (Integer) buttonImpl.getValue()));

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
                    () -> connect(null),
                    new ButtonType("Reconnect", ButtonType.OK.getButtonData()),
                    ButtonType.CANCEL);
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

    private final UpdateConnectionStatusTimer updateConnectionStatusTimer = new UpdateConnectionStatusTimer();

    private final Timer dcValueReader = new Timer();

    private final ChangeListener<? super Scene> closeWindowListener = (observable, oldScene, newScene) -> {
        if (oldScene == null && newScene != null) {
            newScene.windowProperty().addListener((observableWindow, oldWindow, newWindow) -> {
                if (oldWindow == null && newWindow != null) {
                    newWindow.setOnCloseRequest((windowEvent) -> {
                        backendCaller.closeMainWindow();
                        updateConnectionStatusTimer.stopTimer();
                        dcValueReader.cancel();
                        ((Stage) windowEvent.getTarget()).close();
                    });
                }
            });
        }
    };

    private final Supplier<ConnectionInfo> checkConnectionAction = backendCaller::checkConnection;

    private TimerTask getUpdateConnectionStatusTask() {
        return new TimerTask() {
            @Override
            public void run() {
                ConnectionInfo info;
                try {
                    System.out.println("task started!");
                    info = CompletableFuture.supplyAsync(checkConnectionAction).exceptionally(CheckConnectionExceptionHandler).get(defaultTimeOut, SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                    printError(e);
                    info = NO_CONNECTION;
                }

                if (!info.isConnected()) {
                    mainElement.fireEvent(new Event(connectionLost));
                }

                updateConnectionInfo.accept(info);
            }
        };
    }

    private final Consumer<ConnectionInfo> updateConnectionInfo = info -> {
        updateStatusBar(info);
        recalculateButtonsAvailability(info.isConnected());
        if (!info.isConnected()) updateConnectionStatusTimer.stop();
    };

    private final Runnable connectAction = () -> {
        String currentPort = getPort();
        if (!port_button.getItems().contains(currentPort)) port_button.getItems().add(currentPort);
        try {
            backendCaller.connect(currentPort);
            System.out.println(format("Successfully connected to port: '%s'", getPort()));
        } catch (SerialPortException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    };
    private final Runnable disconnectAction = () -> {
        updateConnectionInfo.accept(NO_CONNECTION);
        backendCaller.disconnect(getPort());
        System.out.println(format("Controller manually disconnected from port '%s'", getPort()));
    };

    private final Consumer<String> sendCommandAction = text -> {
        String serverAnswer;
        try {
            serverAnswer = CompletableFuture.supplyAsync(() -> backendCaller.sendCommand(text)).exceptionally((e) -> {
                printError(e);
                return null;
            }).get(defaultTimeOut, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            printError(e);
            serverAnswer = null;
        }
        if (!isBlankOrNull(serverAnswer)) print(serverAnswer);
    };

    private final Runnable rebootAction = () -> sendCommandAction.accept("reboot");
    private final Runnable bootAction = () -> sendCommandAction.accept("boot");
    private final Runnable beepAction = () -> sendCommandAction.accept("beep");
    private final Runnable dcArmAction = () -> sendCommandAction.accept("dc arm");
    private final Runnable rpmArmAction = () -> sendCommandAction.accept("rpm arm");
    private final Runnable updateConnectionStatusAction = this::updateConnectionStatus;
    private final Function<Throwable, ? extends Void> defaultExceptionHandler = ex -> {
        ex.printStackTrace();
        return null;
    };
    private final Function<Throwable, ? extends ConnectionInfo> CheckConnectionExceptionHandler = e -> {
        SapogUtils.printError(setup_console, e);
        return NO_CONNECTION;
    };

    private final Function<String, Object> getCurrentValueFunction = backendCaller::getCurrentValue;

    private final Consumer<ProgressWindowController> progressBarLoadAction = (c) -> {
        LoadAllFromBoardTask task = new LoadAllFromBoardTask(c, buttons);
        c.getProgress_bar().progressProperty().bind(task.progressProperty());
        CompletableFuture.runAsync(task).thenRun(() -> setLabel(c.getLabel(), "All parameters loaded from board."));
    };

    private final Consumer<ProgressWindowController> progressBarSaveAction = (c) -> {
        SetAllToBoardTask task = new SetAllToBoardTask(c, buttons);
        c.getProgress_bar().progressProperty().bind(task.progressProperty());
        CompletableFuture.runAsync(task).thenRun(() -> setLabel(c.getLabel(), "All parameters set to board."));
    };

    private final Consumer<File> loadConfigFromFileActionConsumer = f -> {
        Properties props = parsePropertiesFile(f);
        buttons.forEach((k, v) -> {
            String configValue = props.getProperty(v.getFieldName());

            if (!isBlankOrNull(configValue)) v.setValue(configValue);
            else System.out.println(format("No value for field '%s'...", k));
        });
    };

    private final Consumer<File> saveConfigToFileActionConsumer = f -> savePropertiesToFile(prepareCurrentValuesConfig(), f);

    private final Consumer<Window> loadAllFromBoardAction = window -> showModalWindow("Loading parameters...", progressWindowConfigLocation,
            window, progressBarLoadAction);

    private final Consumer<ActionEvent> setAllToBoardAction = event -> showModalWindow("Setting parameters...", progressWindowConfigLocation,
            ((Button) event.getTarget()).getScene().getWindow(), progressBarSaveAction);

    private final Consumer<ActionEvent> saveConfigToFileAction = event -> showFileDialog(true,
            ((Button) event.getTarget()).getScene().getWindow(), saveConfigToFileActionConsumer);

    private final Consumer<ActionEvent> loadConfigFromFileAction = event -> showFileDialog(false,
            ((Button) event.getTarget()).getScene().getWindow(), loadConfigFromFileActionConsumer);

    private final Consumer<File> parsePropertiesFromFile = file -> {
        Properties props = parsePropertiesFile(file);
        buttons.forEach((k, v) -> {
            String configValue = props.getProperty(v.getFieldName());

            if (!isBlankOrNull(configValue)) v.setValue(configValue);
            else System.out.println(format("No value for field '%s'...", k));
        });
    };

    private final BiFunction<Void, Throwable, Void> connectionHandler = (voidValue, exception) -> {
        if (exception != null) {
            SapogUtils.alert(mainElement.getScene().getWindow(), ERROR, "Connection error", null, getSimpleErrorMessage(exception), null, null);
        } else {
            System.out.println("update connection status");
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
                sendCommandAction.accept(text);
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
    private void setSliderValueImpl(Slider slider) {
        CompletableFuture.runAsync(() -> {
            if (isBlankOrNull((dc_info.getText()))) return; //---

            Double currentValue = roundDoubleValue(slider.getValue());
            if (lastSliderValue != null && lastSliderValue.equals(currentValue)) return; //---

            String variableName = slider.getId();

            try {
                System.out.println(format("%s changing value: '%s' -> '%s'", variableName, lastSliderValue, currentValue));

                slider.setDisable(true);
                backendCaller.sendCommand(format("dc %.2f", currentValue));
                lastSliderValue = currentValue;
                System.out.println("successfully set value");
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
            printError(ex);
        }
        return port;
    }

    private final Consumer<ConnectionInfo> updateStatusBarAction = info -> {
        vol_info.setText(isBlankOrNull(info.getVoltage()) ? "" : format("%s v", info.getVoltage()));
        amp_info.setText(isBlankOrNull(info.getAmperage()) ? "" : format("%s A", info.getAmperage()));
        dc_info.setText(isBlankOrNull(info.getDc()) ? "" : format("%s DC", info.getDc()));
        rpm_info.setText(isBlankOrNull(info.getRpm()) ? "" : format("%s RPM", info.getRpm()));
        connection_indicator.setFill(info.isConnected() ? Color.valueOf("2c9a24") : Color.RED);
        //TODO допилить добавление версии
        info.getVersion();
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
        CompletableFuture.runAsync(() -> updateConnectionStatusTimer.start(getUpdateConnectionStatusTask(), 0, checkConnectionFrequency));
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
        port_button.setDisable(hasConnection);

        connect_button.setDisable(hasConnection);
        disconnect_button.setDisable(!hasConnection);
        reboot_button.setDisable(!hasConnection);
        boot_button.setDisable(!hasConnection);
        beep_button.setDisable(!hasConnection);

        buttons.forEach((name, buttonImpl) -> {
            buttonImpl.getButton().setDisable(!hasConnection);
            buttonImpl.getFieldImpl().setDisable(!hasConnection);
        });

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

    private Object getCurrentBackValue(String fieldName) {
        try {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(
                    () -> getCurrentValueFunction.apply(fieldName)).exceptionally(defaultExceptionHandler);
            return future.get(defaultTimeOut, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void startReadDCValues() {
        dcValueReader.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                setSliderValueImpl(dc_slider);
            }
        }, 0, sliderFrequency);
    }

    private void loadAllFromBoardImpl(Window window) {
        CompletableFuture.runAsync(() -> loadAllFromBoardAction.accept(window)).exceptionally(defaultExceptionHandler);
    }
}