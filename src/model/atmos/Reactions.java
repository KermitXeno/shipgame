package model.atmos;

import java.util.ArrayList;
import java.util.List;

/**
 * The reaction table and the routine that applies it to a {@link GasMixture}. Each entry is
 * a {@link Reaction}; add rows here to add chemistry. Currently: combustion of phlogiston
 * with air into CO2, releasing heat (the basis for fire).
 */
public final class Reactions {
    public static final List<Reaction> ALL = new ArrayList<>();

    private static final double IGNITION = 300;     // K
    private static final double RATE = 20.0;         // base extent/sec at ignition
    private static final double ACCEL = 0.0;       // extra extent/sec per K above ignition
    private static final double ENERGY = 300000;    // J released per extent (exothermic)

    static {
        double[] reactants = new double[Gas.values().length];
        reactants[Gas.PHLOGISTON.ordinal()] = 1; // 1 phlog + 1 air ...
        reactants[Gas.AIR.ordinal()] = 1;
        double[] products = new double[Gas.values().length];
        products[Gas.CO2.ordinal()] = 1;          // ... -> 1 CO2 + heat
        ALL.add(new Reaction("combustion", IGNITION, RATE, ACCEL, ENERGY, reactants, products));
    }

    private Reactions() {
    }

    /** Runs every reaction once over {@code dt}, consuming reactants, making products and releasing heat. */
    public static void react(GasMixture gas, double dt) {
        for (Reaction reaction : ALL) {
            double t = gas.temperature();
            if (t < reaction.ignitionTemperature) {
                continue;
            }
            double extent = (reaction.rate + reaction.accel * (t - reaction.ignitionTemperature)) * dt;
            for (Gas g : Gas.values()) {
                double need = reaction.reactant(g);
                if (need > 0) {
                    extent = Math.min(extent, gas.moles(g) / need); // limited by scarcest reactant
                }
            }
            if (extent <= 0) {
                continue;
            }
            for (Gas g : Gas.values()) {
                if (reaction.reactant(g) > 0) {
                    gas.removeGas(g, extent * reaction.reactant(g));
                }
            }
            for (Gas g : Gas.values()) {
                if (reaction.product(g) > 0) {
                    gas.addGas(g, extent * reaction.product(g), t);
                }
            }
            gas.addHeat(extent * reaction.energyPerExtent);
        }
    }
}
