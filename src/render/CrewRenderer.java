package render;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.utils.Disposable;
import model.crew.Crew;
import model.graph.Ship;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Draws each crew member as a camera-facing billboard decal, generated procedurally (no assets). */
public class CrewRenderer implements Disposable {
    private static final float SIZE = 0.6f;
    private static final float HEIGHT = 0.45f;

    private final Texture normalTexture;
    private final Texture selectedTexture;
    private final TextureRegion normalRegion;
    private final TextureRegion selectedRegion;
    private final Map<Crew, Decal> decals = new HashMap<>();

    public CrewRenderer() {
        normalTexture = makeCircle(false);
        selectedTexture = makeCircle(true);
        normalRegion = new TextureRegion(normalTexture);
        selectedRegion = new TextureRegion(selectedTexture);
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
            Decal decal = decals.computeIfAbsent(crew, c -> Decal.newDecal(SIZE, SIZE, normalRegion, true));
            decal.setTextureRegion(selected.contains(crew) ? selectedRegion : normalRegion);
            decal.setPosition(crew.getPosition().x * ShipRenderer.TILE, HEIGHT, crew.getPosition().y * ShipRenderer.TILE);
            decal.lookAt(camera.position, camera.up); // billboard: always face the camera
            decal.lookAt(camera.position, camera.up);
            batch.add(decal);
        }
    }

    @Override
    public void dispose() {
        normalTexture.dispose();
        selectedTexture.dispose();
    }
}
