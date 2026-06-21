package render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;

/**
 * Renders the 3D scene into a small offscreen buffer, then blows it back up to the window with
 * nearest-neighbour filtering. The downscale gives the chunky low-resolution look and the hard
 * upscale gives the jagged, un-antialiased edges of a PS1-era render. Wrap the 3D passes between
 * {@link #begin()} and {@link #end()}; draw 2D UI afterwards so it stays crisp and readable.
 */
public class RetroRenderer implements Disposable {
    private static final int SCALE = 2; // window pixels per low-res pixel (higher = chunkier)

    private final SpriteBatch batch = new SpriteBatch();
    private FrameBuffer fbo;
    private int screenWidth;
    private int screenHeight;

    public RetroRenderer(int width, int height) {
        resize(width, height);
    }

    /** Bind the low-res buffer and clear it; the 3D scene draws into it at reduced resolution. */
    public void begin() {
        fbo.begin();
        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /** Unbind and stretch the low-res buffer over the whole window with no smoothing. */
    public void end() {
        fbo.end();
        Texture texture = fbo.getColorBufferTexture();
        batch.begin();
        batch.draw(texture, 0f, 0f, screenWidth, screenHeight, 0f, 0f, 1f, 1f); // this draw overload maps screen-bottom->v, screen-top->v2, so the FBO comes out upright as-is
        batch.end();
    }

    public void resize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        if (fbo != null) {
            fbo.dispose();
        }
        int lowWidth = Math.max(1, width / SCALE);
        int lowHeight = Math.max(1, height / SCALE);
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, lowWidth, lowHeight, true); // true: include a depth buffer for the 3D pass
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void dispose() {
        if (fbo != null) {
            fbo.dispose();
        }
        batch.dispose();
    }
}
