package model.system;

import com.badlogic.gdx.utils.JsonValue;
import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Reactions;
import model.atmos.Room;

/**
 * A reaction-chamber engine. It holds its own gas chamber and fills/vents it toward a
 * player-set target partial pressure per gas (pulled from the ship's tanks). It warms the
 * chamber up to a player-set heating temperature (costing ship power) and, once the reaction
 * pushes it past the cooling temperature, harvests the excess heat back into ship power
 * (which also cools the chamber, heading off a blowout). A vigorous reaction therefore nets
 * positive power. It is insulated from its room except when the chamber passes its rated heat
 * (waste heat bleeds in); it blows out, dumping all contents into the room, if chamber
 * pressure passes its rated pressure or if the room itself exceeds the engine's rated limits.
 */
public class EngineSystem extends ShipSystem {
    private static final int MAX_PER_SHIP = 8;
    private static final double CHAMBER_VOLUME_PER_TILE = 0.5;  // m^3 (small -> pressure builds)
    private static final double PILOT_HEAT_PER_TILE = 4000;     // J/s free bootstrap heat
    private static final double HEAT_POWER_PER_TILE = 40000;    // J/s max powered heating
    private static final double HARVEST_POWER_PER_TILE = 1.0e9; // effectively uncapped: chamber is pinned at the cooling setpoint
    private static final double INTAKE_PER_TILE = 4;           // mol/s pulled from tanks
    private static final double CHAMBER_ROOM_CONDUCTIVITY = 2000;
    private static final double POWER_PER_JOULE_IN = 0.006;     // power cost to add 1 J of heat
    private static final double HEAT_TO_POWER = 0.0009;    // power per J harvested (before efficiency)
    private static final double MAX_EFFICIENCY = 0.9;
    private static final Dimension MIN = new Dimension(1, 2);
    private static final Dimension MAX = new Dimension(2, 2);

    private final GasMixture chamber = new GasMixture();
    private final double[] targetPressure = new double[Gas.values().length]; // desired partial pressure (kPa)
    private double heatingTemperature = 1200;  // warm the chamber up to this (costs power)
    private double coolingTemperature = 1500;  // above this, harvest heat into power (cools the chamber)
    private double heatThreshold = 2200;      // rated chamber K above which waste heat enters the room
    private double pressureThreshold = 4000;  // rated kPa above which the chamber blows out
    private boolean ventToSpace = false;      // false: exhaust spent gas into the room; true: dump to space
    private double powerRate;                 // net power units/sec (+gen, -consume), for the UI/helm
    private double powerAccumulator;

    public EngineSystem() {
        targetPressure[Gas.AIR.ordinal()] = 750;       // ~150 mol air at the heating temp
        targetPressure[Gas.PHLOGISTON.ordinal()] = 400; // ~80 mol phlogiston
    }

    @Override
    public String id() {
        return "engine";
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
        return SystemDecal.color("ENGINE", 0.95f, 0.55f, 0.25f);
    }

    @Override
    public void configure(JsonValue entry) {
        heatingTemperature = entry.getFloat("heatingTemperature", (float) heatingTemperature);
        coolingTemperature = entry.getFloat("coolingTemperature", (float) coolingTemperature);
        heatThreshold = entry.getFloat("heatThreshold", (float) heatThreshold);
        pressureThreshold = entry.getFloat("pressureThreshold", (float) pressureThreshold);
        ventToSpace = entry.getBoolean("ventToSpace", ventToSpace);
        JsonValue mix = entry.get("mix"); // values are target partial pressures (kPa)
        if (mix != null) {
            for (JsonValue child = mix.child; child != null; child = child.next) {
                Gas gas = parseGas(child.name());
                if (gas != null) {
                    targetPressure[gas.ordinal()] = child.asDouble();
                }
            }
        }
    }

