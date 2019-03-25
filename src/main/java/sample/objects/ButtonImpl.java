package sample.objects;

import com.sun.istack.internal.NotNull;
import javafx.scene.control.*;
import sample.objects.filters.DecimalFilter;
import sample.objects.filters.IntegerFilter;

import java.util.Objects;
import java.util.function.UnaryOperator;

import static java.lang.String.format;
import static sample.utils.SapogConst.ERROR_BUTTON_STYLE;
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
        try {
            return getValueImpl();
        } catch (Exception ex) {
            ex.printStackTrace();
            if (textField != null && !textField.getStyleClass().contains(ERROR_BUTTON_STYLE))
                textField.getStyleClass().add(ERROR_BUTTON_STYLE);
            throw ex;
        }
    }

    private Object getValueImpl() {
        if (checkBox != null) return checkBox.isSelected() ? 1 : 0; //---

        String currentValue = String.valueOf(textField.getCharacters()).trim();
        TextFormatter formatter = textField.getTextFormatter();
        UnaryOperator filter;
        if (formatter == null || (filter = formatter.getFilter()) == null) return currentValue; //---
        if (isBlankOrNull(currentValue) || currentValue.equals("-")) return null; //---

        if (DecimalFilter.class == filter.getClass())
            return checkFloatValue((DecimalFilter) filter, currentValue); //---

        if (IntegerFilter.class == filter.getClass()) return checkIntValue((IntegerFilter) filter, currentValue); //---

        throw new RuntimeException("Unknown value type!");
    }

    private float checkFloatValue(DecimalFilter filter, String strValue) {
        Float value = Float.valueOf(strValue);
        Float maxValue = filter.getMaxValue();
        Float minValue = filter.getMinValue();
        if (maxValue != null && value > maxValue) {
            setValue(maxValue);
            throw new RuntimeException(format("Max value(%s) < Current value(%s)", maxValue, value));
        }
        if (minValue != null && value < minValue) {
            setValue(minValue);
            throw new RuntimeException(format("Min value(%s) > Current value(%s)", minValue, value));
        }
        return value;
    }

    private int checkIntValue(IntegerFilter filter, String strValue) {
        Integer value = Integer.valueOf(strValue);
        Integer maxValue = filter.getMaxValue();
        Integer minValue = filter.getMinValue();
        if (maxValue != null && value > maxValue) {
            setValue(maxValue);
            throw new RuntimeException(format("Max value(%s) < Current value(%s)", maxValue, value));
        }
        if (minValue != null && value < minValue) {
            setValue(minValue);
            throw new RuntimeException(format("Min value(%s) > Current value(%s)", minValue, value));
        }
        return value;
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
        if (textField != null) {
            textField.setText(currentValue == null ? "" : currentValue.toString());
            return; //---
        }
        if (currentValue == null || checkBox == null) return; //---
        checkBox.setSelected(parseBooleanValue(currentValue));
    }

    private boolean parseBooleanValue(@NotNull Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        if (value instanceof Integer) return (Integer) value == 1;
        throw new RuntimeException("unexpected type of boolean value");
    }
}
