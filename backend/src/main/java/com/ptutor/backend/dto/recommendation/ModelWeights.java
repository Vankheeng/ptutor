package com.ptutor.backend.dto.recommendation;

import java.util.Arrays;

/**
 * The weights of one model, in the order of {@link WeightModel#features()}. Immutable.
 */
public final class ModelWeights {

    private final WeightModel model;
    private final double[] values;

    public ModelWeights(WeightModel model, double... values) {
        if (values.length != model.features().size()) {
            throw new IllegalArgumentException(model + " has " + model.features().size() + " weights, not "
                    + values.length);
        }
        this.model = model;
        this.values = values.clone();
    }

    public WeightModel model() {
        return model;
    }

    /** @param index position of the feature in {@link WeightModel#features()} */
    public double get(int index) {
        return values[index];
    }

    public double get(String feature) {
        int index = model.features().indexOf(feature);
        if (index < 0) {
            throw new IllegalArgumentException(model + " has no feature " + feature);
        }
        return values[index];
    }

    public double[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelWeights weights && weights.model == model && Arrays.equals(weights.values, values);
    }

    @Override
    public int hashCode() {
        return 31 * model.hashCode() + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return model + Arrays.toString(values);
    }
}
