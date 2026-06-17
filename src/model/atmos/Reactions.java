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

    private static final double IGNITION = 300;     // K, below which the reaction is dormant
    private static final double RATE = 0.009;         // mass-action rate constant (a property of the reaction)
    private static final double ACCEL = 0.0;         // temperature sensitivity of the rate constant (per K above ignition)
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

    /**
     * Runs every reaction once over {@code dt} in a gas of the given {@code volume} (m^3).
     * Rate follows mass action: it is proportional to the product of reactant concentrations
     * (moles / volume), so a denser, higher-pressure mixture reacts faster and a thin one barely
     * reacts. Consumes reactants, makes products and releases heat.
     */
    public static void react(GasMixture gas, double volume, double dt) {
        if (volume <= 1e-9) {
            return;
        }
        for (Reaction reaction : ALL) {
            double t = gas.temperature();
            if (t < reaction.ignitionTemperature) {
                continue;
            }
            double k = reaction.rate + reaction.accel * (t - reaction.ignitionTemperature);
            double concentrationProduct = 1.0;
            for (Gas g : Gas.values()) {
                if (reaction.reactant(g) > 0) {
                    concentrationProduct *= gas.moles(g) / volume; // mol/m^3 per reactant species
                }
            }
            double extent = k * concentrationProduct * volume * dt;
            for (Gas g : Gas.values()) {
                double need = reaction.reactant(g);
                if (need > 0) {
                    extent = Math.min(extent, gas.moles(g) / need); // can't consume more reactant than is present
                }
            }
            if (extent <= 0) {
                continue;
            }
            for (Gas g : Gas.values()) {
                if (reaction.reactant(g) > 0) {
                    gas.addMolesRaw(g, -extent * reaction.reactant(g)); // moles only -- the atoms' sensible heat stays in the mixture
                }
            }
            for (Gas g : Gas.values()) {
                if (reaction.product(g) > 0) {
                    gas.addMolesRaw(g, extent * reaction.product(g)); // moles only; the new gas shares the mixture temperature
                }
            }
            gas.addHeat(extent * reaction.energyPerExtent); // only the released bond energy enters as heat
        }
    }
}
