package sample.utils;

import com.sun.istack.internal.NotNull;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static sample.utils.SapogConst.ERROR_BUTTON_STYLE;
import static sample.utils.SapogConst.Events.closeModalWindow;

public class SapogUtils {

    private static final DecimalFormat decimalFormat;
    private static final DateTimeFormatter dateTimeFormatter;

    static {
        decimalFormat = new DecimalFormat("##.##");
        decimalFormat.setMinimumIntegerDigits(2);
        dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    }

    public static synchronized void showPropertiesFile(boolean isSaveAction, @NotNull Window window, Consumer<File> fileHandler) {
        showFileDialog(isSaveAction, window, fileHandler, new FileChooser.ExtensionFilter("Config file (*.properties)", "*.properties"));

    }

    public static synchronized void showDriverFile(boolean isSaveAction, @NotNull Window window, Consumer<File> fileHandler) {
        showFileDialog(isSaveAction, window, fileHandler, new FileChooser.ExtensionFilter("Driver file", "*.bin", "*.s19", "*.hex"));
    }


    public static synchronized void showFileDialog(boolean isSaveAction, @NotNull Window window, Consumer<File> fileHandler, FileChooser.ExtensionFilter extensionFilter) {
        runInMainThread(() -> {
            FileChooser fileChooser = new FileChooser();

            fileChooser.getExtensionFilters().add(extensionFilter);

            File file = isSaveAction ? fileChooser.showSaveDialog(window) : fileChooser.showOpenDialog(window);

            if (file != null && fileHandler != null) fileHandler.accept(file);
        });
    }

    public static void savePropertiesToFile(Properties props, File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


    }

    public static Properties parsePropertiesFile(File file) {
        Properties property = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            property.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return property;
    }

    public static synchronized <T> void showModalWindow(@NotNull String title, @NotNull URL location, @NotNull Window window,
                                                        Consumer<T> actionWithController) {
        runInMainThread(
                () -> {
                    try {
                        Stage stage = new Stage();
                        FXMLLoader loader = new FXMLLoader(location);
                        Parent root = loader.load();
                        stage.setResizable(false);
                        stage.setTitle(title);
                        stage.setMinWidth(400);
                        stage.setMinHeight(150);
                        Scene scene = new Scene(root);
                        root.addEventHandler(closeModalWindow, (event) -> {
                            try {
                                runInMainThread(() -> {
                                    stage.close();
                                    window.fireEvent(event);
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        stage.setScene(scene);
                        stage.initModality(Modality.WINDOW_MODAL);
                        stage.initOwner(window);
                        if (actionWithController != null) actionWithController.accept(loader.getController());
                        stage.show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
    }


    /**
     * Вывести информацию в диалоговом окне
     */
    public static void alert(@NotNull Window window, @NotNull Alert.AlertType type, @NotNull String title,
                             String headerText, String contentText, Runnable action, ButtonType... buttons) {
        runInMainThread(() -> {
            Alert alert = new Alert(type, contentText);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.initModality(Modality.WINDOW_MODAL);
            alert.initOwner(window);
            alert.setContentText(contentText);

            if (buttons != null && buttons.length != 0) {
                alert.getButtonTypes().clear();
                Stream.of(buttons).forEachOrdered(alert.getButtonTypes()::add);
            }

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && ButtonType.OK.getButtonData() == result.get().getButtonData() && action != null)
                action.run();
        });
    }

    /**
     * Метод преобрзует число до второго десятичного знака
     * вид на выходе: #0.01
     */
    public static double roundDoubleValue(double floatValue) {
        return ((double) Math.round(floatValue * 100) / 100);
    }


    /**
     * Метод проверяет значение на null, а строку ещё на пустое значение
     */
    public static boolean isBlankOrNull(Object obj) {
        if (obj == null) return true; //---
        //---
        return (obj instanceof String && ((String) obj).trim().isEmpty());
    }

    /**
     * Метод пытается получить из текстового поля целочисленное значение
     * и применяет для поля ошибочный стиль в зависимости от 2го параметра
     */
    public static int getIntValue(TextField field, boolean applyErrorStyleToField) {
        int portNum;
        try {
            portNum = Integer.parseInt(field.getText());
        } catch (NumberFormatException ex) {
            ObservableList<String> styles = field.getStyleClass();
            if (applyErrorStyleToField && !styles.contains(ERROR_BUTTON_STYLE)) {
                styles.add(ERROR_BUTTON_STYLE);
            }
            throw ex;
        }
        return portNum;
    }

    /**
     * Метод пробегается по стеку до первоначальной ошибки и возвращает её стэк
     */
    public static String getSimpleErrorMessage(@NotNull Throwable throwable) {
        Throwable cause;
        while ((cause = throwable.getCause()) != null) {
            throwable = cause;
        }
        return throwable.getMessage();
    }


    /**
     * Метод печатает строку в консоль
     */
    public static void print(TextArea console, String text, Object... objects) {
        if (console == null || isBlankOrNull(text)) return; //---
        synchronized (console) {
            runInMainThread(() -> {
                if (!console.getText().isEmpty()) console.appendText(System.lineSeparator());
                console.appendText(format("[%s] ", ZonedDateTime.now().format(dateTimeFormatter)));
                console.appendText(format(text, objects));
            });
        }
    }

    public static String formatDecimalValue(Object value) {
        return decimalFormat.format(value);
    }

    /**
     * Метод печатает строку в консоль как есть
     */
    public static void printDirect(TextArea console, String text, Object... objects) {
        if (console == null || isBlankOrNull(text)) return; //---
        synchronized (console) {
            runInMainThread(() -> {
                console.appendText(format(text, objects));
            });
        }
    }


    public static void runInMainThread(final Runnable runnable) {
        Objects.requireNonNull(runnable, "Empty action!");
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    /**
     * Метод печатает строку в консоль
     */
    public static void setLabel(Label label, String text, Object... objects) {
        if (label == null) return; //---
        synchronized (label) {
            runInMainThread(() -> label.setText(format(text, objects)));
        }
    }

    /**
     * Метод распечатывает stackTrace ошибки
     */
    public static Void printError(TextArea console, Throwable throwableElement) {
        if (throwableElement instanceof RuntimeException) {
            print(console, getSimpleErrorMessage(throwableElement));
            return null; //---
        }
        StringWriter stackTraceWriter = new StringWriter();
        throwableElement.printStackTrace(new PrintWriter(stackTraceWriter));
        print(console, stackTraceWriter.toString());
        return null; //---
    }

    public static void printBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        for (byte b : bytes) {
            System.out.println(String.format("0x%02X", b));
        }
    }

    public static byte[] parseFileToBytesArray(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static byte xorBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 2) throw new RuntimeException("Nothing to XOR!");

        byte xorResult = bytes[0];
        for (int i = 1; i < bytes.length; i++) {
            xorResult ^= bytes[i];
        }
        return xorResult;
    }
}