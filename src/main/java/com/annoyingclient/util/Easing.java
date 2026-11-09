package com.annoyingclient.util;

/** Standard easing curves mapping linear progress 0..1 to eased progress 0..1. */
public enum Easing {
    LINEAR {
        @Override
        public float apply(float t) {
            return t;
        }
    },
    EASE_OUT_CUBIC {
        @Override
        public float apply(float t) {
            float inv = 1 - t;
            return 1 - inv * inv * inv;
        }
    },
    EASE_IN_OUT_CUBIC {
        @Override
        public float apply(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
        }
    },
    EASE_OUT_BACK {
        @Override
        public float apply(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1;
            float x = t - 1;
            return 1 + c3 * x * x * x + c1 * x * x;
        }
    },
    EASE_OUT_QUINT {
        @Override
        public float apply(float t) {
            float inv = 1 - t;
            return 1 - inv * inv * inv * inv * inv;
        }
    };

    public abstract float apply(float t);
}
