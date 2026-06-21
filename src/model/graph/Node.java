package model.graph;

import model.atmos.Room;
import model.crew.Crew;
import model.system.ShipSystem;

import java.util.EnumMap;
import java.util.Map;

/**
 * One tile of the ship grid: its coords, edges to neighbours, and two independent
 * occupants - a {@link Crew} standing on it and the {@link ShipSystem} whose room it
 * belongs to (either may be null).
 */
public class Node {
    private static final double BASE_MASS = 800;   // kg of bare compartment

    private final int id;
    private final int x;
    private final int y;
    private final Map<Direction, Edge> edges = new EnumMap<>(Direction.class);
    private Crew occupant;
    private ShipSystem system;
    private Room room;

    public Node(int id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Crew getOccupant() {
        return occupant;
    }

    public void setOccupant(Crew occupant) {
        this.occupant = occupant;
    }

    public boolean isOccupied() {
        return occupant != null;
    }

    public ShipSystem getSystem() {
        return system;
    }

    public void setSystem(ShipSystem system) {
        this.system = system;
    }

    /** The gas room this tile belongs to (set when the atmosphere is built). */
    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Edge getEdge(Direction dir) {
        return edges.get(dir);
    }

    public void setEdge(Direction dir, Edge edge) {
        edges.put(dir, edge);
    }

    public Node neighbor(Direction dir) {
        Edge edge = edges.get(dir);
        return edge == null ? null : edge.other(this);
    }

    /** Bare compartment mass in kg (ship-level armor is applied by Ship). */
    public double mass() {
        return BASE_MASS;
    }
}
