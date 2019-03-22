package sample.objects.tasks;

import com.sun.istack.internal.NotNull;
import javafx.concurrent.Task;
import sample.controllers.ProgressWindowController;
import sample.objects.ButtonImpl;
import sample.utils.BackendCaller;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static sample.utils.SapogUtils.*;

/**
 * Задача по загрузке значений
 */
public class LoadAllFromBoardTask extends Task<Void> {

    private final Map<String, Object> currentValues;
    private final Map<String, ButtonImpl> buttons;
    private final ProgressWindowController progressWindowController;

    public LoadAllFromBoardTask(@NotNull ProgressWindowController progressWindowController, @NotNull Map<String, ButtonImpl> buttons) {
        this.progressWindowController = progressWindowController;
        this.currentValues = Objects.requireNonNull(BackendCaller.getInstance().getCurrentValues(), "Empty current values!");
        this.buttons = buttons;
    }


    protected Void call() throws Exception {
        final AtomicInteger i = new AtomicInteger(1);
        buttons.forEach((k, v) -> {
            String fieldName = v.getFieldName();
            setLabel(progressWindowController.getLabel(), "Setting '%s' value", fieldName);
            try {
                v.setValue(currentValues.get(fieldName));
            } catch (Exception ex) {
                print(progressWindowController.getConsole(), "'%s': %s", fieldName, getSimpleErrorMessage(ex));
            } finally {
                this.updateProgress(i.incrementAndGet(), buttons.size());
            }
        });
        return null;
    }
}
