package sample.objects;

import com.sun.istack.internal.NotNull;
import javafx.scene.control.*;
import sample.objects.filters.DecimalFilter;
import sample.objects.filters.IntegerFilter;

import java.util.Objects;
import java.util.function.UnaryOperator;

import static java.lang.String.format;
import static sample.utils.SapogUtils.isBlankOrNull;

public class ButtonImpl {

    final private Button button;
    final private TextField textField;
    final private CheckBox checkBox;
    final private ProgressIndicator indicator;
    final private String fieldName;


    public ButtonImpl(Button button, @NotNull TextField field, ProgressIndicator indicator) {
        this(button, field, null, indicator);
    }

    public ButtonImpl(Button button, @NotNull CheckBox field, ProgressIndicator indicator) {
        this(button, null, field, indicator);
    }

    private ButtonImpl(Button button, TextField textField, CheckBox checkField, ProgressIndicator indicator) {
        this.button = Objects.requireNonNull(button);
        this.indicator = Objects.requireNonNull(indicator);
        this.textField = textField;
        this.checkBox = checkField;
        this.fieldName = calculateFieldName();

    }

    public Button getButton() {
        return button;
    }

    public ProgressIndicator getIndicator() {
        return indicator;
    }

    public Control getFieldImpl() {
        return textField != null ? textField : checkBox;
    }

    /**
     * Возвращает значение из поля (визуальное значение)
     */
    public Object getValue() {
        if (checkBox != null) return checkBox.isSelected(); //---

        String currentValue = String.valueOf(textField.getCharacters()).trim();
        TextFormatter formatter = textField.getTextFormatter();
        UnaryOperator filter;
        if (formatter == null || (filter = formatter.getFilter()) == null) return currentValue; //---
        if (isBlankOrNull(currentValue) || currentValue.equals("-")) return null; //---

        if (DecimalFilter.class == filter.getClass()) return Double.valueOf(currentValue); //---
        if (IntegerFilter.class == filter.getClass()) return Integer.valueOf(currentValue); //---

        throw new RuntimeException("Unknown value type!");
    }

    public String getFieldName() {
        return this.fieldName;
    }

    /**
     * Получиим имя поля из названия кнопки
     */
    private String calculateFieldName() {
        String postFix = "_button";
        String but = button.getId();
        return but.endsWith(postFix) ? but.substring(0, but.length() - postFix.length()) : but;
    }

    /**
     * Метод устанавливает визуальное значение
     */
    public void setValue(Object currentValue) {
//        if (checkBox != null && Objects.requireNonNull(currentValue, "Boolean value can not be null") instanceof Boolean) {
//            checkBox.setSelected((Boolean) currentValue);
//            return; //---
//        }
        if (checkBox != null) {
            if (Objects.requireNonNull(currentValue, "Boolean value can not be null") instanceof Boolean) {
                checkBox.setSelected((Boolean) currentValue);
                return; //---
            }
            if (currentValue instanceof String) {
                checkBox.setSelected(Boolean.parseBoolean((String) currentValue));
                return; //---
            }
            throw new RuntimeException(format("Value '%s' cannot be fetch to boolean value", currentValue));
        }
        textField.setText(currentValue == null ? "" : currentValue.toString());

    }
}
