package model.system;

import model.graph.Direction;
import model.graph.Edge;
import model.graph.EdgeType;
import model.graph.Node;
import model.graph.Ship;

/** Checks that a w x h room at (x, y) is a real, fully-open, unoccupied rectangle of an allowed size. */
public final class SystemPlacement {
    private SystemPlacement() {
    }

    public static boolean isValid(Ship ship, ShipSystem system, int x, int y, int w, int h) {
        return reason(ship, system, x, y, w, h) == null;
    }

    /** A human-readable reason the placement is invalid, or null if valid. */
    public static String reason(Ship ship, ShipSystem system, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return "non-positive size " + w + "x" + h;
        }
        if (!new Dimension(w, h).fitsWithin(system.minDimension(), system.maxDimension())) {
            return w + "x" + h + " not within " + system.minDimension() + ".." + system.maxDimension();
        }
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                Node node = ship.nodeAt(x + dx, y + dy);
                if (node == null) {
                    return "no node at (" + (x + dx) + "," + (y + dy) + ")";
                }
                if (node.getSystem() != null && node.getSystem() != system) {
                    return "(" + (x + dx) + "," + (y + dy) + ") occupied by " + node.getSystem().id();
                }
            }
        }
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                Node node = ship.nodeAt(x + dx, y + dy);
                // interior edges must be OPEN; check only east/north to avoid testing each twice
                if (dx + 1 < w && !isOpen(node, Direction.EAST)) {
                    return "wall between (" + (x + dx) + "," + (y + dy) + ") and east";
                }
                if (dy + 1 < h && !isOpen(node, Direction.NORTH)) {
                    return "wall between (" + (x + dx) + "," + (y + dy) + ") and north";
                }
            }
        }
        return null;
    }

    private static boolean isOpen(Node node, Direction dir) {
        Edge edge = node.getEdge(dir);
        return edge != null && edge.getType() == EdgeType.OPEN;
    }
}
