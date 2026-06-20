package render;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.utils.Disposable;
import model.crew.Crew;
import model.graph.Node;
import model.graph.Ship;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Draws each crew member as a camera-facing billboard decal, generated procedurally (no assets). */
public class CrewRenderer implements Disposable {
    private static final float SIZE = 0.6f;
    private static final float HEIGHT = 0.45f;
    private static final float HIGHLIGHT_Y = 0.014f; // just above the floor so it doesn't z-fight
    private static final float HIGHLIGHT_INSET = 0.9f;

    private final Texture normalTexture;
    private final Texture selectedTexture;
    private final Texture highlightTexture;
    private final TextureRegion normalRegion;
    private final TextureRegion selectedRegion;
    private final TextureRegion highlightRegion;
    private final Map<Crew, Decal> decals = new HashMap<>();
    private final Map<Node, Decal> highlights = new HashMap<>();

    public CrewRenderer() {
        normalTexture = makeCircle(false);
        selectedTexture = makeCircle(true);
        highlightTexture = makeHighlight();
        normalRegion = new TextureRegion(normalTexture);
        selectedRegion = new TextureRegion(selectedTexture);
        highlightRegion = new TextureRegion(highlightTexture);
    }

    private Texture makeHighlight() {
        int size = 64;
        int border = 6;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();
        pm.setColor(0.2f, 0.9f, 0.3f, 0.25f); // translucent green fill
        pm.fillRectangle(border, border, size - border * 2, size - border * 2);
        pm.setColor(0.3f, 1f, 0.4f, 0.9f); // solid green border
        pm.fillRectangle(border, border, size - border * 2, border);
        pm.fillRectangle(border, size - border * 2, size - border * 2, border);
        pm.fillRectangle(border, border, border, size - border * 2);
        pm.fillRectangle(size - border * 2, border, border, size - border * 2);
        Texture texture = new Texture(pm);
        pm.dispose();
        return texture;
    }

    private Texture makeCircle(boolean selected) {
        Pixmap pm = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();
        if (selected) {
            pm.setColor(0.3f, 0.9f, 1f, 1f);
            pm.fillCircle(32, 32, 31);
        }
        pm.setColor(0.95f, 0.85f, 0.3f, 1f);
        pm.fillCircle(32, 32, selected ? 26 : 30);
        Texture texture = new Texture(pm);
        pm.dispose();
        return texture;
    }

    public void render(DecalBatch batch, Camera camera, Ship ship, Collection<Crew> selected) {
        for (Crew crew : ship.getCrew()) {
            Node goal = crew.getGoal();
            if (goal == null) {
                continue; // idle or arrived: no highlight (covers paused-with-pending-order too, since getGoal reads the path)
            }
            Decal hl = highlights.computeIfAbsent(goal, g -> Decal.newDecal(SIZE, SIZE, highlightRegion, true));
            hl.setWidth(ShipRenderer.TILE * HIGHLIGHT_INSET);
            hl.setHeight(ShipRenderer.TILE * HIGHLIGHT_INSET);
            hl.setPosition(goal.getX() * ShipRenderer.TILE, HIGHLIGHT_Y, goal.getY() * ShipRenderer.TILE);
            hl.setRotationX(-90f); // lay flat on the floor
            batch.add(hl);
        }
        for (Crew crew : ship.getCrew()) {
            Decal decal = decals.computeIfAbsent(crew, c -> Decal.newDecal(SIZE, SIZE, normalRegion, true));
            decal.setTextureRegion(selected.contains(crew) ? selectedRegion : normalRegion);
            decal.setPosition(crew.getPosition().x * ShipRenderer.TILE, HEIGHT, crew.getPosition().y * ShipRenderer.TILE);
            decal.lookAt(camera.position, camera.up); // billboard: always face the camera
            batch.add(decal);
        }
    }

    @Override
    public void dispose() {
        normalTexture.dispose();
        selectedTexture.dispose();
        highlightTexture.dispose();
    }
}
