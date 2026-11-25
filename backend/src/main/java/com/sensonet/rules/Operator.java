package com.sensonet.rules;

/**
 * Operador de una regla con histéresis. Ejemplo GT: dispara si v &gt; 30 y se recupera si v &lt; 28.
 */
public enum Operator {
    GT(">") {
        @Override public boolean triggers(double v, double threshold) { return v > threshold; }
        @Override public boolean recovers(double v, double recovery) { return v < recovery; }
        @Override public boolean validHysteresis(double threshold, double recovery) { return recovery <= threshold; }
        @Override public boolean moreExtreme(double candidate, double current) { return candidate > current; }
    },
    LT("<") {
        @Override public boolean triggers(double v, double threshold) { return v < threshold; }
        @Override public boolean recovers(double v, double recovery) { return v > recovery; }
        @Override public boolean validHysteresis(double threshold, double recovery) { return recovery >= threshold; }
        @Override public boolean moreExtreme(double candidate, double current) { return candidate < current; }
    };

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public abstract boolean triggers(double v, double threshold);

    public abstract boolean recovers(double v, double recovery);

    /** El umbral de recuperación debe estar del lado "seguro" del umbral de disparo. */
    public abstract boolean validHysteresis(double threshold, double recovery);

    public abstract boolean moreExtreme(double candidate, double current);
}
