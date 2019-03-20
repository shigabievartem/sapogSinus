package sample.objects.filters;

import static javafx.scene.control.TextFormatter.Change;

/**
 * Фильтр ввода для целых чисел
 */
public class IntegerFilter extends NumberFilter {

    @Override
    boolean checkValue(Change change) {
        String value = change.getControlNewText();
        if ("".equals(value)) return true;
        if (!isPositiveOnly() && "-".equals(value)) return true;
        try {
            Integer intValue = Integer.parseInt(change.getControlNewText());
            if (minValue != null && intValue < (Integer) minValue) return false;
            if (maxValue != null && intValue > (Integer) maxValue) return false;
            return true;
        } catch (Exception ex) {
            return false;
        }
//        return value.matches(this.isPositiveOnly() ? "(^\\d*$)" : "(^[-]?\\d*$)");
    }
}
