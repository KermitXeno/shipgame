package model.graph;

import model.atmos.Atmosphere;
import model.combat.Armor;
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
    private static final double ARMOR_MASS_K = 0.4; // each armor point multiplies compartment mass by this
    private static final double HULL_KJ_PER_KG = 1.0; // structural toughness: kJ of damage the bare hull absorbs per kg (1 unit = 1 kJ)

    private final List<Node> nodes = new ArrayList<>();
    private final List<Crew> crew = new ArrayList<>();
    private final List<ShipSystem> systems = new ArrayList<>();
    private Atmosphere atmosphere;

    // innate ship output/storage before any systems; engines/batteries add on top
    private int baseGeneration = 0;
    private int baseStorageCapacity = 0;
    private int storedEnergy;
    private double armor;
    private int hull = -1; // current integrity in energy units (kJ); -1 means full, resolved lazily against the live max

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

    /** Bare compartment mass (kg), summed before any armor scaling. */
    private double compartmentMass() {
        double sum = 0;
        for (Node node : nodes) {
            sum += node.mass();
        }
        return sum;
    }

    /** Structural boundary mass (kg): every wall/door counted exactly once. */
    private double boundaryMass() {
        double sum = 0;
        Set<Edge> counted = new HashSet<>();
        for (Node node : nodes) {
            for (Direction dir : Direction.values()) {
                Edge edge = node.getEdge(dir);
                if (edge != null && counted.add(edge)) {
                    sum += edge.getType().mass();
                }
            }
        }
        return sum;
    }

    /** Combined mass (kg) of installed system machinery. */
    private double equipmentMass() {
        double sum = 0;
        for (ShipSystem system : systems) {
            sum += system.equipmentMass();
        }
        return sum;
    }

    /** Total ship mass (kg): armor-scaled compartments + boundaries (each once) + system machinery. */
    public int getTotalMass() {
        return (int) Math.round(compartmentMass() * (1 + ARMOR_MASS_K * armor) + boundaryMass() + equipmentMass());
    }

    /** Load-bearing hull mass (kg): compartments + boundaries, before armor and excluding machinery. */
    public int getHullMass() {
        return (int) Math.round(compartmentMass() + boundaryMass());
    }

    /** Maximum hull integrity in energy units (kJ): bare hull mass x toughness, independent of armor. */
    public int getMaxHull() {
        return (int) Math.round((compartmentMass() + boundaryMass()) * HULL_KJ_PER_KG);
    }

    /** Current hull integrity (kJ); a fresh ship starts full and is resolved live against the max. */
    public int getHull() {
        int max = getMaxHull();
        if (hull < 0 || hull > max) {
            return max;
        }
        return hull;
    }

    /** Takes a shot of {@code rawEnergy} (kJ): armor mitigates it, the remainder is subtracted from the hull. Returns kJ dealt. */
    public int takeHit(double rawEnergy) {
        int dealt = (int) Math.round(Armor.damage(rawEnergy, armor));
        hull = Math.max(0, getHull() - dealt);
        return dealt;
    }

    /** Restores up to {@code amount} kJ of integrity, clamped to the max; returns the amount actually repaired. */
    public int repairHull(int amount) {
        int current = getHull();
        int next = Math.max(0, Math.min(current + Math.max(0, amount), getMaxHull()));
        hull = next;
        return next - current;
    }

    /** True once integrity reaches zero. */
    public boolean isDestroyed() {
        return getHull() <= 0;
    }

    /** Ship-wide armor rating, applied to compartment mass and used by combat damage. */
    public double getArmor() {
        return armor;
    }

    public void setArmor(double armor) {
        this.armor = Math.max(0, armor);
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
