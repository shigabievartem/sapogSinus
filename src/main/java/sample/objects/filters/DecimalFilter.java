package sample.objects.filters;

import static javafx.scene.control.TextFormatter.Change;

/**
 * Фильтр ввода для дробных чисел
 */
public class DecimalFilter extends NumberFilter {

    public DecimalFilter(boolean positiveOnly, Float minValue, Float maxValue) {
        super(positiveOnly, minValue, maxValue);
    }

    public DecimalFilter() {
        super(false, null, null);
    }

    @Override
    boolean checkValue(Change change) {
        String value = change.getControlNewText();
        if ("".equals(value)) return true;
        if (!isPositiveOnly() && "-".equals(value)) return true;
        try {
            Float.parseFloat(change.getControlNewText());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Float getMinValue() {
        return (Float) super.getMinValue();
    }

    @Override
    public Float getMaxValue() {
        return (Float) super.getMaxValue();
    }
}
