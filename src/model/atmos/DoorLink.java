package model.atmos;

import model.graph.Edge;

/** A door connecting two rooms; gas flows across it only while {@code edge} is open. */
public record DoorLink(Edge edge, Room a, Room b) {
}
