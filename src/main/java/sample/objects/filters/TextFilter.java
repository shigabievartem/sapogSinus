package sample.objects.filters;

import java.util.Collection;
import java.util.function.UnaryOperator;

import static javafx.scene.control.TextFormatter.Change;
import static sample.utils.SapogConst.ERROR_BUTTON_STYLE;

/**
 * Класс, содержащий общую логику применения стилей для текстового поля
 */
public abstract class TextFilter implements UnaryOperator<Change> {

    /**
     * Наименование применяемого стиля
     */
    private String errorStyleName = ERROR_BUTTON_STYLE;

    /**
     * Нужно ли применять стиль к полю
     */
    private boolean isApplyErrorStyle = false;

    @Override
    public Change apply(Change change) {

        boolean result = checkValue(change);
        applyStyle(result, change.getControl().getStyleClass(), errorStyleName); //---
        return result ? change : null;
    }

    private void applyStyle(boolean isSuccess, Collection<String> styles, String styleName) {
        if (isSuccess) {
            styles.remove(styleName);
        } else if (!styles.contains(styleName)) {
            if (isApplyErrorStyle) styles.add(styleName);
        }
    }

    abstract boolean checkValue(Change change);

    public String getErrorStyleName() {
        return errorStyleName;
    }

    public TextFilter setErrorStyleName(String errorStyleName) {
        this.errorStyleName = errorStyleName;
        return this;
    }

    public boolean isApplyErrorStyle() {
        return isApplyErrorStyle;
    }

    public TextFilter setApplyErrorStyle(boolean applyErrorStyle) {
        isApplyErrorStyle = applyErrorStyle;
        return this;
    }
}
