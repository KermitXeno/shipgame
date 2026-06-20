package model.system;

import com.badlogic.gdx.utils.JsonValue;
import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Reactions;
import model.atmos.Room;

/**
 * A reaction-chamber engine, modelled as a heat engine. It fills/vents its chamber toward a
 * player-set target partial pressure per gas (pulled from the ship's tanks), runs the reaction
 * inside, and exhausts the spent products into a matching tank (or to space). The chamber runs
 * at whatever temperature the reaction drives it to — there is no operating-temperature knob.
 * Power comes from cooling: each tick the engine draws heat from the hot chamber toward its
 * fixed cold-side rating ("rated cooling") and converts it to ship power at Carnot efficiency
 * (1 - T_cold / T_hot), so a hotter reaction is both hotter and more efficient — more power.
 * The engine is insulated from its room until the chamber passes its rated heat (waste heat
 * then bleeds in), and it blows out, dumping the chamber into the room, if chamber pressure
 * passes its rated pressure or the room itself exceeds the engine's rated limits.
 */
public class EngineSystem extends ShipSystem {
    private static final int MAX_PER_SHIP = 8;
    private static final double CHAMBER_VOLUME_PER_TILE = 0.5;        // m^3 (small -> pressure builds)
    private static final double PILOT_HEAT_PER_TILE = 4000;          // J/s igniter heat, only while lighting a cold charge (not free once running)
    private static final double INTAKE_CONDUCTANCE_PER_TILE = 1.5;     // intake valve size: fraction of the partial-pressure deficit admitted per second, per tile
    private static final double VENT_CONDUCTANCE_PER_TILE = 3;       // exhaust valve size: fraction of the partial-pressure excess vented per second, per tile
    private static final double COOLING_CONDUCTANCE_PER_MOLE = 16; // J/s per K of temperature gap, per mole of chamber gas
    private static final double COOLING_FLOOR = 600;                 // below this the heat exchanger can't draw work (lets a cold reaction establish)
    private static final double GENERATOR_GAIN = 0.00087;             // power units per Joule cooled (before Carnot)
    private static final double WASTE_HEAT_TO_ROOM = 0.05;            // fraction of rejected heat that leaks into the engine's room
    private static final double CHAMBER_ROOM_CONDUCTIVITY = 2000;
    private static final double MAX_EFFICIENCY = 0.95;
    private static final Dimension MIN = new Dimension(1, 2);
    private static final Dimension MAX = new Dimension(2, 2);

    private final GasMixture chamber = new GasMixture();
    private final double[] targetPressure = new double[Gas.values().length]; // desired partial pressure (kPa) -- the player's throttle
    private double coldSinkTemperature = 290;  // rated cooling: the cold side heat is rejected to, sets the efficiency ceiling
    private double heatThreshold = 2200;       // rated chamber K above which waste heat enters the room
    private double pressureThreshold = 4000;   // rated kPa above which the chamber blows out
    private boolean ventToSpace = false;       // false: exhaust spent gas into the matching tank; true: dump to space
    private boolean pumpEnabled = true;        // false: stop drawing fuel so the chamber burns out (emergency shutoff)
    private boolean ignitionEnabled = true;    // false: igniter off, so fuel can accumulate cold and the reaction is delayed
    private double powerRate;                  // power units/sec generated, for the UI/helm
    private double powerAccumulator;

