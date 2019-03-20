package sample.controllers;

import com.sun.istack.internal.NotNull;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import jssc.SerialPortException;
import sample.objects.ButtonImpl;
import sample.objects.ConnectionInfo;
import sample.objects.filters.DecimalFilter;
import sample.objects.filters.IntegerFilter;
import sample.objects.tasks.LoadAllFromBoardTask;
import sample.objects.tasks.SetAllToBoardTask;
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

        backendCaller.setMainConsole(setup_console);
    }

    /**
     * Метод пытается подключиться к контроллеру
     */
    @FXML
    public void connect(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(connectAction).handle(connectionHandler).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void disconnect(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(disconnectAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void reboot(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(rebootAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void boot(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(bootAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void beep(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(beepAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void dcArmAction(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(dcArmAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод рвёт коннект с контроллером
     */
    @FXML
    public void rpmArmAction(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(rpmArmAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void loadAllFromBoard(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(() -> loadAllFromBoardAction.accept(event)).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void setAllToBoard(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(() -> setAllToBoardAction.accept(event)).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void saveConfigToFile(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(() -> saveConfigToFileAction.accept(event)).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void loadConfigFromFile(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(() -> loadConfigFromFileAction.accept(event)).exceptionally(defaultExceptionHandler));
    }

    /**
     * Метод устанавливает все текущие значения с формы
     */
    @FXML
    public void loadDefaultConfig(ActionEvent event) {
        runSaveAction(() -> CompletableFuture.runAsync(loadDefaultConfigAction).exceptionally(defaultExceptionHandler));
    }

    /**
     * Обработчик нажатия на кнопку "Установить значение"
     */
    @FXML
    public void setValue(ActionEvent event) {
        runSaveAction(() -> {
            Objects.requireNonNull(event, "Empty event!");
            if (event.getTarget() == null || event.getTarget().getClass() != Button.class) {
                print("Bad button");
                return; //---
            }

            EventTarget target = Objects.requireNonNull(event.getTarget(), "Target element cannot be empty!");
            if (target instanceof Button) setButtonValueImpl(buttons.get(((Button) event.getTarget()).getId()));
        });
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

    private final Supplier<ConnectionInfo> checkConnectionAction = () -> backendCaller.checkConnection(getPort());


    private final Timer updateConnectionStatusTimer = new Timer();
    private final TimerTask updateConnectionStatusTask = new TimerTask() {
        @Override
        public void run() {
            ConnectionInfo info;
            try {
                info = CompletableFuture.supplyAsync(checkConnectionAction).exceptionally(CheckConnectionExceptionHandler).get(defaultTimeOut, SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
                printError(e);
                info = NO_CONNECTION;
            }

            if (!info.isConnected()) {
                mainElement.fireEvent(new Event(connectionLost));
                print("Connection lost...");
            }

            updateConnectionInfo.accept(info);
        }
    };

    private final Consumer<ConnectionInfo> updateConnectionInfo = info -> {
        updateStatusBar(info);
        recalculateButtonsAvailability(info.isConnected());
        if (!info.isConnected()) updateConnectionStatusTimer.cancel();
    };

    private final Runnable connectAction = () -> {
        String currentPort = getPort();
        if (!port_button.getItems().contains(currentPort)) port_button.getItems().add(currentPort);
        try {
            backendCaller.connect(currentPort);
            print("Successfully connected to port: '%s'", getPort());
        } catch (SerialPortException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    };
    private final Runnable disconnectAction = () -> {
        updateConnectionInfo.accept(NO_CONNECTION);
        backendCaller.disconnect(getPort());
        print("Controller manually disconnected from port '%s'", getPort());
    };

    private final Consumer<String> sendCommandAction = text -> {
        print(text);
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
    private final Function<Throwable, ? extends Void> defaultExceptionHandler = this::printError;
    private final Function<Throwable, ? extends ConnectionInfo> CheckConnectionExceptionHandler = e -> {
        SapogUtils.printError(setup_console, e);
        return NO_CONNECTION;
    };

    private final BiFunction<Void, Throwable, Void> connectionHandler = (voidValue, exception) -> {
        if (exception != null) {
            defaultExceptionHandler.apply(exception);
        } else {
            updateConnectionStatusAction.run();
        }
        return null;
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
            else print("No value for field '%s'...", k);
        });
    };

    private final Consumer<File> saveConfigToFileActionConsumer = f -> savePropertiesToFile(prepareCurrentValuesConfig(), f);

    private final Consumer<ActionEvent> loadAllFromBoardAction = event -> showModalWindow("Loading parameters...", progressWindowConfigLocation,
            ((Button) event.getTarget()).getScene().getWindow(), progressBarLoadAction);

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
            else print("No value for field '%s'...", k);
        });
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
        esc_base_value.setTextFormatter(new TextFormatter(new IntegerFilter().setPositiveOnly(true).setMinValue(0).setMaxValue(2047)));
        esc_index_value.setTextFormatter(new TextFormatter(new IntegerFilter().setPositiveOnly(true).setMinValue(0).setMaxValue(31)));
        mot_num_poles_value.setTextFormatter(new TextFormatter(new IntegerFilter().setPositiveOnly(true).setMinValue(2).setMaxValue(100)));
        mot_dc_slope_value.setTextFormatter(new TextFormatter(new DecimalFilter().setPositiveOnly(true).setMinValue(0.1f).setMaxValue(20f)));
        mot_dc_accel_value.setTextFormatter(new TextFormatter(new DecimalFilter().setPositiveOnly(true).setMinValue(0.001f).setMaxValue(0.5f)));
        mot_pwm_hz_value.setTextFormatter(new TextFormatter(new IntegerFilter().setPositiveOnly(true).setMinValue(2000).setMaxValue(75000)));
        temp_lim_value.setTextFormatter(new TextFormatter(new IntegerFilter().setPositiveOnly(true).setMinValue(90).setMaxValue(150)));
        mot_i_max_value.setTextFormatter(new TextFormatter(new DecimalFilter().setPositiveOnly(true).setMinValue(1f).setMaxValue(60f)));
        sens_i_scale_value.setTextFormatter(new TextFormatter(new DecimalFilter().setPositiveOnly(true).setMinValue(0f).setMaxValue(1000000f)));
        // TODO уточнить границы значений
        set_rpm_value.setTextFormatter(new TextFormatter(new IntegerFilter().setPositiveOnly(true)/*.setMinValue(0).setMaxValue(1000000)*/));
    }

    /**
     * Соберем все кнопки с изменяемым значением в одну кололекцию
     */
    private void initializeButtons() {
        port_button.setItems(FXCollections.observableArrayList("por1", "qwe/rty"));
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
            String variableName = slider.getId();

            Double currentBackValue = (Double) getCurrentBackValue(variableName);
            if (currentValue.equals(currentBackValue)) return; //---

            try {
                print("%s changing value: '%s' -> '%s'", variableName, currentBackValue, currentValue);

                slider.setDisable(true);
                backendCaller.setValue(variableName, currentValue);
                print("successfully set value");
            } finally {
                slider.setDisable(false);
            }
        }).exceptionally(defaultExceptionHandler);
    }

    private void setButtonValueImpl(ButtonImpl buttonImpl) {
        CompletableFuture.runAsync(() -> {
            try {
                Object currentValue = buttonImpl.getValue();
                String variableName = buttonImpl.getFieldName();
                Object currentBackValue = getCurrentBackValue(variableName);
                if (currentValue == null || currentValue.equals(currentBackValue))
                    return; //---
                print("%s setting value: %s", variableName, currentValue);

                buttonImpl.getIndicator().setVisible(true);
                buttonImpl.getButton().setDisable(true);
                backendCaller.setValue(variableName, currentValue);
                print("successfully set value");
            } finally {
                buttonImpl.getIndicator().setVisible(false);
                buttonImpl.getButton().setDisable(false);
            }
        }).exceptionally(defaultExceptionHandler);
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
        CompletableFuture.runAsync(() -> updateConnectionStatusTimer.scheduleAtFixedRate(updateConnectionStatusTask, 0, checkConnectionFrequency));
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
            printError(ex);
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
        buttons.forEach((k, v) -> props.setProperty(v.getFieldName(), String.valueOf(v.getValue())));
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
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                setSliderValueImpl(dc_slider);
            }
        }, 0, sliderFrequency);
    }
}