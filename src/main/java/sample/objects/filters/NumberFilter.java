package sample.objects.filters;

public abstract class NumberFilter extends TextFilter {

    private boolean isPositiveOnly = false;

    private Number minValue;
    private Number maxValue;

    public NumberFilter(boolean positiveOnly, Number minValue, Number maxValue) {
        this.isPositiveOnly = positiveOnly;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public Number getMinValue() {
        return minValue;
    }

    public Number getMaxValue() {
        return maxValue;
    }

    boolean isPositiveOnly() {
        return isPositiveOnly;
    }
}