    public EngineSystem() {
        targetPressure[Gas.AIR.ordinal()] = 200;
        targetPressure[Gas.PHLOGISTON.ordinal()] = 110;
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
        coldSinkTemperature = entry.getFloat("ratedCooling", (float) coldSinkTemperature);
        heatThreshold = entry.getFloat("heatThreshold", (float) heatThreshold);
        pressureThreshold = entry.getFloat("pressureThreshold", (float) pressureThreshold);
        ventToSpace = entry.getBoolean("ventToSpace", ventToSpace);
        pumpEnabled = entry.getBoolean("pumpEnabled", pumpEnabled);
        ignitionEnabled = entry.getBoolean("ignitionEnabled", ignitionEnabled);
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

    /** Target partial pressure (kPa) for a gas; the mix bar edits these. */
    public double getTargetMix(Gas gas) {
        return targetPressure[gas.ordinal()];
    }

    public void setTargetMix(Gas gas, double kpa) {
        targetPressure[gas.ordinal()] = Math.max(0, kpa);
    }

    /** Rated cooling: the cold-side temperature heat is rejected to. Fixed per reactor; sets the efficiency ceiling. */
    public double getRatedCooling() {
        return coldSinkTemperature;
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

    public boolean isPumpEnabled() {
        return pumpEnabled;
    }

    public void togglePump() {
        pumpEnabled = !pumpEnabled;
    }

    public boolean isIgnitionEnabled() {
        return ignitionEnabled;
    }

    public void toggleIgnition() {
        ignitionEnabled = !ignitionEnabled;
    }

    /** Fuel moles for a gas to reach its target partial pressure at the chamber's actual temperature (true pressure regulation). */
    private double targetMoles(Gas gas) {
        double t = Math.max(chamber.temperature(), AMBIENT_TEMPERATURE);
        return targetPressure[gas.ordinal()] * 1000.0 * chamberVolume() / (GasMixture.R * t);
    }

    /** One simulation step: intake, react, exhaust spent gas, cool for power, leak/blow out. */
    public void operate(Room room, float dt) {
        if (pumpEnabled) {
            intakeFromTanks(dt); // off: no new fuel, so the existing charge reacts and burns out
        }
        if (ignitionEnabled && Reactions.needsIgnition(chamber)) {
            chamber.addHeat(tileCount() * PILOT_HEAT_PER_TILE * dt); // igniter only sparks a cold charge that has fuel; off once lit or when empty
        }
        Reactions.react(chamber, chamberVolume(), dt);
        coolForPower(room, dt);
        exhaust(room, dt);
        leakToRoom(room, dt);
        if (chamberPressure() > pressureThreshold
                || room.temperature() > heatThreshold
                || room.pressure() / 1000.0 > pressureThreshold) {
            blowOut(room);
        }
    }

    /** Vents gas above its target fuel level into the matching tank if any (else space), or always space. */
    private void exhaust(Room room, float dt) {
        double t = chamber.temperature();
        for (Gas gas : Gas.values()) {
            double excess = chamber.moles(gas) - targetMoles(gas);
            if (excess > 0) {
                double vented = chamber.removeGas(gas, Math.min(excess, VENT_CONDUCTANCE_PER_TILE * tileCount() * excess * dt));
                double rejected = vented * gas.specificHeat * Math.max(0, t - coldSinkTemperature); // exhaust cooled to the radiator; that heat is rejected, not deleted
                if (room.gas().heatCapacity() > 1e-6) {
                    room.gas().addHeat(rejected * WASTE_HEAT_TO_ROOM); // a slice leaks into the room, the rest radiates to space
                }
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
                return tank.accept(gas, moles, coldSinkTemperature);
            }
        }
        return 0;
    }

    /** Admits each fuel gas from any tank holding it, flowing toward the target partial pressure (pure pressure regulation). */
    private void intakeFromTanks(float dt) {
        if (ship() == null) {
            return;
        }
        for (Gas gas : Gas.values()) {
            double deficit = targetMoles(gas) - chamber.moles(gas);
            if (deficit <= 0) {
                continue;
            }
            double want = Math.min(deficit, INTAKE_CONDUCTANCE_PER_TILE * tileCount() * deficit * dt); // flow scales with the partial-pressure deficit
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

    /** Draws heat from the hot chamber toward the cold-side rating and turns it into power at Carnot efficiency. */
    private void coolForPower(Room room, float dt) {
        double t = chamber.temperature();
        double cap = chamber.heatCapacity();
        if (cap < 1e-6 || t <= COOLING_FLOOR) {
            powerRate = 0;
            smoothPower(0, dt);
            return;
        }
        double pull = COOLING_CONDUCTANCE_PER_MOLE * chamber.totalMoles() * (t - coldSinkTemperature) * dt; // heat exchange scales with the gas present, not just chamber size
        pull = Math.min(pull, (t - coldSinkTemperature) * cap); // never cool below the cold sink
        chamber.addHeat(-pull);
        double efficiency = Math.min(MAX_EFFICIENCY, 1.0 - coldSinkTemperature / t);
        double power = pull * efficiency * GENERATOR_GAIN;
        double rejected = pull * (1 - efficiency) * WASTE_HEAT_TO_ROOM; // waste heat that leaks into the cabin
        if (room.gas().heatCapacity() > 1e-6) {
            room.gas().addHeat(rejected);
        }
        powerAccumulator += power;
        int whole = (int) powerAccumulator;
        if (whole > 0 && ship() != null) {
            ship().addEnergy(whole);
            powerAccumulator -= whole;
        }
        powerRate = dt > 0 ? power / dt : 0;
        smoothPower(powerRate, dt);
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
