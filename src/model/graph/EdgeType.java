package model.graph;

/** Boundary between adjacent tiles: OPEN (same room), DOOR (passable gap), WALL (blocked). */
public enum EdgeType {
    OPEN(true),
    DOOR(true),
    WALL(false);

    private final boolean passable;

    EdgeType(boolean passable) {
        this.passable = passable;
    }

    public boolean isPassable() {
        return passable;
    }
}
