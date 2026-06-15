package render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import model.graph.Direction;
import model.graph.Edge;
import model.graph.EdgeType;
import model.graph.Node;
import model.graph.Ship;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds the ship's static 3D geometry once: a floor box per tile, a thin box per
 * non-open edge (walls/doors), and corner posts that fill the seams where walls meet.
 * The model is immutable after construction.
 */
public class ShipRenderer implements Disposable {
    // world units per tile; shared by every renderer and the input picker
    public static final float TILE = 1f;

    private static final float FLOOR_HEIGHT = 0.02f;
    private static final float WALL_THICKNESS = 0.1f;
    private static final float WALL_HEIGHT = 0.35f;

    private static final Color FLOOR_COLOR = new Color(0.32f, 0.42f, 0.55f, 1f);
    private static final Color WALL_COLOR = new Color(0.20f, 0.22f, 0.26f, 1f);
    private static final Color DOOR_COLOR = new Color(0.85f, 0.65f, 0.25f, 1f);

    private final Model model;
    private final ModelInstance instance;
    private final Vector3 center = new Vector3();

    public ShipRenderer(Ship ship) {
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        buildFloors(builder, ship, attrs);
        buildEdges(builder, ship, attrs);
        buildCorners(builder, ship, attrs);
        model = builder.end();
        instance = new ModelInstance(model);
        computeCenter(ship);
    }

    private void buildFloors(ModelBuilder builder, Ship ship, long attrs) {
        for (Node node : ship.getNodes()) {
            MeshPartBuilder part = builder.part("floor" + node.getId(), GL20.GL_TRIANGLES, attrs,
                    new Material(ColorAttribute.createDiffuse(FLOOR_COLOR)));
            float cx = node.getX() * TILE;
            float cz = node.getY() * TILE;
            BoxShapeBuilder.build(part, cx, -FLOOR_HEIGHT / 2f, cz, TILE, FLOOR_HEIGHT, TILE);
        }
    }

    private void buildEdges(ModelBuilder builder, Ship ship, long attrs) {
        Set<Edge> done = new HashSet<>();
        for (Node node : ship.getNodes()) {
            for (Direction dir : Direction.values()) {
                Edge edge = node.getEdge(dir);
                // skip open edges and any edge already built from its other side
                if (edge == null || edge.getType() == EdgeType.OPEN || !done.add(edge)) {
                    continue;
                }
                Color color = edge.getType() == EdgeType.DOOR ? DOOR_COLOR : WALL_COLOR;
                MeshPartBuilder part = builder.part("edge" + done.size(), GL20.GL_TRIANGLES, attrs,
                        new Material(ColorAttribute.createDiffuse(color)));
                float cx = node.getX() * TILE + dir.dx * TILE / 2f;
                float cz = node.getY() * TILE + dir.dy * TILE / 2f;
                boolean horizontal = dir == Direction.NORTH || dir == Direction.SOUTH;
                float length = TILE - WALL_THICKNESS;
                float width = horizontal ? length : WALL_THICKNESS;
                float depth = horizontal ? WALL_THICKNESS : length;
                BoxShapeBuilder.build(part, cx, WALL_HEIGHT / 2f, cz, width, WALL_HEIGHT, depth);
            }
        }
    }

    /** Adds a post at each grid corner touched by a wall, hiding where two wall boxes meet. */
    private void buildCorners(ModelBuilder builder, Ship ship, long attrs) {
        Set<Long> corners = new HashSet<>();
        for (Node node : ship.getNodes()) {
            addCorner(corners, node, Direction.NORTH, Direction.EAST);
            addCorner(corners, node, Direction.NORTH, Direction.WEST);
            addCorner(corners, node, Direction.SOUTH, Direction.EAST);
            addCorner(corners, node, Direction.SOUTH, Direction.WEST);
        }
        int index = 0;
        for (long key : corners) {
            // unpack the two ints; coords were doubled so a half-tile is integral
            float px = (int) (key >> 32) * (TILE / 2f);
            float pz = (int) key * (TILE / 2f);
            MeshPartBuilder part = builder.part("post" + index++, GL20.GL_TRIANGLES, attrs,
                    new Material(ColorAttribute.createDiffuse(WALL_COLOR)));
            BoxShapeBuilder.build(part, px, WALL_HEIGHT / 2f, pz, WALL_THICKNESS, WALL_HEIGHT, WALL_THICKNESS);
        }
    }

    private void addCorner(Set<Long> corners, Node node, Direction vertical, Direction horizontal) {
        if (!isSolid(node.getEdge(vertical)) && !isSolid(node.getEdge(horizontal))) {
            return;
        }
        int hx = node.getX() * 2 + horizontal.dx;
        int hz = node.getY() * 2 + vertical.dy;
        // pack the doubled half-tile corner coords into a long so the Set dedups shared corners
        corners.add(((long) hx << 32) | ((long) hz & 0xffffffffL));
    }

    private boolean isSolid(Edge edge) {
        return edge != null && edge.getType() != EdgeType.OPEN;
    }

    private void computeCenter(Ship ship) {
        float sx = 0f;
        float sz = 0f;
        for (Node node : ship.getNodes()) {
            sx += node.getX() * TILE;
            sz += node.getY() * TILE;
        }
        int count = ship.getNodes().size();
        center.set(sx / count, 0f, sz / count);
    }

    public Vector3 getCenter() {
        return center;
    }

    public void render(ModelBatch batch, Environment environment) {
        batch.render(instance, environment);
    }

    @Override
    public void dispose() {
        model.dispose();
    }
}
