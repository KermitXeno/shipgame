package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import model.graph.Ship;

/** Top-of-screen HUD: power-per-tick label and a stored/max energy bar, drawn in 2D. */
public class EnergyUi implements Disposable {
    private static final float BAR_WIDTH = 320f;
    private static final float BAR_HEIGHT = 14f;
    private static final float TOP_MARGIN = 14f;

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Texture pixel;
    private final Ship ship;

    public EnergyUi(Ship ship) {
        this.ship = ship;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
    }

    public void render() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        float x = (width - BAR_WIDTH) / 2f;
        float y = height - TOP_MARGIN - BAR_HEIGHT;

        int stored = ship.getStoredEnergy();
        int max = ship.getMaxStorage();
        float fill = max > 0 ? (float) stored / max : 0f;

        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.5f);
        batch.draw(pixel, x - 10f, y - 26f, BAR_WIDTH + 20f, BAR_HEIGHT + 48f);

        batch.setColor(0.15f, 0.17f, 0.22f, 1f);
        batch.draw(pixel, x, y, BAR_WIDTH, BAR_HEIGHT);
        batch.setColor(0.30f, 0.80f, 1f, 1f);
        batch.draw(pixel, x, y, BAR_WIDTH * fill, BAR_HEIGHT);
        batch.setColor(Color.WHITE);

        font.setColor(0.85f, 0.92f, 1f, 1f);
        font.draw(batch, "ENERGY  " + stored + " / " + max, x, y + BAR_HEIGHT + 18f);
        int gen = ship.getGeneratedPerSecond();
        int use = ship.getConsumedPerSecond();
        int net = gen - use;
        font.draw(batch, "NET POWER: " + net + "/s", x, y - 8f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        pixel.dispose();
    }
}
