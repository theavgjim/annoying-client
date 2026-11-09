package com.annoyingclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A bounded number, snapped to {@code step}. Integer-like settings simply use a step of 1. */
public class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private String suffix = "";

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        if (min > max) {
            throw new IllegalArgumentException("min > max for " + name);
        }
        this.min = min;
        this.max = max;
        this.step = step <= 0 ? 0 : step;
        this.decimals = this.step == 0 ? 2 : Math.max(0, BigDecimal.valueOf(this.step).stripTrailingZeros().scale());
        this.value = validate(defaultValue);
    }

    public NumberSetting withSuffix(String suffix) {
        this.suffix = suffix == null ? "" : suffix;
        return this;
    }

    @Override
    protected Double validate(Double candidate) {
        double v = candidate;
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return defaultValue;
        }
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            v = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
        }
        return Math.max(min, Math.min(max, v));
    }

    public double get() {
        return value;
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    /** Position of the value between min and max, from 0 to 1 (used by sliders). */
    public double getProgress() {
        return max == min ? 0 : (value - min) / (max - min);
    }

    public void setProgress(double progress) {
        setValue(min + Math.max(0, Math.min(1, progress)) * (max - min));
    }

    public String getDisplayValue() {
        String number = decimals == 0 ? Long.toString(Math.round(value))
                : BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
        return number + suffix;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        try {
            setValue(element.getAsDouble());
            return true;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return false;
        }
    }
}
