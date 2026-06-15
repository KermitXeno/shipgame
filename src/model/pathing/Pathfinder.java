package model.pathing;

import model.graph.Direction;
import model.graph.Edge;
import model.graph.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** Breadth-first pathfinding over passable edges, avoiding occupied tiles (except the goal). */
public final class Pathfinder {
    private Pathfinder() {
    }

    public static List<Node> findPath(Node start, Node goal) {
        if (start == null || goal == null || start == goal || goal.isOccupied()) {
            return List.of();
        }
        Map<Node, Node> cameFrom = new HashMap<>();
        Queue<Node> frontier = new ArrayDeque<>();
        frontier.add(start);
        cameFrom.put(start, start); // start maps to itself so reconstruct() knows where to stop
        cameFrom.put(start, start);
        while (!frontier.isEmpty()) {
            Node current = frontier.poll();
            if (current == goal) {
                return reconstruct(cameFrom, start, goal);
            }
            for (Direction dir : Direction.values()) {
                Edge edge = current.getEdge(dir);
                if (edge == null || !edge.isPassable()) {
                    continue;
                }
                Node next = edge.other(current);
                if (next == null || cameFrom.containsKey(next)) {
                    continue;
                }
                // can't route through occupied tiles, but the goal itself is allowed
                if (next.isOccupied() && next != goal) {
                    continue;
                }
                cameFrom.put(next, current);
                frontier.add(next);
            }
        }
        return List.of();
    }

    private static List<Node> reconstruct(Map<Node, Node> cameFrom, Node start, Node goal) {
        List<Node> path = new ArrayList<>();
        Node current = goal;
        while (current != start) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return path;
    }
}
