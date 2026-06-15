package model.atmos;

/**
 * A quantity of gas: moles of each {@link Gas} plus stored thermal energy (Joules).
 * Temperature is derived from energy and heat capacity, pressure from the ideal gas
 * law (nRT/V). All transfers conserve moles and energy.
 */
public class GasMixture {
    public static final double R = 8.314;          // ideal gas constant, J/(mol*K)
    public static final double TILE_VOLUME = 2.5;  // m^3 of gas volume per tile

    private final double[] moles = new double[Gas.values().length];
    private double thermalEnergy;                  // Joules

    public double moles(Gas gas) {
        return moles[gas.ordinal()];
    }

    public double totalMoles() {
        double total = 0;
        for (double m : moles) {
            total += m;
        }
        return total;
    }

    /** Sum of moles x specific heat across all gases (J/K). */
    public double heatCapacity() {
        double c = 0;
        Gas[] gases = Gas.values();
        for (int i = 0; i < moles.length; i++) {
            c += moles[i] * gases[i].specificHeat;
        }
        return c;
    }

    public double temperature() {
        double c = heatCapacity();
        return c > 1e-9 ? thermalEnergy / c : 0;
    }

    public double pressure(double volume) {
        return volume > 0 ? totalMoles() * R * temperature() / volume : 0;
    }

    public double thermalEnergy() {
        return thermalEnergy;
    }

    /** Adds (or removes, if negative) energy; never lets the bank go below zero. */
    public void addHeat(double joules) {
        thermalEnergy = Math.max(0, thermalEnergy + joules);
    }

    /** Adds n moles already at temperature t, carrying their thermal energy in. */
    public void addGas(Gas gas, double n, double t) {
        if (n <= 0) {
            return;
        }
        moles[gas.ordinal()] += n;
        thermalEnergy += n * gas.specificHeat * t;
    }

    /** Removes up to n moles, carrying the proportional thermal energy out. Returns moles removed. */
    public double removeGas(Gas gas, double n) {
        double take = Math.min(Math.max(0, n), moles[gas.ordinal()]);
        if (take <= 0) {
            return 0;
        }
        thermalEnergy = Math.max(0, thermalEnergy - take * gas.specificHeat * temperature());
        moles[gas.ordinal()] -= take;
        return take;
    }

    /** Moves moles only (no energy); used by room-to-room flow which moves energy separately. */
    public void addMolesRaw(Gas gas, double n) {
        moles[gas.ordinal()] = Math.max(0, moles[gas.ordinal()] + n);
    }

    public boolean isEmpty() {
        return totalMoles() < 1e-9;
    }
}
