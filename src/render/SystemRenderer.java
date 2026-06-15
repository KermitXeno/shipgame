package render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import model.graph.Ship;
import model.system.ShipSystem;
import model.system.SystemDecal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marks each placed system. A system whose decal sets a texture path is drawn as a flat
 * floor image (the path for future custom decals); a system with no texture path is drawn
 * as a screen-space text label over its room, tinted by the decal colour. The label pass
 * ({@link #renderLabels}) runs in the 2D phase, after the 3D scene.
 */
public class SystemRenderer implements Disposable {
    private static final float FLOOR_Y = 0.012f;
    private static final float INSET = 0.85f;

    private final Map<String, TextureRegion> regions = new HashMap<>();
    private final List<Texture> textures = new ArrayList<>();
    private final Map<ShipSystem, Decal> decals = new HashMap<>();

    private final SpriteBatch labelBatch = new SpriteBatch();
    private final BitmapFont labelFont = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 screen = new Vector3();

    /** Floor image decals only (systems that set a texture path). Text labels are in renderLabels. */
    public void render(DecalBatch batch, Camera camera, Ship ship) {
        for (ShipSystem system : ship.getSystems()) {
            if (system.tileCount() == 0 || system.decal().texturePath() == null) {
                continue;
            }
            Decal decal = decals.computeIfAbsent(system, this::createDecal);
            decal.setWidth(system.width() * ShipRenderer.TILE * INSET);
            decal.setHeight(system.height() * ShipRenderer.TILE * INSET);
            decal.setPosition(system.centerX() * ShipRenderer.TILE, FLOOR_Y, system.centerY() * ShipRenderer.TILE);
            decal.setRotationX(-90f); // lay the billboard flat, facing up from the floor
            batch.add(decal);
        }
    }

    /** Draws a text label over each system that has no custom image decal. */
    public void renderLabels(Camera camera, Ship ship) {
        labelBatch.getProjectionMatrix().setToOrtho2D(0f, 0f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        labelBatch.begin();
        for (ShipSystem system : ship.getSystems()) {
            SystemDecal config = system.decal();
            if (system.tileCount() == 0 || config.texturePath() != null) {
                continue;
            }
            screen.set(system.centerX() * ShipRenderer.TILE, FLOOR_Y, system.centerY() * ShipRenderer.TILE);
            camera.project(screen);
            if (screen.z < 0f || screen.z > 1f) {
                continue; // behind the camera or clipped
            }
            String text = config.label() == null ? system.id() : config.label();
            layout.setText(labelFont, text);
            float x = screen.x - layout.width / 2f;
            float y = screen.y + layout.height / 2f;
            labelFont.setColor(0f, 0f, 0f, 0.6f);
            labelFont.draw(labelBatch, text, x + 1f, y - 1f); // shadow for readability
            labelFont.setColor(config.r(), config.g(), config.b(), 1f);
            labelFont.draw(labelBatch, text, x, y);
        }
        labelFont.setColor(Color.WHITE);
        labelBatch.end();
    }

    private Decal createDecal(ShipSystem system) {
        SystemDecal config = system.decal();
        TextureRegion region = regions.computeIfAbsent(config.texturePath(), k -> load(config));
        return Decal.newDecal(1f, 1f, region, true);
    }

    private TextureRegion load(SystemDecal config) {
        Texture texture;
        if (Gdx.files.internal(config.texturePath()).exists()) {
            texture = new Texture(Gdx.files.internal(config.texturePath()));
        } else {
            texture = generate(config); // texture path set but missing -> tinted marker
        }
        textures.add(texture);
        return new TextureRegion(texture);
    }

    /** Fallback marker for a missing custom decal: a translucent tinted square with a border. */
    private Texture generate(SystemDecal config) {
        int size = 64;
        int border = 5;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();
        pm.setColor(config.r(), config.g(), config.b(), config.a() * 0.30f);
        pm.fillRectangle(border, border, size - border * 2, size - border * 2);
        pm.setColor(config.r(), config.g(), config.b(), config.a());
        pm.fillRectangle(border, border, size - border * 2, border);
        pm.fillRectangle(border, size - border * 2, size - border * 2, border);
        pm.fillRectangle(border, border, border, size - border * 2);
        pm.fillRectangle(size - border * 2, border, border, size - border * 2);
        Texture texture = new Texture(pm);
        pm.dispose();
        return texture;
    }

    @Override
    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        labelBatch.dispose();
        labelFont.dispose();
    }
}
