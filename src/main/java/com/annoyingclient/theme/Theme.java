package com.annoyingclient.theme;

/**
 * A preset color scheme.
 *
 * @param accent     primary accent (toggles, highlights, sliders)
 * @param accent2    secondary accent used for gradients
 * @param background main window background
 * @param text       primary text color
 */
public record Theme(String name, int accent, int accent2, int background, int text) {
}
