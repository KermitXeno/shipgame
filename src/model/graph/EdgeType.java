package model.graph;

/** Boundary between adjacent tiles: OPEN (same room), DOOR (passable gap), WALL (blocked). */
public enum EdgeType {
    OPEN(true, 0),
    DOOR(true, 600),
    WALL(false, 500);

    private final boolean passable;
    private final double mass;

    EdgeType(boolean passable, double mass) {
        this.passable = passable;
        this.mass = mass;
    }

    public boolean isPassable() {
        return passable;
    }

    /** Structural mass of this boundary in kg (0 for an open gap). */
    public double mass() {
        return mass;
    }
}
