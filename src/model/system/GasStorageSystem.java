package model.system;

import com.badlogic.gdx.utils.JsonValue;
import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Room;

/**
 * A single-gas tank. The gas it stores is fixed by the ship JSON (`store` names exactly one
 * gas and its amount). It trends its contents toward a player-set target temperature, heating
 * or cooling as needed; that work draws ship power scaled by the energy moved and the tank's
 * volume, and is throttled to whatever power the ship can actually afford. It accepts more of
 * its own gas from the atmospherics scrubber unless it is already near its pressure rating, and
 * ruptures all contents into its room if its own gas — or the room around it — exceeds its
 * rated heat or pressure. The heat/pressure ratings themselves are fixed, not player-editable.
 */
public class GasStorageSystem extends ShipSystem {
    private static final int MAX_PER_SHIP = 8;
    private static final double TANK_VOLUME_PER_TILE = 0.5;   // m^3 per tile (small -> high pressure)
    private static final double CONDITION_RATE_PER_TILE = 12000; // J/s of heating or cooling per tile
    private static final double POWER_PER_JOULE_M3 = 0.0006;   // power per (Joule * m^3) conditioned
    private static final double ACCEPT_PRESSURE_FRACTION = 0.90; // stop accepting at 90% of rated pressure
    private static final Dimension MIN = new Dimension(1, 1);
    private static final Dimension MAX = new Dimension(2, 2);

    private final GasMixture tank = new GasMixture();
    private Gas storedGas = Gas.AIR;          // set from JSON; the only gas this tank holds
    private double targetTemperature = AMBIENT_TEMPERATURE; // player-set; trends the gas toward it
    private double heatTolerance = 600;       // K  (fixed rating)
    private double pressureTolerance = 50000;  // kPa (fixed rating)
    private double powerRate;                 // power units/sec drawn (negative), for the UI/helm

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
            Gas gas = parseGas(store.child.name()); // first (and only) entry defines the tank's gas
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
        return tank.pressure(tankVolume()) / 1000.0; // kPa
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
        return (int) Math.round(powerRate); // negative: tanks consume power to condition gas
    }

    /** Takes more of its own gas from the scrubber, unless already near its pressure rating. */
    public double accept(Gas gas, double moles, double temperature) {
        if (gas != storedGas || moles <= 0) {
            return 0;
        }
        if (tankPressure() >= ACCEPT_PRESSURE_FRACTION * pressureTolerance) {
            return 0; // full: let the caller vent the excess
        }
        tank.addGas(gas, moles, temperature);
        return moles;
    }

    /** Conditions the gas toward the target temperature, then ruptures if itself or its room is over-rated. */
    public void operate(Room room, float dt) {
        conditionTowardTarget(dt);
        boolean ownOver = tank.temperature() > heatTolerance || tankPressure() > pressureTolerance;
        boolean roomOver = room.temperature() > heatTolerance || room.pressure() / 1000.0 > pressureTolerance;
        if (ownOver || roomOver) {
            rupture(room);
        }
    }

    private void conditionTowardTarget(float dt) {
        double cap = tank.heatCapacity();
        if (cap < 1e-6) {
            powerRate = 0;
            return;
        }
        double deltaEnergy = targetTemperature * cap - tank.thermalEnergy(); // + heat, - cool
        double want = clamp(deltaEnergy, -tileCount() * CONDITION_RATE_PER_TILE * dt,
                tileCount() * CONDITION_RATE_PER_TILE * dt);
        if (Math.abs(want) < 1e-6) {
            powerRate = 0;
            return;
        }
        int cost = (int) Math.round(Math.abs(want) * POWER_PER_JOULE_M3 * tankVolume());
        int paid = ship() != null ? ship().drawEnergy(cost) : 0;
        double fraction = cost > 0 ? (double) paid / cost : 1.0; // tiny costs are free
        tank.addHeat(want * fraction);
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
