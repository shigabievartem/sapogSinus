package sample.objects.filters;

public abstract class NumberFilter extends TextFilter {

    private boolean isPositiveOnly = false;

    public NumberFilter setPositiveOnly(boolean positiveOnly) {
        isPositiveOnly = positiveOnly;
        return this;
    }

    Number minValue;
    Number maxValue;

    public NumberFilter setMinValue(Number minValue) {
        this.minValue = minValue;
        return this;
    }

    public NumberFilter setMaxValue(Number maxValue) {
        this.maxValue = maxValue;
        return this;
    }

    boolean isPositiveOnly() {
        return isPositiveOnly;
    }
}
