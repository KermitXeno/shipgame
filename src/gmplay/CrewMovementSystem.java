package gmplay;

import model.crew.Crew;
import model.graph.Direction;
import model.graph.Edge;
import model.graph.Node;
import model.graph.Ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Issues movement orders, spreading a group of crew across free tiles near a target (BFS).
 * Note: the live game currently drives crew via Ship.tick, so this is a helper rather than
 * the wired-in mover.
 */
public class CrewMovementSystem implements GameSystem {
    private final Ship ship;

    public CrewMovementSystem(Ship ship) {
        this.ship = ship;
    }

    @Override
    public void update(float delta) {
        for (Crew member : ship.getCrew()) {
            member.tick(delta);
        }
    }

    /** Sends each crew member to a distinct free tile near the target. */
    public void order(Iterable<Crew> crew, Node target) {
        List<Crew> members = new ArrayList<>();
        for (Crew member : crew) {
            members.add(member);
        }
        if (members.isEmpty() || target == null) {
            return;
        }
        List<Node> destinations = freeNodesNear(target, members.size());
        for (int i = 0; i < members.size() && i < destinations.size(); i++) {
            members.get(i).setDestination(destinations.get(i));
        }
    }

    private List<Node> freeNodesNear(Node start, int count) {
        List<Node> result = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        Queue<Node> frontier = new ArrayDeque<>();
        frontier.add(start);
        visited.add(start);
        while (!frontier.isEmpty() && result.size() < count) {
            Node current = frontier.poll();
            if (!current.isOccupied()) {
                result.add(current);
            }
            for (Direction dir : Direction.values()) {
                Edge edge = current.getEdge(dir);
                if (edge == null || !edge.isPassable()) {
                    continue;
                }
                Node next = edge.other(current);
                if (next == null || visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                frontier.add(next);
            }
        }
        return result;
    }
}
