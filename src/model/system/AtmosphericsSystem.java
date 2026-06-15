package model.system;

import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Room;

import java.util.List;

/**
 * Ship-wide life support. Each frame it nudges every room toward a player-set target
 * temperature and a target partial pressure per gas (the same pressure in every room, so a
 * bigger room takes proportionally more gas). Gases above target are scrubbed; the scrubbed
 * gas is pumped into a tank that stores that gas, unless that tank is near its pressure rating,
 * in which case the excess is vented. All of this draws ship power — gas moves cost per mole,
 * heating/cooling costs per Joule scaled by room volume — and the work is throttled to whatever
 * power the ship can actually afford this frame.
 */
public class AtmosphericsSystem extends ShipSystem {
    private static final Dimension MIN = new Dimension(1, 2);
    private static final Dimension MAX = new Dimension(2, 2);

    private static final double DEFAULT_TARGET_TEMPERATURE = 293.15; // 20 C
    private static final double DEFAULT_AIR_PRESSURE = 101.0;        // kPa (~1 atm)
    private static final double THERMAL_RATE = 4000;                 // J/s of heating/cooling per room
    private static final double SUPPLY_RATE = 10;                    // mol/s per tile it can add or scrub
    private static final double POWER_PER_MOLE = 0.05;               // power cost per mole displaced
    private static final double POWER_PER_JOULE_M3 = 0.0016;         // power per (Joule * m^3) conditioned

    private double targetTemperature = DEFAULT_TARGET_TEMPERATURE;
    private final double[] targetPressure = new double[Gas.values().length]; // target partial pressure (kPa)
    private double powerRate; // power units/sec drawn (negative), for the UI/helm

    public AtmosphericsSystem() {
        targetPressure[Gas.AIR.ordinal()] = DEFAULT_AIR_PRESSURE;
    }

    @Override
    public String id() {
        return "atmospherics";
    }

    @Override
    public Dimension minDimension() {
        return MIN;
    }

    @Override
    public Dimension maxDimension() {
        return MAX;
    }

    @Override
    public int maxPerShip() {
        return 1;
    }

    @Override
    public SystemDecal decal() {
        return SystemDecal.color("ATMOS", 0.40f, 0.85f, 0.85f);
    }

    @Override
    public int generationPerTick() {
        return (int) Math.round(powerRate); // negative: atmospherics consumes power
    }

    public double getPowerRate() {
        return powerRate;
    }

    public void setTargetTemperature(double kelvin) {
        targetTemperature = Math.max(0, kelvin);
    }

    public double getTargetTemperature() {
        return targetTemperature;
    }

    /** Target partial pressure (kPa) for a gas; the mix bar edits these. */
    public void setTargetPerTile(Gas gas, double kpa) {
        targetPressure[gas.ordinal()] = Math.max(0, kpa);
    }

    public double getTargetPerTile(Gas gas) {
        return targetPressure[gas.ordinal()];
    }

    /** Moles that give the target partial pressure in a room of the given volume and target temp. */
    private double targetMoles(Gas gas, double volume) {
        return targetPressure[gas.ordinal()] * 1000.0 * volume / (GasMixture.R * targetTemperature);
    }

    /** Moves every room toward its target mix and temperature, spending only affordable power. */
    public void regulate(List<Room> rooms, float dt) {
        double budget = ship() != null ? ship().getStoredEnergy() : 0; // power affordable this frame
        double spent = 0;

        for (Room room : rooms) {
            GasMixture gas = room.gas();
            double volume = room.volume();
            for (Gas g : Gas.values()) {
                double maxStep = SUPPLY_RATE * dt * room.tileCount();
                double step = clamp(targetMoles(g, volume) - gas.moles(g), -maxStep, maxStep);
                if (Math.abs(step) < 1e-9) {
                    continue;
                }
                double cost = Math.abs(step) * POWER_PER_MOLE;
                double fraction = cost > 0 ? Math.min(1, budget / cost) : 1;
                step *= fraction;
                if (step > 0) {
                    gas.addGas(g, step, targetTemperature);
                } else {
                    double removed = gas.removeGas(g, -step);
                    double accepted = routeToTank(g, removed, gas.temperature());
                    // (removed - accepted) is vented to space
                }
                double used = cost * fraction;
                budget -= used;
                spent += used;
            }

            double cap = gas.heatCapacity();
            if (cap > 1e-6) {
                double delta = clamp(targetTemperature * cap - gas.thermalEnergy(),
                        -THERMAL_RATE * dt, THERMAL_RATE * dt);
                double cost = Math.abs(delta) * POWER_PER_JOULE_M3 * volume;
                double fraction = cost > 0 ? Math.min(1, budget / cost) : 1;
                gas.addHeat(delta * fraction);
                double used = cost * fraction;
                budget -= used;
                spent += used;
            }
        }

        int cost = (int) Math.round(spent);
        if (cost > 0 && ship() != null) {
            ship().drawEnergy(cost);
        }
        powerRate = dt > 0 ? -cost / dt : 0;
    }

    /** Pumps scrubbed gas into the tank that stores it; returns how much the tank took. */
    private double routeToTank(Gas gas, double moles, double temperature) {
        if (moles <= 0 || ship() == null) {
            return 0;
        }
        for (ShipSystem system : ship().getSystems()) {
            if (system instanceof GasStorageSystem tank && tank.storedGas() == gas) {
                return tank.accept(gas, moles, temperature);
            }
        }
        return 0;
    }

    private static double clamp(double value, double lo, double hi) {
        return value < lo ? lo : Math.min(value, hi);
    }
}
