package model.atmos;

import model.graph.Node;

import java.util.List;

/**
 * A room: a maximal cluster of OPEN-connected tiles sharing one {@link GasMixture}
 * (so pressure and temperature are uniform across it). Volume scales with tile count.
 */
public class Room {
    private final List<Node> nodes;
    private final GasMixture gas = new GasMixture();

    public Room(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public GasMixture gas() {
        return gas;
    }

    public int tileCount() {
        return nodes.size();
    }

    public double volume() {
        return nodes.size() * GasMixture.TILE_VOLUME;
    }

    public double pressure() {
        return gas.pressure(volume());
    }

    public double temperature() {
        return gas.temperature();
    }

    public float centerX() {
        float sum = 0;
        for (Node n : nodes) {
            sum += n.getX();
        }
        return sum / nodes.size();
    }

    public float centerY() {
        float sum = 0;
        for (Node n : nodes) {
            sum += n.getY();
        }
        return sum / nodes.size();
    }
}
