package render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Loads the generated hard-edged pixel font and keeps any font crisp by forcing nearest filtering. */
public final class RetroFont {
    private RetroFont() {}

    public static BitmapFont pixel() {
        BitmapFont font = new BitmapFont(Gdx.files.internal("assets/fonts/pixel.fnt"), false);
        font.getData().setScale(2f); // smaller on-screen size; raise toward SCALE (3) for pixel-perfect alignment, lower for smaller text
        return nearest(font);
    }

    public static BitmapFont nearest(BitmapFont font) {
        for (TextureRegion region : font.getRegions()) { region.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); }
        return font;
    }
}
