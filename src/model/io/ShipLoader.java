package model.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import model.atmos.Atmosphere;
import model.atmos.Gas;
import model.crew.Crew;
import model.graph.Direction;
import model.graph.Edge;
import model.graph.EdgeType;
import model.graph.Node;
import model.graph.Ship;
import model.system.ShipSystem;
import model.system.SystemPlacement;
import model.system.SystemRegistry;

/**
 * Builds a {@link Ship} from a JSON definition: nodes, edges, optional base energy,
 * systems, then crew. Logs a summary and per-system placement results to make bad
 * ship layouts easy to diagnose.
 */
public final class ShipLoader {
    private ShipLoader() {
    }

    public static Ship load(FileHandle file) {
        JsonValue root = new JsonReader().parse(file);
        Ship ship = new Ship();

        for (JsonValue n = root.get("nodes").child; n != null; n = n.next) {
            ship.addNode(new Node(n.getInt("id"), n.getInt("x"), n.getInt("y")));
        }

        JsonValue edges = root.get("edges");
        if (edges != null) {
            for (JsonValue e = edges.child; e != null; e = e.next) {
                Node from = findById(ship, e.getInt("from"));
                Direction dir = Direction.valueOf(e.getString("dir"));
                EdgeType type = EdgeType.valueOf(e.getString("type"));
                Node to = ship.nodeAt(from.getX() + dir.dx, from.getY() + dir.dy);
                connect(from, to, dir, type);
            }
        }

        sealVoid(ship); // unspecified edges become walls so the ship has no open sides
        sealVoid(ship);
        if (root.has("baseGeneration")) {
            ship.setBaseGeneration(root.getInt("baseGeneration"));
        }
        if (root.has("baseStorage")) {
            ship.setBaseStorageCapacity(root.getInt("baseStorage"));
        }
        if (root.has("armor")) {
            ship.setArmor(root.getFloat("armor"));
        }
        loadSystems(ship, root.get("systems"));
        loadCrew(ship, root.get("crew"));
        loadAtmosphere(ship, root.get("atmosphere"));
        Gdx.app.log("ShipLoader", "=== loaded: systems=" + ship.getSystems().size()
                + " produced/tick=" + ship.getProducedPerTick()
                + " maxStorage=" + ship.getMaxStorage()
                + " mass=" + ship.getTotalMass() + "kg"
                + " rooms=" + ship.getAtmosphere().rooms().size() + " ===");
        return ship;
    }

    /** Builds the room/gas model, then fills it from the optional "atmosphere" block (vacuum if absent). */
    private static void loadAtmosphere(Ship ship, JsonValue config) {
        Atmosphere atmosphere = new Atmosphere(ship);
        ship.setAtmosphere(atmosphere);
        if (config == null) {
            Gdx.app.log("ShipLoader", "atmosphere: vacuum (no config)");
            return;
        }
        double temperature = config.getFloat("temperature", 293.15f);
        if (config.has("air")) {
            atmosphere.fill(Gas.AIR, config.getFloat("air"), temperature);
        }
        JsonValue moles = config.get("moles");
        if (moles != null) {
            for (JsonValue g = moles.child; g != null; g = g.next) {
                Gas gas = parseGas(g.name());
                if (gas != null) {
                    atmosphere.fill(gas, g.asFloat(), temperature);
                }
            }
        }
        Gdx.app.log("ShipLoader", "atmosphere: filled at " + temperature + "K");
    }

    private static Gas parseGas(String name) {
        try {
            return Gas.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            Gdx.app.error("ShipLoader", "Unknown gas: " + name);
            return null;
        }
    }

    /** Places each system from the "systems" array, skipping (with a logged reason) any that don't fit. */
    private static void loadSystems(Ship ship, JsonValue array) {
        Gdx.app.log("ShipLoader", "systems array present=" + (array != null)
                + " entries=" + (array == null ? 0 : array.size));
        if (array == null) {
            return;
        }
        for (JsonValue s = array.child; s != null; s = s.next) {
            String type = s.getString("type", null);
            if (type == null || !SystemRegistry.exists(type)) {
                Gdx.app.error("ShipLoader", "Unknown system type: " + type);
                continue;
            }
            ShipSystem system = SystemRegistry.create(type);
            if (ship.countSystems(type) >= system.maxPerShip()) {
                Gdx.app.error("ShipLoader", "Max " + type + " reached, skipping");
                continue;
            }
            system.bind(ship);
            system.configure(s);
            int x = s.getInt("x"), y = s.getInt("y"), w = s.getInt("w"), h = s.getInt("h");
            // validate first so a bad entry is reported, not silently dropped
            String why = SystemPlacement.reason(ship, system, x, y, w, h);
            if (why == null) {
                system.place(x, y, w, h);
                ship.addSystem(system);
                Gdx.app.log("ShipLoader", "placed " + type + " at " + x + "," + y + " " + w + "x" + h
                        + " (tiles=" + system.tileCount() + ", gen=" + system.generationPerTick()
                        + ", store=" + system.storageCapacity() + ")");
            } else {
                Gdx.app.error("ShipLoader", "SKIPPED " + type + " at " + x + "," + y + " "
                        + w + "x" + h + ": " + why);
            }
        }
    }

    private static void loadCrew(Ship ship, JsonValue crewArray) {
        if (crewArray == null) {
            return;
        }
        int id = 0;
        for (JsonValue c = crewArray.child; c != null; c = c.next) {
            Node node = findById(ship, c.getInt("node"));
            if (node == null || node.isOccupied()) {
                continue;
            }
            String name = c.getString("name", "Crew " + id);
            ship.addCrew(new Crew(id++, name, node));
        }
    }

    private static Node findById(Ship ship, int id) {
        for (Node node : ship.getNodes()) {
            if (node.getId() == id) {
                return node;
            }
        }
        return null;
    }

    private static void connect(Node from, Node to, Direction dir, EdgeType type) {
        Edge edge = new Edge(from, to, type);
        from.setEdge(dir, edge);
        if (to != null) {
            to.setEdge(dir.opposite(), edge);
        }
    }

    /** Fills every edge not defined in JSON with a WALL facing the void. */
    private static void sealVoid(Ship ship) {
        for (Node node : ship.getNodes()) {
            for (Direction dir : Direction.values()) {
                if (node.getEdge(dir) == null) {
                    node.setEdge(dir, new Edge(node, null, EdgeType.WALL));
                }
            }
        }
    }
}
