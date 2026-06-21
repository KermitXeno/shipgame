package model.combat;

/**
 * Armor damage model: a mix of flat resist and percent bleed that scales toward extremes.
 * damage = max(LEAK_FRAC*raw, (raw - flat) * (1 - pct)), flat = A^EXP, pct = A/(A+K).
 */
public final class Armor {
    private static final double FLAT_EXP = 1.6;   // super-linear flat resist (1 armor ~ 1 resist at A=1)
    private static final double PCT_K = 120;       // controls how fast the percent bleed ramps
    private static final double LEAK_FRAC = 0.02;  // minimum fraction of a shot that always lands

    private Armor() {
    }

    /** Damage a shot of {@code raw} energy deals through {@code armor} points. */
    public static double damage(double raw, double armor) {
        if (raw <= 0) {
            return 0;
        }
        if (armor <= 0) {
            return raw;
        }
        double flat = Math.pow(armor, FLAT_EXP);
        double pct = armor / (armor + PCT_K);
        double dealt = (raw - flat) * (1 - pct);
        return Math.max(LEAK_FRAC * raw, dealt);
    }
}
