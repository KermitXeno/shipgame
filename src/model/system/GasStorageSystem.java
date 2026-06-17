package model.system;

import com.badlogic.gdx.utils.JsonValue;
import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Room;

/**
 * A single-gas tank. The gas it stores is fixed by the ship JSON. It trends its contents
 * toward a player-set target temperature, accepts more of its own gas from the scrubber until
 * near its pressure rating, and ruptures into its room if it or the room exceeds its ratings.
 */
public class GasStorageSystem extends ShipSystem {
    private static final int MAX_PER_SHIP = 8;
    private static final double TANK_VOLUME_PER_TILE = 0.5;
    private static final double CONDITION_CONDUCTANCE_PER_TILE = 0.12; // heat-exchange rate: per-second fraction of the gap closed, per tile (the J/s moved scales with the gas present)
    private static final double POWER_PER_JOULE = 0.0006; // power units per Joule of work done (independent of tank size)
    private static final double COOLING_BASELINE_WORK = 0.3; // work per Joule moved for active cooling even downhill; the Carnot lift is added on top when pumping below the room
    private static final double ACCEPT_PRESSURE_FRACTION = 0.90;
    private static final Dimension MIN = new Dimension(1, 1);
    private static final Dimension MAX = new Dimension(2, 2);

    private final GasMixture tank = new GasMixture();
    private Gas storedGas = Gas.AIR;
    private double targetTemperature = AMBIENT_TEMPERATURE;
    private double heatTolerance = 400;
    private double pressureTolerance = 50000;
    private double powerRate;

    @Override
    public String id() {
        return "gasstorage";
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
        return MAX_PER_SHIP;
    }

    @Override
    public SystemDecal decal() {
        return SystemDecal.color("TANK", 0.90f, 0.80f, 0.35f);
    }

    @Override
    public void configure(JsonValue entry) {
        double temperature = entry.getFloat("temperature", (float) AMBIENT_TEMPERATURE);
        targetTemperature = entry.getFloat("targetTemperature", (float) temperature);
        heatTolerance = entry.getFloat("heatTolerance", (float) heatTolerance);
        pressureTolerance = entry.getFloat("pressureTolerance", (float) pressureTolerance);
        JsonValue store = entry.get("store");
        if (store != null && store.child != null) {
            Gas gas = parseGas(store.child.name());
            if (gas != null) {
                storedGas = gas;
                tank.addGas(gas, store.child.asDouble(), temperature);
            }
        }
    }

    public GasMixture tank() {
        return tank;
    }

    public Gas storedGas() {
        return storedGas;
    }

    public double tankVolume() {
        return Math.max(1, tileCount()) * TANK_VOLUME_PER_TILE;
    }

    public double tankPressure() {
        return tank.pressure(tankVolume()) / 1000.0;
    }

    public double getTargetTemperature() {
        return targetTemperature;
    }

    public void setTargetTemperature(double kelvin) {
        targetTemperature = Math.max(0, kelvin);
    }

    public double getHeatTolerance() {
        return heatTolerance;
    }

    public double getPressureTolerance() {
        return pressureTolerance;
    }

    public double getPowerRate() {
        return powerRate;
    }

    @Override
    public int generationPerTick() {
        return (int) Math.round(powerRate);
    }

    public double accept(Gas gas, double moles, double temperature) {
        if (gas != storedGas || moles <= 0) {
            return 0;
        }
        if (tankPressure() >= ACCEPT_PRESSURE_FRACTION * pressureTolerance) {
            return 0;
        }
        tank.addGas(gas, moles, temperature);
        return moles;
    }

    public void operate(Room room, float dt) {
        conditionTowardTarget(room, dt);
        boolean ownOver = tank.temperature() > heatTolerance || tankPressure() > pressureTolerance;
        boolean roomOver = room.temperature() > heatTolerance || room.pressure() / 1000.0 > pressureTolerance;
        if (ownOver || roomOver) {
            rupture(room);
        }
    }

    private void conditionTowardTarget(Room room, float dt) {
        double cap = tank.heatCapacity();
        if (cap < 1e-6) {
            powerRate = 0;
            return;
        }
        double deltaEnergy = targetTemperature * cap - tank.thermalEnergy(); // J to reach target; magnitude scales with the gas present (cap = moles * specific heat)
        double want = deltaEnergy * Math.min(1.0, CONDITION_CONDUCTANCE_PER_TILE * tileCount() * dt); // Newtonian: rate set by conductance, throughput by how much gas is there
        if (Math.abs(want) < 1e-6) {
            powerRate = 0;
            return;
        }
        double work; // electrical work this step
        double reject = 0; // heat dumped to the room (cooling only)
        if (want >= 0) {
            work = want; // resistive heating: 1 J of work becomes 1 J of heat in the gas
        } else {
            double qc = -want; // heat pulled out of the tank gas
            double tCold = tank.temperature();
            double tHot = room.gas().heatCapacity() > 1e-6 ? room.temperature() : tCold; // rejection sink is the room
            double lift = tCold > 1 ? Math.max(0, tHot - tCold) / tCold : 0; // ideal heat-pump work per Joule, only when pumping uphill
            work = qc * (COOLING_BASELINE_WORK + lift); // active-cooling baseline plus the Carnot penalty for sub-room temperatures
            reject = qc + work; // the hot side receives the pumped heat plus all the work
        }
        int cost = (int) Math.round(work * POWER_PER_JOULE);
        int paid = ship() != null ? ship().drawEnergy(cost) : 0;
        double fraction = cost > 0 ? (double) paid / cost : 1.0;
        tank.addHeat(want * fraction);
        if (want < 0 && room.gas().heatCapacity() > 1e-6) {
            room.gas().addHeat(reject * fraction); // cooling rejects the heat and the work into the room
        }
        powerRate = dt > 0 ? -paid / dt : 0;
    }

    private void rupture(Room room) {
        double t = tank.temperature();
        for (Gas gas : Gas.values()) {
            double n = tank.moles(gas);
            if (n > 0) {
                room.gas().addGas(gas, n, t);
                tank.removeGas(gas, n);
            }
        }
    }

    private static double clamp(double value, double lo, double hi) {
        return value < lo ? lo : Math.min(value, hi);
    }

    private static Gas parseGas(String name) {
        try {
            return Gas.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
