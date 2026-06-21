package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import render.RetroFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import model.graph.Ship;

/** Top-of-screen HUD: power-per-tick label and a stored/max energy bar, drawn in 2D. */
public class EnergyUi implements Disposable {
    private static final float BAR_WIDTH = 320f;
    private static final float BAR_HEIGHT = 14f;
    private static final float HEADER_PAD = 6f;
    private static final float LINE = 18f;

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = RetroFont.pixel();
    private final Texture pixel;
    private final Ship ship;
    private float refreshTimer = 1f;
    private int powerShown;
    private int storedShown;

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
        float integrityY = height - HEADER_PAD;
        float massY = integrityY - LINE;
        float armorY = integrityY - 2f * LINE;
        float energyY = integrityY - 3f * LINE;
        float barTop = energyY - LINE;
        float barY = barTop - BAR_HEIGHT;
        float powerY = barY - 6f;

        int stored = ship.getStoredEnergy();
        int max = ship.getMaxStorage();
        float fill = max > 0 ? (float) stored / max : 0f;
        int maxHull = ship.getMaxHull();
        int integrityPct = maxHull > 0 ? Math.round(100f * ship.getHull() / maxHull) : 0;
        refreshTimer += Gdx.graphics.getDeltaTime();
        if (refreshTimer >= 1f) {
            refreshTimer = 0f;
            powerShown = ship.getProducedPerTick();
            storedShown = stored;
        }

        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.5f);
        batch.draw(pixel, x - 10f, powerY - 18f, BAR_WIDTH + 20f, height - (powerY - 18f));

        batch.setColor(0.15f, 0.17f, 0.22f, 1f);
        batch.draw(pixel, x, barY, BAR_WIDTH, BAR_HEIGHT);
        batch.setColor(0.30f, 0.80f, 1f, 1f);
        batch.draw(pixel, x, barY, BAR_WIDTH * fill, BAR_HEIGHT);
        batch.setColor(Color.WHITE);

        font.setColor(0.85f, 0.92f, 1f, 1f);
        font.draw(batch, "INTEGRITY  " + integrityPct + "%", x, integrityY);
        font.draw(batch, "MASS  " + String.format("%.1f", ship.getTotalMass() / 1000f) + " t", x, massY);
        font.draw(batch, "ARMOR  " + String.format("%.1f", ship.getArmor()), x, armorY);
        font.draw(batch, "ENERGY  " + energyText(storedShown) + " / " + energyText(max), x, energyY);
        font.draw(batch, "POWER  " + powerText(powerShown), x, powerY);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** Energy units (1 unit = 1 kJ) shown with the largest fitting SI prefix. */
    private static String energyText(int units) {
        if (units >= 1000000) {
            return String.format("%.1f GJ", units / 1000000f);
        }
        if (units >= 1000) {
            return String.format("%.1f MJ", units / 1000f);
        }
        return units + " kJ";
    }

    /** Power in units/s (1 unit/s = 1 kW) shown with the largest fitting SI prefix. */
    private static String powerText(int unitsPerSec) {
        String sign = unitsPerSec >= 0 ? "+" : "-";
        int v = Math.abs(unitsPerSec);
        if (v >= 1000000) {
            return sign + String.format("%.1f GW", v / 1000000f);
        }
        if (v >= 1000) {
            return sign + String.format("%.1f MW", v / 1000f);
        }
        return sign + v + " kW";
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
