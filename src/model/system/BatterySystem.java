package model.system;

import com.badlogic.gdx.utils.JsonValue;

/** Raises the ship's storage capacity (tiles x storagePerTile). Room is 1x1 or 1x2. */
public class BatterySystem extends ShipSystem {
    private static final int DEFAULT_STORAGE_PER_TILE = 250000;
    private static final int MAX_PER_SHIP = 8;
    private static final Dimension MIN = new Dimension(1, 1);
    private static final Dimension MAX = new Dimension(1, 2);

    private int storagePerTile = DEFAULT_STORAGE_PER_TILE;

    @Override
    public String id() {
        return "battery";
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
        return SystemDecal.color("BATTERY", 0.45f, 0.85f, 0.45f);
    }

    @Override
    public double equipmentMass() {
        return 1200;
    }

    @Override
    public void configure(JsonValue entry) {
        storagePerTile = entry.getInt("storagePerTile", storagePerTile);
    }

    @Override
    public int storageCapacity() {
        return tileCount() * storagePerTile;
    }
}
