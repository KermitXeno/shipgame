package model.graph;

import model.atmos.Atmosphere;
import model.crew.Crew;
import model.system.ShipSystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The ship model: tiles ({@link Node}s), crew, and installed {@link ShipSystem}s, plus
 * the energy economy. Power and storage are derived live from the systems, so adding or
 * removing one immediately changes {@link #getProducedPerTick()} and {@link #getMaxStorage()}.
 * Other systems read and modify energy through this object.
 */
public class Ship {
    // seconds between discrete energy ticks (crew movement stays per-frame)
    private static final float TICK_INTERVAL = 1f;
    // global sim-time multiplier: <1 slows the whole game so power swings are readable and recoverable
    private static final float SIM_SPEED = 0.5f;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Crew> crew = new ArrayList<>();
    private final List<ShipSystem> systems = new ArrayList<>();
    private Atmosphere atmosphere;

    // innate ship output/storage before any systems; engines/batteries add on top
    private int baseGeneration = 0;
    private int baseStorageCapacity = 0;
    private int storedEnergy;

    private float tickTimer;

    public List<Node> getNodes() {
        return nodes;
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public Node nodeAt(int x, int y) {
        for (Node node : nodes) {
            if (node.getX() == x && node.getY() == y) {
                return node;
            }
        }
        return null;
    }

    public List<Crew> getCrew() {
        return crew;
    }

    public void addCrew(Crew member) {
        crew.add(member);
    }

    public void removeCrew(Crew member) {
        if (crew.remove(member)) {
            member.releaseNodes();
        }
    }

    public List<ShipSystem> getSystems() {
        return systems;
    }

    public Atmosphere getAtmosphere() {
        return atmosphere;
    }

    public void setAtmosphere(Atmosphere atmosphere) {
        this.atmosphere = atmosphere;
    }

    public void addSystem(ShipSystem system) {
        system.bind(this);
        systems.add(system);
    }

    public int countSystems(String id) {
        int count = 0;
        for (ShipSystem system : systems) {
            if (system.id().equals(id)) {
                count++;
            }
        }
        return count;
    }

    public int getBaseGeneration() {
        return baseGeneration;
    }

    public void setBaseGeneration(int value) {
        baseGeneration = value;
    }

    public void setBaseStorageCapacity(int value) {
        baseStorageCapacity = value;
    }

    /** Power generated per tick: base plus every system's contribution. */
    public int getProducedPerTick() {
        int generation = baseGeneration;
        for (ShipSystem system : systems) {
            generation += system.generationPerTick();
        }
        return generation;
    }

    /** Total power being generated this second (base plus any system producing). */
    public int getGeneratedPerSecond() {
        int g = baseGeneration;
        for (ShipSystem system : systems) {
            int p = system.generationPerTick();
            if (p > 0) {
                g += p;
            }
        }
        return g;
    }

    /** Total power being consumed this second (systems with a negative rate), as a positive number. */
    public int getConsumedPerSecond() {
        int c = 0;
        for (ShipSystem system : systems) {
            int p = system.generationPerTick();
            if (p < 0) {
                c -= p;
            }
        }
        return c;
    }

    /** Total storable energy: base plus every battery's capacity. */
    public int getMaxStorage() {
        int max = baseStorageCapacity;
        for (ShipSystem system : systems) {
            max += system.storageCapacity();
        }
        return max;
    }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    /** Adds energy clamped to [0, maxStorage]; returns the amount actually stored. */
    public int addEnergy(int amount) {
        int next = Math.max(0, Math.min(storedEnergy + amount, getMaxStorage()));
        int changed = next - storedEnergy;
        storedEnergy = next;
        return changed;
    }

    /** Removes up to {@code amount} from the bank; returns the amount actually drawn. */
    public int drawEnergy(int amount) {
        int drawn = Math.min(Math.max(0, amount), storedEnergy);
        storedEnergy -= drawn;
        return drawn;
    }

    public void tick(float delta) {
        delta *= SIM_SPEED; // stretch sim time so the game doesn't feel frantic
        for (Crew member : crew) {
            member.tick(delta);
        }
        tickTimer += delta;
        // fixed-timestep accumulator: run a whole number of energy ticks per frame
        while (tickTimer >= TICK_INTERVAL) {
            tickTimer -= TICK_INTERVAL;
            systemsTick();
        }
        // gas/heat run every frame so flow tracks crew crossing doors smoothly
        if (atmosphere != null) {
            atmosphere.tick(delta, openDoors());
        }
    }

    /** Door edges a crew member is currently crossing (gas may flow through these this frame). */
    private Set<Edge> openDoors() {
        Set<Edge> open = new HashSet<>();
        for (Crew member : crew) {
            Node from = member.getCurrentNode();
            Node to = member.getTargetNode();
            if (from == null || to == null) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                Edge edge = from.getEdge(dir);
                if (edge != null && edge.other(from) == to && edge.getType() == EdgeType.DOOR) {
                    open.add(edge);
                    break;
                }
            }
        }
        return open;
    }

    private void systemsTick() {
        for (ShipSystem system : systems) {
            system.tick();
        }
        // engines now add power continuously (heat->power in the atmosphere step); only base is discrete
        addEnergy(baseGeneration);
    }
}
