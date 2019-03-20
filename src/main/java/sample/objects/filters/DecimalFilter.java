package sample.objects.filters;

import static javafx.scene.control.TextFormatter.Change;

/**
 * Фильтр ввода для дробных чисел
 */
public class DecimalFilter extends NumberFilter {

    @Override
    boolean checkValue(Change change) {
        String value = change.getControlNewText();
        if ("".equals(value)) return true;
        if (!isPositiveOnly() && "-".equals(value)) return true;
        try {
            Float floatValue = Float.parseFloat(change.getControlNewText());
            if (minValue != null && floatValue < (Float) minValue) return false;
            if (maxValue != null && floatValue > (Float) maxValue) return false;
            return true;
        } catch (Exception ex) {
            return false;
        }
//        return change.getControlNewText().matches(isPositiveOnly() ? "(^(\\d+\\.)?\\d*$)" : "(^[-]?(\\d+\\.)?\\d*$)");
    }
}
