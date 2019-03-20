package sample.objects.filters;

import static javafx.scene.control.TextFormatter.Change;

/**
 * Фильтр ввода для целых чисел
 */
public class IntegerFilter extends NumberFilter {
    public IntegerFilter(boolean positiveOnly, Integer minValue, Integer maxValue) {
        super(positiveOnly, minValue, maxValue);
    }

    public IntegerFilter() {
        super(false, null, null);
    }

    @Override
    boolean checkValue(Change change) {
        String value = change.getControlNewText();
        if ("".equals(value)) return true;
        if (!isPositiveOnly() && "-".equals(value)) return true;
        try {
            Integer.parseInt(change.getControlNewText());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Integer getMinValue() {
        return (Integer) super.getMinValue();
    }

    @Override
    public Integer getMaxValue() {
        return (Integer) super.getMaxValue();
    }
}
