package sample.controllers;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import static sample.utils.SapogConst.Events.closeModalWindow;

public class ProgressWindowController {
    @FXML
    private Label label;
    @FXML
    private TextArea console;
    @FXML
    private ProgressBar progress_bar;

    @FXML
    void initialize() {

        label.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                newScene.windowProperty().addListener((observableWindow, oldWindow, newWindow) -> {
                    if (oldWindow == null && newWindow != null) {
                        newWindow.setOnCloseRequest((windowEvent) -> {
                            Stage target = (Stage) windowEvent.getTarget();
                            if (requestPermissionToClose()) {
                                target.close();
                            } else {
                                // if we are here, we consume the event to prevent closing the dialog
                                windowEvent.consume();
                            }
                        });
                    }
                });
            }
        });
    }

    public Label getLabel() {
        return label;
    }

    public TextArea getConsole() {
        synchronized (console) {
            return console;
        }
    }

    public ProgressBar getProgress_bar() {
        synchronized (progress_bar) {
            return progress_bar;
        }
    }

    public void closeWindow() {
        getProgress_bar().getScene().getRoot().fireEvent(new Event(closeModalWindow));
    }

    private boolean requestPermissionToClose() {
        return progress_bar.progressProperty().getValue() == 1;
    }
}
