package render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;
import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Room;
import model.graph.Direction;
import model.graph.Edge;
import model.graph.EdgeType;
import model.graph.Node;
import model.graph.Ship;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders each room's gas as one continuous translucent shell (top plus the room's outer
 * walls, no internal seams or per-tile gaps). Colour is the mol-fraction blend, opacity
 * scales with density, and temperature drives the gas's own emissive glow plus soft
 * per-tile point lights, so hot gas looks self-lit rather than spotlit.
 *
 * Draw it AFTER the floor decals and crew, with depth-write off, so they show through it.
 * Call {@link #updateLights()} before the hull is drawn so the lights illuminate it.
 */
public class AtmosphereRenderer implements Disposable {
    public static final int MAX_LIGHTS = 32; // also set as the model batch's point-light count

    private static final float GAS_BASE = 0.0f;
    private static final float ROOM_HEIGHT = 0.55f;                 // matches the wall height
    private static final float GAS_HEIGHT = ROOM_HEIGHT * 4f / 3f;  // ~a third taller than the room
    private static final float HALF = ShipRenderer.TILE * 0.5f;

    private static final double REFERENCE_MOLES_PER_TILE = 104.5;   // ~1 atm; sets the opacity scale
    private static final float MAX_ALPHA = 0.8f;
    private static final float HOT_ALPHA_CAP = 0.75f;

    private static final double GLOW_START = 330;  // K: gas begins to glow
    private static final double FIRE_TEMP = 420;   // K: full glow / fire range
    private static final float EMISSIVE_GAIN = 1.6f;
    private static final float LIGHT_INTENSITY = 1.0f;
    private static final float HEAT_RANGE = 1350f;        // K over which the glow colour sweeps red->orange->yellow
    private static final float DIFFUSE_HEAT_MIX = 0.8f;  // how far the gas body trends to the incandescent colour
    private static final float GAS_TINT = 0.5f;         // how much the glow keeps the gas's own colour (kept small)
    private static final float FLICKER_AMP = 0.12f;      // hot-glow flicker strength
    private static final float DRIFT_AMP = 0.06f;        // slow opacity drift so gas isn't a dead slab

    private final List<Model> models = new ArrayList<>();
    private final List<RoomVisual> roomVisuals = new ArrayList<>();
    private final List<PointLight> lights = new ArrayList<>();
    private final float[] tint = new float[3];
    private final float[] hue = new float[3];
    private float time;

    private record RoomVisual(Room room, ModelInstance instance) {
    }

    public AtmosphereRenderer(Ship ship, Environment environment) {
        List<Room> rooms = ship.getAtmosphere().rooms();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        int tiles = 0;
        for (Room room : rooms) {
            tiles += room.tileCount();
            Material material = new Material(
                    ColorAttribute.createDiffuse(1f, 1f, 1f, 0.2f),
                    ColorAttribute.createEmissive(0f, 0f, 0f, 1f),
                    new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.2f),
                    new DepthTestAttribute(GL20.GL_LEQUAL, false), // test against hull, don't occlude decals/crew
                    IntAttribute.createCullFace(GL20.GL_NONE));    // thin shell: keep both face sides

            ModelBuilder builder = new ModelBuilder();
            builder.begin();
            MeshPartBuilder part = builder.part("gas", GL20.GL_TRIANGLES, attrs, material);
            for (Node node : room.nodes()) {
                buildTile(part, node);
            }
            Model model = builder.end();
            models.add(model);
            roomVisuals.add(new RoomVisual(room, new ModelInstance(model)));
        }

        int count = Math.min(MAX_LIGHTS, Math.max(1, tiles));
        for (int i = 0; i < count; i++) {
            PointLight light = new PointLight().set(0f, 0f, 0f, 0f, 0f, 0f, 0f);
            lights.add(light);
            environment.add(light);
        }
    }

    /** Adds the tile's top face, plus a side wall only where the tile borders another room or the void. */
    private void buildTile(MeshPartBuilder part, Node node) {
        float cx = node.getX() * ShipRenderer.TILE;
        float cz = node.getY() * ShipRenderer.TILE;
        float base = GAS_BASE;
        float top = GAS_HEIGHT;

        part.rect(cx - HALF, top, cz - HALF, cx - HALF, top, cz + HALF,
                cx + HALF, top, cz + HALF, cx + HALF, top, cz - HALF, 0f, 1f, 0f);

        if (boundary(node, Direction.EAST)) {
            part.rect(cx + HALF, base, cz + HALF, cx + HALF, base, cz - HALF,
                    cx + HALF, top, cz - HALF, cx + HALF, top, cz + HALF, 1f, 0f, 0f);
        }
        if (boundary(node, Direction.WEST)) {
            part.rect(cx - HALF, base, cz - HALF, cx - HALF, base, cz + HALF,
                    cx - HALF, top, cz + HALF, cx - HALF, top, cz - HALF, -1f, 0f, 0f);
        }
        if (boundary(node, Direction.NORTH)) {
            part.rect(cx - HALF, base, cz + HALF, cx + HALF, base, cz + HALF,
                    cx + HALF, top, cz + HALF, cx - HALF, top, cz + HALF, 0f, 0f, 1f);
        }
        if (boundary(node, Direction.SOUTH)) {
            part.rect(cx + HALF, base, cz - HALF, cx - HALF, base, cz - HALF,
                    cx - HALF, top, cz - HALF, cx + HALF, top, cz - HALF, 0f, 0f, -1f);
        }
    }

    private boolean boundary(Node node, Direction dir) {
        Edge edge = node.getEdge(dir);
        return edge == null || edge.getType() != EdgeType.OPEN; // same room only across OPEN edges
    }

    /** Places a soft light over each hot tile (hottest rooms first), so the glow spreads evenly. */
    public void updateLights() {
        time += Gdx.graphics.getDeltaTime(); // advanced here (runs once per frame, before the gas pass)
        List<Room> hot = new ArrayList<>();
        for (RoomVisual visual : roomVisuals) {
            if (visual.room().temperature() > GLOW_START) {
                hot.add(visual.room());
            }
        }
        hot.sort((a, b) -> Double.compare(b.temperature(), a.temperature()));

        int li = 0;
        for (Room room : hot) {
            float heat = clamp((float) ((room.temperature() - GLOW_START) / (FIRE_TEMP - GLOW_START)), 0f, 1.5f);
            float[] color = glowColor(room); // incandescent colour, slightly gas-tinted
            float brightness = heat * LIGHT_INTENSITY * flicker(room);
            for (Node node : room.nodes()) {
                if (li >= lights.size()) {
                    break;
                }
                lights.get(li++).set(color[0], color[1], color[2],
                        node.getX() * ShipRenderer.TILE, GAS_HEIGHT * 0.5f, node.getY() * ShipRenderer.TILE,
                        brightness);
            }
            if (li >= lights.size()) {
                break;
            }
        }
        for (; li < lights.size(); li++) {
            lights.get(li).intensity = 0f;
        }
    }

    /** Draws the translucent gas shells. Call after decals/crew, in a depth-write-off pass. */
    public void renderGas(ModelBatch batch, Environment environment) {
        for (RoomVisual visual : roomVisuals) {
            GasMixture gas = visual.room().gas();
            double total = gas.totalMoles();
            if (total < 1e-6) {
                continue; // vacuum: nothing to draw
            }
            float r = 0f, g = 0f, b = 0f;
            double alphaAcc = 0;
            for (Gas type : Gas.values()) {
                double n = gas.moles(type);
                if (n <= 0) {
                    continue;
                }
                float frac = (float) (n / total);
                r += frac * type.r;
                g += frac * type.g;
                b += frac * type.b;
                alphaAcc += n * type.visualAlpha;
            }
            float baseAlpha = clamp((float) (alphaAcc / (REFERENCE_MOLES_PER_TILE * visual.room().tileCount())), 0f, MAX_ALPHA);
            float heat = clamp((float) ((visual.room().temperature() - GLOW_START) / (FIRE_TEMP - GLOW_START)), 0f, 1.5f);

            // incandescent colour for this temperature (warm-biased blackbody ramp)
            heatHue(visual.room(), hue);
            // gas body warms toward that colour as it heats, kept partial so gases stay distinct
            float mix = Math.min(heat, 1f) * DIFFUSE_HEAT_MIX;
            float dr = lerp(r, hue[0], mix);
            float dg = lerp(g, hue[1], mix);
            float db = lerp(b, hue[2], mix);
            float alpha = clamp((baseAlpha + heat * 0.25f) * drift(visual.room()), 0f, HOT_ALPHA_CAP);

            Material material = visual.instance().materials.get(0);
            material.set(ColorAttribute.createDiffuse(dr, dg, db, alpha));
            material.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha));
            // glow is the incandescent colour, slightly gas-tinted, with a subtle flicker
            float e = heat * EMISSIVE_GAIN * flicker(visual.room());
            material.set(ColorAttribute.createEmissive(
                    lerp(hue[0], r, GAS_TINT) * e, lerp(hue[1], g, GAS_TINT) * e, lerp(hue[2], b, GAS_TINT) * e, 1f));
            batch.render(visual.instance(), environment);
        }
    }

    /** Incandescent (warm-biased blackbody) colour for the room's temperature into {@code out}. */
    private void heatHue(Room room, float[] out) {
        float t = clamp((float) ((room.temperature() - GLOW_START) / HEAT_RANGE), 0f, 1f);
        if (t < 0.5f) {
            float k = t / 0.5f; // deep red -> orange
            out[0] = lerp(0.85f, 1.00f, k);
            out[1] = lerp(0.12f, 0.45f, k);
            out[2] = lerp(0.03f, 0.10f, k);
        } else {
            float k = (t - 0.5f) / 0.5f; // orange -> warm yellow
            out[0] = 1.00f;
            out[1] = lerp(0.45f, 0.82f, k);
            out[2] = lerp(0.10f, 0.50f, k);
        }
    }

    /** The glow/light colour: incandescent hue tinted slightly by the room's gas, written to {@code tint}. */
    private float[] glowColor(Room room) {
        blendBase(room, tint);
        heatHue(room, hue);
        tint[0] = lerp(hue[0], tint[0], GAS_TINT);
        tint[1] = lerp(hue[1], tint[1], GAS_TINT);
        tint[2] = lerp(hue[2], tint[2], GAS_TINT);
        return tint;
    }

    /** Fast, low-amplitude flicker (two out-of-phase sines), per-room so rooms aren't in sync. */
    private float flicker(Room room) {
        float phase = room.centerX() * 1.3f + room.centerY() * 0.7f;
        float n = 0.6f * (float) Math.sin(time * 9.0f + phase) + 0.4f * (float) Math.sin(time * 14.3f + phase * 1.7f);
        return 1f + FLICKER_AMP * n;
    }

    /** Slow opacity drift so even cool gas looks alive. */
    private float drift(Room room) {
        float phase = room.centerX() * 0.9f + room.centerY() * 1.1f;
        return 1f + DRIFT_AMP * (float) Math.sin(time * 0.8f + phase);
    }

    /** Mol-fraction blend of the gases' base colours into {@code out}. */
    private void blendBase(Room room, float[] out) {
        GasMixture gas = room.gas();
        double total = gas.totalMoles();
        out[0] = 0f;
        out[1] = 0f;
        out[2] = 0f;
        if (total < 1e-6) {
            return;
        }
        for (Gas type : Gas.values()) {
            double n = gas.moles(type);
            if (n <= 0) {
                continue;
            }
            float frac = (float) (n / total);
            out[0] += frac * type.r;
            out[1] += frac * type.g;
            out[2] += frac * type.b;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    @Override
    public void dispose() {
        for (Model model : models) {
            model.dispose();
        }
    }
}
