package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import model.crew.Crew;
import input.Selection;
import model.graph.Ship;

/** Left-hand crew roster panel plus the drag-selection box overlay, drawn in 2D. */
public class CrewListUi implements Disposable {
    private static final float PANEL_WIDTH = 200f;
    private static final float TOP = 30f;
    private static final float ROW_HEIGHT = 26f;

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Texture pixel;
    private final Ship ship;
    private final Selection selection;

    public CrewListUi(Ship ship, Selection selection) {
        this.ship = ship;
        this.selection = selection;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
    }

    public void render(Rectangle box) {
        float height = Gdx.graphics.getHeight();
        int count = ship.getCrew().size();
        float panelHeight = TOP + count * ROW_HEIGHT + 10f; // ends just below the last member
        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.5f);
        batch.draw(pixel, 0f, height - panelHeight, PANEL_WIDTH, panelHeight);
        batch.setColor(Color.WHITE);
        font.setColor(0.7f, 0.8f, 0.9f, 1f);
        font.draw(batch, "CREW", 12f, height - 8f);
        int i = 0;
        for (Crew member : ship.getCrew()) {
            font.setColor(selection.isSelected(member) ? Color.YELLOW : Color.WHITE);
            font.draw(batch, member.getName(), 12f, height - (TOP + i * ROW_HEIGHT));
            i++;
        }
        font.setColor(Color.WHITE);

        if (box != null) {
            // input y is top-down; flip to the bottom-up coords of the 2D batch
            float by = height - box.y - box.height;
            batch.setColor(0.4f, 0.7f, 1f, 0.15f);
            batch.draw(pixel, box.x, by, box.width, box.height);
            batch.setColor(0.4f, 0.7f, 1f, 0.7f);
            float t = 1.5f;
            batch.draw(pixel, box.x, by, box.width, t);
            batch.draw(pixel, box.x, by + box.height - t, box.width, t);
            batch.draw(pixel, box.x, by, t, box.height);
            batch.draw(pixel, box.x + box.width - t, by, t, box.height);
            batch.setColor(Color.WHITE);
        }
        batch.end();
    }

    public boolean contains(int screenX, int screenY) {
        return screenX <= PANEL_WIDTH;
    }

    public Crew crewAt(int screenX, int screenY) {
        if (screenX > PANEL_WIDTH || screenY < TOP) {
            return null;
        }
        int index = (int) ((screenY - TOP) / ROW_HEIGHT);
        if (index < 0 || index >= ship.getCrew().size()) {
            return null;
        }
        return ship.getCrew().get(index);
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