    private static Gas parseGas(String name) {
        try {
            return Gas.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int generationPerTick() {
        return (int) Math.round(powerRate);
    }

    public double getPowerRate() {
        return powerRate;
    }

    public GasMixture chamber() {
        return chamber;
    }

    public double chamberVolume() {
        return Math.max(1, tileCount()) * CHAMBER_VOLUME_PER_TILE;
    }

    public double chamberPressure() {
        return chamber.pressure(chamberVolume()) / 1000.0; // kPa
    }

    public double getHeatingTemperature() {
        return heatingTemperature;
    }

    public void setHeatingTemperature(double kelvin) {
        heatingTemperature = Math.max(0, kelvin);
    }

    public double getCoolingTemperature() {
        return coolingTemperature;
    }

    public void setCoolingTemperature(double kelvin) {
        coolingTemperature = Math.max(0, kelvin);
    }

    /** Target partial pressure (kPa) for a gas; the mix bar edits these. */
    public double getTargetMix(Gas gas) {
        return targetPressure[gas.ordinal()];
    }

    public void setTargetMix(Gas gas, double kpa) {
        targetPressure[gas.ordinal()] = Math.max(0, kpa);
    }

    public double getHeatThreshold() {
        return heatThreshold;
    }

    public void setHeatThreshold(double kelvin) {
        heatThreshold = Math.max(0, kelvin);
    }

    public double getPressureThreshold() {
        return pressureThreshold;
    }

    public void setPressureThreshold(double kpa) {
        pressureThreshold = Math.max(0, kpa);
    }

    public boolean isVentToSpace() {
        return ventToSpace;
    }

    public void toggleVentToSpace() {
        ventToSpace = !ventToSpace;
    }

    /** Target moles for a gas to reach its target pressure at the engine's operating (cooling) temperature. */
    private double targetMoles(Gas gas) {
        return targetPressure[gas.ordinal()] * 1000.0 * chamberVolume() / (GasMixture.R * Math.max(1, coolingTemperature));
    }

    /** One simulation step: intake, react, exhaust spent gas, regulate, leak/blow out. */
    public void operate(Room room, float dt) {
        intakeFromTanks(dt);
        chamber.addHeat(tileCount() * PILOT_HEAT_PER_TILE * dt); // free pilot for bootstrap ignition
        Reactions.react(chamber, dt);
        exhaust(dt);
        regulateTemperature(dt);
        leakToRoom(room, dt);
        if (chamberPressure() > pressureThreshold
                || room.temperature() > heatThreshold
                || room.pressure() / 1000.0 > pressureThreshold) {
            blowOut(room);
        }
    }

    /** Vents gas above its target partial pressure: into the matching tank if any (else space), or always space. */
    private void exhaust(float dt) {
        double budget = tileCount() * INTAKE_PER_TILE * dt;
        for (Gas gas : Gas.values()) {
            double excess = chamber.moles(gas) - targetMoles(gas);
            if (excess > 0) {
                double vented = chamber.removeGas(gas, Math.min(excess, budget));
                if (!ventToSpace) {
                    routeToTank(gas, vented); // whatever a tank won't take is vented to space
                }
            }
        }
    }

    /** Pushes cooled exhaust into a tank that stores this gas; returns how much it took. */
    private double routeToTank(Gas gas, double moles) {
        if (moles <= 0 || ship() == null) {
            return 0;
        }
        for (ShipSystem system : ship().getSystems()) {
            if (system instanceof GasStorageSystem tank && tank.storedGas() == gas) {
                return tank.accept(gas, moles, AMBIENT_TEMPERATURE);
            }
        }
        return 0;
    }

    /** Pulls each target gas from any tank that holds it, up to the per-frame intake budget. */
    private void intakeFromTanks(float dt) {
        if (ship() == null) {
            return;
        }
        double budget = tileCount() * INTAKE_PER_TILE * dt;
        for (Gas gas : Gas.values()) {
            double deficit = targetMoles(gas) - chamber.moles(gas);
            if (deficit <= 0) {
                continue;
            }
            double want = Math.min(deficit, budget);
            for (ShipSystem system : ship().getSystems()) {
                if (want <= 0) {
                    break;
                }
                if (system instanceof GasStorageSystem tank && tank.tank().moles(gas) > 0) {
                    double pulled = tank.tank().removeGas(gas, want);
                    chamber.addGas(gas, pulled, tank.tank().temperature());
                    want -= pulled;
                }
            }
        }
    }

    /** Warms toward the heating temp (costs power); above the cooling temp, harvests heat into power. */
    private void regulateTemperature(float dt) {
        double cap = chamber.heatCapacity();
        if (cap < 1e-6) {
            powerRate = 0;
            return;
        }
        double t = chamber.temperature();
        if (t < heatingTemperature) {
            double deltaEnergy = (heatingTemperature - t) * cap;
            double want = Math.min(deltaEnergy, tileCount() * HEAT_POWER_PER_TILE * dt);
            int cost = (int) Math.round(want * POWER_PER_JOULE_IN);
            int paid = ship() != null ? ship().drawEnergy(cost) : 0;
            double applied = cost > 0 ? want * ((double) paid / cost) : want; // tiny costs are free
            chamber.addHeat(applied);
            powerRate = dt > 0 ? -paid / dt : 0;
        } else if (t > coolingTemperature) {
            double excessEnergy = (t - coolingTemperature) * cap;
            double removable = Math.min(excessEnergy, tileCount() * HARVEST_POWER_PER_TILE * dt);
            double efficiency = Math.min(MAX_EFFICIENCY, 1.0 - AMBIENT_TEMPERATURE / t);
            chamber.addHeat(-removable);
            double power = removable * efficiency * HEAT_TO_POWER;
            powerAccumulator += power;
            int whole = (int) powerAccumulator;
            if (whole > 0 && ship() != null) {
                ship().addEnergy(whole);
                powerAccumulator -= whole;
            }
            powerRate = dt > 0 ? power / dt : 0;
        } else {
            powerRate = 0; // within the dead-band: neither heating nor harvesting
        }
    }

    /** Only an over-rated-heat chamber warms the room; below that the chamber is insulated. */
    private void leakToRoom(Room room, float dt) {
        double tC = chamber.temperature();
        if (tC <= heatThreshold || room.gas().heatCapacity() < 1e-6) {
            return;
        }
        double q = CHAMBER_ROOM_CONDUCTIVITY * (tC - heatThreshold) * dt;
        q = Math.min(q, (tC - heatThreshold) * chamber.heatCapacity()); // don't dip below the threshold
        chamber.addHeat(-q);
        room.gas().addHeat(q);
    }

    /** Blowout: empties the whole chamber into the room at once. */
    private void blowOut(Room room) {
        double t = chamber.temperature();
        for (Gas gas : Gas.values()) {
            double n = chamber.moles(gas);
            if (n > 0) {
                room.gas().addGas(gas, n, t);
                chamber.removeGas(gas, n);
            }
        }
    }
}
