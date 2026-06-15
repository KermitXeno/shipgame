package model.crew;

import com.badlogic.gdx.math.Vector2;
import model.graph.Node;
import model.pathing.Pathfinder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A crew member that walks node-to-node along a BFS path. Motion is continuous
 * (interpolated each frame), but the next tile is reserved as the member steps toward
 * it so two crew never share a node.
 */
public class Crew {
    private static final float SPEED = 3f;

    private final int id;
    private final String name;

    private Node currentNode;
    private Node targetNode;
    private final Deque<Node> path = new ArrayDeque<>();
    private final Vector2 position = new Vector2();

    public Crew(int id, String name, Node start) {
        this.id = id;
        this.name = name;
        this.currentNode = start;
        start.setOccupant(this);
        position.set(start.getX(), start.getY());
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    /** The node this crew is currently stepping toward, or null if not moving. */
    public Node getTargetNode() {
        return targetNode;
    }

    public Vector2 getPosition() {
        return position;
    }

    public boolean isMoving() {
        return targetNode != null;
    }

    public void setDestination(Node goal) {
        Node from = targetNode != null ? targetNode : currentNode;
        List<Node> result = Pathfinder.findPath(from, goal);
        path.clear();
        path.addAll(result);
    }

    public void tick(float delta) {
        updateMovement(delta);
    }

    public void releaseNodes() {
        if (targetNode != null) {
            targetNode.setOccupant(null);
            targetNode = null;
        }
        if (currentNode != null) {
            currentNode.setOccupant(null);
        }
        path.clear();
    }

    private void updateMovement(float delta) {
        // no active step: pull the next node off the path and reserve it
        if (targetNode == null) {
            if (path.isEmpty()) {
                return;
            }
            Node next = path.peekFirst();
            if (next.isOccupied()) {
                path.clear();
                return;
            }
            path.pollFirst();
            currentNode.setOccupant(null);
            next.setOccupant(this);
            targetNode = next;
        }

        float tx = targetNode.getX();
        float ty = targetNode.getY();
        float dx = tx - position.x;
        float dy = ty - position.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float step = SPEED * delta;
        // snap on arrival, otherwise advance toward the target at constant speed
        if (dist <= step || dist == 0f) {
            position.set(tx, ty);
            currentNode = targetNode;
            targetNode = null;
        } else {
            position.add(dx / dist * step, dy / dist * step);
        }
    }
}
