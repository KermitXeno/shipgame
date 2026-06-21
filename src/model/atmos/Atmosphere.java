package model.atmos;

import model.crew.Crew;
import model.graph.Direction;
import model.graph.Edge;
import model.graph.EdgeType;
import model.graph.Node;
import model.graph.Ship;
import model.system.AtmosphericsSystem;
import model.system.EngineSystem;
import model.system.GasStorageSystem;
import model.system.ShipSystem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the ship's rooms and runs the gas/heat simulation each frame:
 * <ol>
 *   <li>systems conduct heat with their room's gas (engine warms it, a cool system cools it),</li>
 *   <li>gas equalizes between rooms through any door that is currently open,</li>
 *   <li>the atmospherics system (if any) nudges every room toward the player's targets.</li>
 * </ol>
 * Rooms and door links are derived once from the static graph.
 */
public class Atmosphere {
    private static final double SYSTEM_GAS_CONDUCTIVITY = 300000; // J/(K*s) between a system and its room (100x scale)
    private static final double DOOR_FLOW_RATE = 2.5;          // fraction equalised per second through an open door
    private static final double WALL_SEEP = 4000;             // J/(K*s) of heat conducted through a shared wall (100x scale)
    private static final double DOOR_SEEP = 15000;            // J/(K*s) through a (closed) shared door (100x scale)
    private static final double AIR_PER_CREW = 0.0003;        // mol of air a crew member breathes per second
    private static final double CO2_PER_CREW = 0.00025;       // mol of CO2 a crew member exhales per second
    private static final double SPACE_TEMPERATURE = 3;        // K, the cold background the hull radiates heat to
    private static final double HULL_LOSS_PER_TILE = 80;     // J/(K*s) each tile's hull loses to space (100x scale)

    private final Ship ship;
    private final List<Room> rooms = new ArrayList<>();
    private final List<DoorLink> doors = new ArrayList<>();
    private final List<RoomSeep> seeps = new ArrayList<>();

    private record RoomSeep(Room a, Room b, double conductivity) {
    }

    public Atmosphere(Ship ship) {
        this.ship = ship;
        buildRooms();
        buildDoors();
        buildSeeps();
    }

    public List<Room> rooms() {
        return rooms;
    }

