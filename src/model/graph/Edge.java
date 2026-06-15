package model.graph;

/** Undirected link between two nodes (or a node and the void); {@link #other} returns the far side. */
public class Edge {
    private final Node a;
    private final Node b;
    private EdgeType type;

    public Edge(Node a, Node b, EdgeType type) {
        this.a = a;
        this.b = b;
        this.type = type;
    }

    public Node other(Node from) {
        return from == a ? b : a;
    }

    public EdgeType getType() {
        return type;
    }

    public void setType(EdgeType type) {
        this.type = type;
    }

    public boolean isPassable() {
        return type.isPassable();
    }
}
