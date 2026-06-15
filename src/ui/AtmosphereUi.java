package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import input.Selection;
import model.atmos.Gas;
import model.atmos.GasMixture;
import model.atmos.Room;
import model.crew.Crew;
import model.graph.Ship;

/**
 * Bottom-left readout: the atmospherics target, and for the room of the first selected
 * crew (or the first room) its temperature, pressure and gas composition, plus a colour
 * legend. Informational for now; the atmospherics target is settable via its API.
 */
public class AtmosphereUi implements Disposable {
    private static final float LEFT = 212f;
    private static final float BOTTOM = 12f;

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Texture pixel;
    private final Ship ship;
    private final Selection selection;

    public AtmosphereUi(Ship ship, Selection selection) {
        this.ship = ship;
        this.selection = selection;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
    }

    public void render() {
        if (ship.getAtmosphere() == null || ship.getAtmosphere().rooms().isEmpty()) {
            return;
        }
        Room room = selectedRoom();
        if (room == null) {
            return;
        }
        GasMixture gas = room.gas();

        float x = LEFT;
        float y = BOTTOM + 52f;

        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.5f);
        batch.draw(pixel, x - 8f, BOTTOM - 6f, 360f, 76f);
        batch.setColor(Color.WHITE);

        font.setColor(Color.WHITE);
        font.draw(batch, String.format("ROOM  %.0f K   %.1f kPa",
                room.temperature(), room.pressure() / 1000.0), x, y);
        y -= 22f;

        double total = gas.totalMoles();
        float gx = x;
        for (Gas g : Gas.values()) {
            if (gas.moles(g) <= 0) {
                continue; // only show gases actually present
            }
            float pct = total > 0 ? (float) (100.0 * gas.moles(g) / total) : 0f;
            batch.setColor(g.r, g.g, g.b, 1f);
            batch.draw(pixel, gx, y - 9f, 11f, 11f);
            font.setColor(0.85f, 0.85f, 0.85f, 1f);
            font.draw(batch, String.format("%s %2.0f%%", g.label, pct), gx + 15f, y);
            gx += 110f;
        }
        font.setColor(Color.WHITE);
        batch.end();
    }

    private Room selectedRoom() {
        for (Crew crew : selection.getSelected()) {
            if (crew.getCurrentNode() != null && crew.getCurrentNode().getRoom() != null) {
                return crew.getCurrentNode().getRoom();
            }
        }
        return ship.getAtmosphere().rooms().get(0);
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
