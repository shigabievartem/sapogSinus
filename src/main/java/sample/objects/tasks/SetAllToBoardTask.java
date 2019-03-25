package sample.objects.tasks;

import com.sun.istack.internal.NotNull;
import javafx.concurrent.Task;
import sample.controllers.ProgressWindowController;
import sample.objects.ButtonImpl;
import sample.utils.BackendCaller;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static sample.utils.SapogUtils.*;

public class SetAllToBoardTask extends Task<Void> {
    private Map<String, Object> currentValues = null; //TODO обратите внимание
    private final Map<String, ButtonImpl> buttons;
    private final ProgressWindowController progressWindowController;

    public SetAllToBoardTask(@NotNull ProgressWindowController progressWindowController, @NotNull Map<String, ButtonImpl> buttons) {
        this.progressWindowController = progressWindowController;
        try {
            this.currentValues = Objects.requireNonNull(BackendCaller.getInstance().getCurrentValues(), "Empty current values!");
        } catch (IOException e) {
            e.printStackTrace();
            //TODO обработать
        }
        this.buttons = buttons;
    }


    protected Void call() {
        final AtomicInteger i = new AtomicInteger(1);
        buttons.forEach((k, v) -> {
            String fieldName = v.getFieldName();
            setLabel(progressWindowController.getLabel(), "Setting '%s' value", fieldName);
            try {
                v.getButton().fire();
            } catch (Exception ex) {
                print(progressWindowController.getConsole(), "'%s': %s", fieldName, getSimpleErrorMessage(ex));
            } finally {
                this.updateProgress(i.incrementAndGet(), buttons.size());
            }
        });
        return null;
    }
}
