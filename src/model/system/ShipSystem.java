package model.system;

import com.badlogic.gdx.utils.JsonValue;
import model.graph.Node;
import model.graph.Ship;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for an FTL-style system occupying a rectangular room of open tiles. A
 * subclass declares its identity, size limits, decal and behaviour in one file; power
 * and storage scale with {@link #tileCount()}. Placement is mutable so a system can be
 * moved or resized at runtime later.
 */
public abstract class ShipSystem {
    /** Default starting temperature of a system and its surroundings (20 C). */
    public static final double AMBIENT_TEMPERATURE = 293.15;
    private static final double THERMAL_MASS_PER_TILE = 3000; // J/K of the machine itself

    private Ship ship;
    private final List<Node> nodes = new ArrayList<>();
    private int originX;
    private int originY;
    private int width;
    private int height;

    private double temperature = AMBIENT_TEMPERATURE;

    /** Stable type id used in ship JSON and the registry. */
    public abstract String id();

    public abstract Dimension minDimension();

    public abstract Dimension maxDimension();

    public abstract SystemDecal decal();

    public int maxPerShip() {
        return Integer.MAX_VALUE;
    }

    /** Optional hook to read per-instance overrides from the ship JSON entry. */
    public void configure(JsonValue entry) {
    }

    public void tick() {
    }

    /** Passive power per tick (0 unless overridden, e.g. by the engine). */
    public int generationPerTick() {
        return 0;
    }

    /** Passive storage capacity (0 unless overridden, e.g. by the battery). */
    public int storageCapacity() {
        return 0;
    }

    /** Heat generated per second while the system is in use (0 unless overridden, e.g. by the engine). */
    public double heatOutput() {
        return 0;
    }

    /** Heat capacity of the machine itself (J/K), scaling with its size. */
    public double thermalMass() {
        return Math.max(1, tileCount()) * THERMAL_MASS_PER_TILE;
    }

    public double temperature() {
        return temperature;
    }

    /** Adds (or removes) heat to the machine; temperature follows from {@link #thermalMass()}. */
    public void addHeat(double joules) {
        temperature = Math.max(0, temperature + joules / thermalMass());
    }

    public void bind(Ship ship) {
        this.ship = ship;
    }

    /** Validates and occupies the w x h room at (x, y). Returns false and changes nothing if invalid. */
    public boolean place(int x, int y, int w, int h) {
        if (ship == null || !SystemPlacement.isValid(ship, this, x, y, w, h)) {
            return false;
        }
        clearNodes();
        originX = x;
        originY = y;
        width = w;
        height = h;
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                Node node = ship.nodeAt(x + dx, y + dy);
                node.setSystem(this);
                nodes.add(node);
            }
        }
        return true;
    }

    private void clearNodes() {
        for (Node node : nodes) {
            if (node.getSystem() == this) {
                node.setSystem(null);
            }
        }
        nodes.clear();
    }

    public Ship ship() {
        return ship;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public int tileCount() {
        return nodes.size();
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Footprint centroid in tile coords (half-tile for even sizes); where the decal sits. */
    public float centerX() {
        return originX + (width - 1) / 2f;
    }

    public float centerY() {
        return originY + (height - 1) / 2f;
    }
}
