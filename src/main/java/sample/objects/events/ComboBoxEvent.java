package sample.objects.events;

import com.sun.istack.internal.NotNull;
import javafx.event.Event;
import javafx.event.EventType;
import sample.objects.PortItem;

import java.util.Objects;

public class ComboBoxEvent extends Event {
    final private ActionType actionType;
    final private PortItem item;

    public ComboBoxEvent(@NotNull EventType<? extends Event> eventType, PortItem item, @NotNull ActionType type) {
        super(eventType);
        this.item = type == ActionType.Create ? Objects.requireNonNull(item, "Port cannot be null!") : item;
        this.actionType = type;
    }

    public PortItem getItem() {
        return item;
    }

    public ActionType getActionType() {
        return actionType;
    }
}