    private void buildRooms() {
        Set<Node> seen = new HashSet<>();
        for (Node start : ship.getNodes()) {
            if (!seen.add(start)) {
                continue;
            }
            List<Node> cluster = new ArrayList<>();
            Deque<Node> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                Node n = stack.pop();
                cluster.add(n);
                for (Direction d : Direction.values()) {
                    Edge e = n.getEdge(d);
                    if (e == null || e.getType() != EdgeType.OPEN) {
                        continue; // doors and walls bound the room
                    }
                    Node nb = e.other(n);
                    if (nb != null && seen.add(nb)) {
                        stack.push(nb);
                    }
                }
            }
            Room room = new Room(cluster);
            for (Node n : cluster) {
                n.setRoom(room);
            }
            rooms.add(room);
        }
    }

    private void buildDoors() {
        Set<Edge> seen = new HashSet<>();
        for (Node n : ship.getNodes()) {
            for (Direction d : Direction.values()) {
                Edge e = n.getEdge(d);
                if (e == null || e.getType() != EdgeType.DOOR || !seen.add(e)) {
                    continue;
                }
                Node nb = e.other(n);
                if (nb == null) {
                    continue;
                }
                Room a = n.getRoom();
                Room b = nb.getRoom();
                if (a != null && b != null && a != b) {
                    doors.add(new DoorLink(e, a, b));
                }
            }
        }
    }

    /** Heat-conduction links between adjacent rooms: one per bordering edge (walls slow, doors faster). */
    private void buildSeeps() {
        Set<Edge> seen = new HashSet<>();
        for (Node n : ship.getNodes()) {
            for (Direction d : Direction.values()) {
                Edge e = n.getEdge(d);
                if (e == null || e.getType() == EdgeType.OPEN || !seen.add(e)) {
                    continue; // OPEN edges are within one room
                }
                Node nb = e.other(n);
                if (nb == null) {
                    continue;
                }
                Room a = n.getRoom();
                Room b = nb.getRoom();
                if (a != null && b != null && a != b) {
                    seeps.add(new RoomSeep(a, b, e.getType() == EdgeType.DOOR ? DOOR_SEEP : WALL_SEEP));
                }
            }
        }
    }

    /** Populates every room with a gas at the given moles-per-tile and temperature. */
    public void fill(Gas gas, double molesPerTile, double temperature) {
        for (Room room : rooms) {
            room.gas().addGas(gas, molesPerTile * room.tileCount(), temperature);
        }
    }

    /** Advances the simulation. {@code openDoors} is the set of door edges gas may flow through this frame. */
    public void tick(float dt, Set<Edge> openDoors) {
        systemHeat(dt);
        operateStorage(dt);
        operateEngines(dt);
        reactRooms(dt);
        breathe(dt);
        seep(dt);
        flow(dt, openDoors);
        hullLoss(dt);
        regulate(dt);
    }

    /** Every room slowly radiates heat through its hull to cold space, so life support must heat constantly. */
    private void hullLoss(float dt) {
        for (Room room : rooms) {
            double cap = room.gas().heatCapacity();
            if (cap < 1e-6) {
                continue;
            }
            double t = room.gas().temperature();
            if (t <= SPACE_TEMPERATURE) {
                continue;
            }
            double q = Math.min(HULL_LOSS_PER_TILE * room.tileCount() * (t - SPACE_TEMPERATURE) * dt, (t - SPACE_TEMPERATURE) * cap);
            room.gas().addHeat(-q);
        }
    }

    /** Gas tanks release/cool their contents and rupture if over tolerance. */
    private void operateStorage(float dt) {
        for (ShipSystem system : ship.getSystems()) {
            if (system instanceof GasStorageSystem tank && !tank.nodes().isEmpty()) {
                Room room = tank.nodes().get(0).getRoom();
                if (room != null) {
                    tank.operate(room, dt);
                }
            }
        }
    }

    /** Runs the reaction table in every room (combustion etc.). */
    private void reactRooms(float dt) {
        for (Room room : rooms) {
            Reactions.react(room.gas(), room.volume(), dt);
        }
    }

    /** Engines run their internal chamber: intake, react, regulate, leak/blow out, make power. */
    private void operateEngines(float dt) {
        for (ShipSystem system : ship.getSystems()) {
            if (system instanceof EngineSystem engine && !engine.nodes().isEmpty()) {
                Room room = engine.nodes().get(0).getRoom();
                if (room != null) {
                    engine.operate(room, dt);
                }
            }
        }
    }

    /** Crew consume air from their room and exhale CO2. */
    private void breathe(float dt) {
        for (Crew member : ship.getCrew()) {
            Node node = member.getCurrentNode();
            if (node == null || node.getRoom() == null) {
                continue;
            }
            GasMixture gas = node.getRoom().gas();
            if (gas.moles(Gas.AIR) <= 0) {
                continue; // nothing to breathe
            }
            gas.addMolesRaw(Gas.AIR, -AIR_PER_CREW * dt);
            gas.addMolesRaw(Gas.CO2, CO2_PER_CREW * dt); // convert moles only; no spurious heat from the differing specific heats
        }
    }

    /** Heat slowly conducts between adjacent rooms through their shared walls and doors. */
    private void seep(float dt) {
        for (RoomSeep link : seeps) {
            GasMixture ga = link.a().gas();
            GasMixture gb = link.b().gas();
            double q = link.conductivity() * (ga.temperature() - gb.temperature()) * dt;
            q = clampSeep(q, ga, gb);
            ga.addHeat(-q);
            gb.addHeat(q);
        }
    }

    private double clampSeep(double q, GasMixture a, GasMixture b) {
        double ca = a.heatCapacity();
        double cb = b.heatCapacity();
        if (ca < 1e-6 || cb < 1e-6) {
            return 0;
        }
        double gap = Math.abs(a.temperature() - b.temperature());
        double maxByGap = gap * (ca * cb) / (ca + cb);
        return Math.signum(q) * Math.min(Math.abs(q), maxByGap);
    }

    private void systemHeat(float dt) {
        for (ShipSystem system : ship.getSystems()) {
            if (system instanceof AtmosphericsSystem || system instanceof EngineSystem || system.nodes().isEmpty()) {
                continue; // atmospherics regulates in regulate(); engines run their own chamber
            }
            system.addHeat(system.heatOutput() * dt); // heat from being used
            Room room = system.nodes().get(0).getRoom();
            if (room == null) {
                continue;
            }
            GasMixture gas = room.gas();
            if (gas.heatCapacity() < 1e-6) {
                continue; // vacuum: nothing to conduct into
            }
            double q = SYSTEM_GAS_CONDUCTIVITY * (system.temperature() - gas.temperature()) * dt;
            q = clampToEquilibrium(q, system, gas);
            system.addHeat(-q);
            gas.addHeat(q);
        }
    }

    /** Caps a conductive transfer so neither side overshoots the other's temperature this step. */
    private double clampToEquilibrium(double q, ShipSystem system, GasMixture gas) {
        double gap = Math.abs(system.temperature() - gas.temperature());
        double sysMass = system.thermalMass();
        double gasCap = gas.heatCapacity();
        double maxByGap = gap * (sysMass * gasCap) / (sysMass + gasCap);
        return Math.signum(q) * Math.min(Math.abs(q), maxByGap);
    }

    private void flow(float dt, Set<Edge> openDoors) {
        if (openDoors == null || openDoors.isEmpty()) {
            return;
        }
        double rate = Math.min(1.0, DOOR_FLOW_RATE * dt);
        for (DoorLink door : doors) {
            if (openDoors.contains(door.edge())) {
                share(door.a(), door.b(), rate);
            }
        }
    }

    /** Moves a fraction of moles and energy toward the volume-weighted average of the two rooms. */
    private void share(Room a, Room b, double rate) {
        double va = a.volume();
        double vb = b.volume();
        double vt = va + vb;
        GasMixture ga = a.gas();
        GasMixture gb = b.gas();
        for (Gas gas : Gas.values()) {
            double total = ga.moles(gas) + gb.moles(gas);
            double targetA = total * va / vt;
            double delta = rate * (targetA - ga.moles(gas)); // + flows into a
            ga.addMolesRaw(gas, delta);
            gb.addMolesRaw(gas, -delta);
        }
        double totalEnergy = ga.thermalEnergy() + gb.thermalEnergy();
        double targetEnergyA = totalEnergy * va / vt;
        double deltaEnergy = rate * (targetEnergyA - ga.thermalEnergy());
        ga.addHeat(deltaEnergy);
        gb.addHeat(-deltaEnergy);
    }

    private void regulate(float dt) {
        AtmosphericsSystem atmos = null;
        for (ShipSystem system : ship.getSystems()) {
            if (system instanceof AtmosphericsSystem found && !found.nodes().isEmpty()) {
                atmos = found;
                break;
            }
        }
        if (atmos == null) {
            return;
        }
        atmos.regulate(rooms, dt);
    }
}
