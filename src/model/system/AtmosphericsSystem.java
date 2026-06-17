package model.system;

import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Room;

import java.util.List;

/**
 * Ship-wide life support. Nudges every room toward a target temperature and a target partial
 * pressure per gas (same pressure in every room). Scrubbed gas is pumped into the tank that
 * stores it unless that tank is near its pressure rating (then vented). Work is throttled to
 * the power the ship can afford this frame, so an empty bank does nothing.
 */
public class AtmosphericsSystem extends ShipSystem {
    private static final Dimension MIN = new Dimension(1, 2);
    private static final Dimension MAX = new Dimension(2, 2);

    private static final double DEFAULT_TARGET_TEMPERATURE = 293.15;
    private static final double DEFAULT_AIR_PRESSURE = 101.0;
    private static final double THERMAL_CONDUCTANCE_PER_TILE = 0.15; // heat-exchange rate: per-second fraction of the gap closed, per room tile
    private static final double SUPPLY_RATE = 30;
    private static final double POWER_PER_MOLE = 0.05;
    private static final double POWER_PER_JOULE_M3 = 0.008;  // power per Joule of heat moved (size-independent)

    private double targetTemperature = DEFAULT_TARGET_TEMPERATURE;
    private final double[] targetPressure = new double[Gas.values().length];
    private boolean scrubEnabled = true; // remove gas above its target (vent/route to tanks)
    private boolean pumpEnabled = true;  // add gas below its target (supply from reserves)
    private double powerRate;

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
        return (int) Math.round(powerRate);
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

    public void setTargetPerTile(Gas gas, double kpa) {
        targetPressure[gas.ordinal()] = Math.max(0, kpa);
    }

    public double getTargetPerTile(Gas gas) {
        return targetPressure[gas.ordinal()];
    }

    public boolean isScrubEnabled() {
        return scrubEnabled;
    }

    public void toggleScrub() {
        scrubEnabled = !scrubEnabled;
    }

    public boolean isPumpEnabled() {
        return pumpEnabled;
    }

    public void togglePump() {
        pumpEnabled = !pumpEnabled;
    }

    private double targetMoles(Gas gas, double volume) {
        return targetPressure[gas.ordinal()] * 1000.0 * volume / (GasMixture.R * targetTemperature);
    }

    public void regulate(List<Room> rooms, float dt) {
        double budget = ship() != null ? ship().getStoredEnergy() : 0;
        double spent = 0;
        for (Room room : rooms) {
            GasMixture gas = room.gas();
            double volume = room.volume();
            for (Gas g : Gas.values()) {
                double maxStep = SUPPLY_RATE * dt * room.tileCount();
                double step = clamp(targetMoles(g, volume) - gas.moles(g), -maxStep, maxStep);
                if (Math.abs(step) < 1e-9 || (step > 0 && !pumpEnabled) || (step < 0 && !scrubEnabled)) {
                    continue;
                }
                double cost = Math.abs(step) * POWER_PER_MOLE;
                double fraction = cost > 0 ? Math.min(1, budget / cost) : 1;
                step *= fraction;
                if (step > 0) {
                    gas.addGas(g, step, targetTemperature);
                } else {
                    double removed = gas.removeGas(g, -step);
                    routeToTank(g, removed, gas.temperature());
                }
                double drawn = cost * fraction;
                budget -= drawn;
                spent += drawn;
            }
            double cap = gas.heatCapacity();
            if (cap > 1e-6) {
                double delta = (targetTemperature * cap - gas.thermalEnergy()) * Math.min(1.0, THERMAL_CONDUCTANCE_PER_TILE * room.tileCount() * dt); // rate by conductance, throughput by gas present
                double cost = Math.abs(delta) * POWER_PER_JOULE_M3;
                double fraction = cost > 0 ? Math.min(1, budget / cost) : 1;
                gas.addHeat(delta * fraction);
                double drawn = cost * fraction;
                budget -= drawn;
                spent += drawn;
            }
        }
        int cost = (int) Math.round(spent);
        if (cost > 0 && ship() != null) {
            ship().drawEnergy(cost);
        }
        powerRate = dt > 0 ? -cost / dt : 0;
    }

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
