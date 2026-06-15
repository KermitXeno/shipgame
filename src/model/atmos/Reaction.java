package model.atmos;

/**
 * A single gas reaction as data: how many moles of each gas it consumes and produces per
 * unit of reaction extent, the heat it releases (positive) or absorbs (negative) per unit,
 * an ignition temperature, and how fast it runs. Add more entries to {@link Reactions} to
 * extend the chemistry; nothing else needs to know the specifics.
 */
public class Reaction {
    public final String name;
    public final double ignitionTemperature; // K, below this the reaction does not run
    public final double rate;                 // base extent per second at ignition
    public final double accel;                // extra extent per second per K above ignition
    public final double energyPerExtent;      // J released per unit extent (+ exothermic, - endothermic)
    private final double[] reactants;         // moles consumed per unit extent, indexed by Gas
    private final double[] products;          // moles produced per unit extent, indexed by Gas

    public Reaction(String name, double ignitionTemperature, double rate, double accel,
                    double energyPerExtent, double[] reactants, double[] products) {
        this.name = name;
        this.ignitionTemperature = ignitionTemperature;
        this.rate = rate;
        this.accel = accel;
        this.energyPerExtent = energyPerExtent;
        this.reactants = reactants;
        this.products = products;
    }

    public double reactant(Gas gas) {
        return reactants[gas.ordinal()];
    }

    public double product(Gas gas) {
        return products[gas.ordinal()];
    }
}
