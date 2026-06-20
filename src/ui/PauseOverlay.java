package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/** Drawn over everything while the game is paused: a faint grey screen filter and a PAUSED label. */
public class PauseOverlay implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Texture pixel;

    public PauseOverlay() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
    }

    public void render() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        batch.begin();
        batch.setColor(0.5f, 0.5f, 0.55f, 0.22f); // small grey filter over the whole screen
        batch.draw(pixel, 0f, 0f, width, height);
        batch.setColor(Color.WHITE);
        font.getData().setScale(2f);
        layout.setText(font, "PAUSED");
        float x = (width - layout.width) / 2f;
        float y = height - 40f;
        font.setColor(0f, 0f, 0f, 0.6f);
        font.draw(batch, "PAUSED", x + 2f, y - 2f); // shadow for legibility over any background
        font.setColor(0.92f, 0.95f, 1f, 0.95f);
        font.draw(batch, "PAUSED", x, y);
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
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
