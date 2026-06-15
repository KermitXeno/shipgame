package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import model.atmos.Gas;
import model.graph.Ship;
import model.system.AtmosphericsSystem;
import model.system.EngineSystem;
import model.system.GasStorageSystem;
import model.system.ShipSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating, draggable info/control windows for ship systems. Clicking a system opens its
 * window; Escape closes the most-recently-opened one. Tunable scalars use [-]/[+] buttons;
 * gas mixes use a stacked colour bar whose segment boundaries can be dragged to set each
 * gas's share. All input is in screen coords (origin top-left, y down); drawing flips to
 * the 2D batch's y-up, with text vertically centred in its band.
 */
public class SystemWindows implements Disposable {
    private static final float W = 256f;
    private static final float TH = 26f;   // title bar height
    private static final float RH = 24f;   // row height
    private static final float PAD = 8f;
    private static final float BTN = 20f;  // +/- button size
    private static final float CLOSE = 16f;
    private static final float TXT = 15f;  // approx text height, for vertical centring
    private static final float BAR_H = 12f;
    private static final float GRAB = 7f;  // mix-boundary grab radius

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Texture pixel;
    private final Ship ship;
    private final List<Win> windows = new ArrayList<>();

    private Win dragging;
    private float dragDX;
    private float dragDY;

    private MixModel mixDrag;   // a mix bar boundary currently being dragged
    private int mixDragK;       // boundary index (between gas k and k+1)
    private float mixBarX;
    private float mixBarW;

    public SystemWindows(Ship ship) {
        this.ship = ship;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
    }

    /** Read/write access to a system's per-gas target mix (engine chamber or atmospherics). */
    private interface MixModel {
        double get(Gas gas);

        void set(Gas gas, double moles);
    }

    private static final class Win {
        final ShipSystem system;
        float x;
        float y;

        Win(ShipSystem system, float x, float y) {
            this.system = system;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Row {
        String label;
        String value;
        Runnable minus;
        Runnable plus;
        MixModel mix;
        boolean bar;
        float barFrac;

        static Row text(String label) {
            Row r = new Row();
            r.label = label;
            return r;
        }

        static Row adjust(String label, String value, Runnable minus, Runnable plus) {
            Row r = new Row();
            r.label = label;
            r.value = value;
            r.minus = minus;
            r.plus = plus;
            return r;
        }

        static Row bar(String label, String value, float frac) {
            Row r = new Row();
            r.label = label;
            r.value = value;
            r.bar = true;
            r.barFrac = frac;
            return r;
        }

        static Row mix(MixModel model) {
            Row r = new Row();
            r.mix = model;
            return r;
        }
    }

    public void openFor(ShipSystem system) {
        for (Win w : windows) {
            if (w.system == system) {
                bringToFront(w);
                return;
            }
        }
        float offset = windows.size() * 28f;
        windows.add(new Win(system, 320f + offset, 70f + offset));
    }

    public boolean closeTop() {
        if (windows.isEmpty()) {
            return false;
        }
        windows.remove(windows.size() - 1);
        return true;
    }

    public boolean hasWindows() {
        return !windows.isEmpty();
    }

    // ---- input (screen coords, y down) ----

    public boolean touchDown(int px, int py) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            Win w = windows.get(i);
            if (!contains(w, px, py)) {
                continue;
            }
            bringToFront(w);
            if (inRect(px, py, w.x + W - PAD - CLOSE, w.y + (TH - CLOSE) / 2f, CLOSE, CLOSE)) {
                windows.remove(w);
                return true;
            }
            if (py <= w.y + TH) {
                dragging = w;
                dragDX = px - w.x;
                dragDY = py - w.y;
                return true;
            }
            handleRowClick(w, px, py);
            return true; // clicks inside a window are always consumed
        }
        return false;
    }

    private void handleRowClick(Win w, int px, int py) {
        List<Row> rows = build(w);
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            float top = w.y + TH + r * RH;
            if (row.mix != null) {
                if (py >= top && py <= top + RH) {
                    grabMixBoundary(row.mix, w.x + 10f, W - 20f, px);
                }
                continue;
            }
            if (row.minus == null) {
                continue;
            }
            float by = top + (RH - BTN) / 2f;
            if (inRect(px, py, minusX(w), by, BTN, BTN)) {
                row.minus.run();
                return;
            }
            if (inRect(px, py, plusX(w), by, BTN, BTN)) {
                row.plus.run();
                return;
            }
        }
    }

    private void grabMixBoundary(MixModel mix, float barX, float barW, int px) {
        double total = sumMix(mix);
        if (total <= 1e-9) {
            return;
        }
        Gas[] gases = Gas.values();
        double cum = 0;
        for (int k = 0; k < gases.length - 1; k++) {
            cum += mix.get(gases[k]);
            float bx = barX + barW * (float) (cum / total);
            if (Math.abs(px - bx) <= GRAB) {
                mixDrag = mix;
                mixDragK = k;
                mixBarX = barX;
                mixBarW = barW;
                return;
            }
        }
    }

    public boolean touchDragged(int px, int py) {
        if (mixDrag != null) {
            Gas[] gases = Gas.values();
            double total = sumMix(mixDrag);
            if (total > 1e-9) {
                double frac = Math.max(0, Math.min(1, (px - mixBarX) / mixBarW));
                double cumBelow = 0;
                for (int i = 0; i <= mixDragK; i++) {
                    cumBelow += mixDrag.get(gases[i]);
                }
                double desired = frac * total;
                double delta = desired - cumBelow;
                Gas a = gases[mixDragK];
                Gas b = gases[mixDragK + 1];
                delta = Math.max(-mixDrag.get(a), Math.min(delta, mixDrag.get(b)));
                mixDrag.set(a, mixDrag.get(a) + delta);
                mixDrag.set(b, mixDrag.get(b) - delta);
            }
            return true;
        }
        if (dragging != null) {
            dragging.x = px - dragDX;
            dragging.y = py - dragDY;
            return true;
        }
        return false;
    }

    public boolean touchUp() {
        if (mixDrag != null) {
            mixDrag = null;
            return true;
        }
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return false;
    }

    private void bringToFront(Win w) {
        windows.remove(w);
        windows.add(w);
    }

    private boolean contains(Win w, float px, float py) {
        return inRect(px, py, w.x, w.y, W, TH + build(w).size() * RH + PAD);
    }

    private static boolean inRect(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private float minusX(Win w) {
        return w.x + W - PAD - BTN - 46f - BTN;
    }

    private float plusX(Win w) {
        return w.x + W - PAD - BTN;
    }

    // ---- rendering ----

    public void render() {
        if (windows.isEmpty()) {
            return;
        }
        float h = Gdx.graphics.getHeight();
        batch.begin();
        for (Win w : windows) {
            List<Row> rows = build(w);
            float winH = TH + rows.size() * RH + PAD;

            batch.setColor(0.06f, 0.07f, 0.09f, 0.92f);
            box(w.x, w.y, W, winH, h);
            batch.setColor(0.16f, 0.20f, 0.28f, 1f);
            box(w.x, w.y, W, TH, h);

            drawTitle(w, h);
            batch.setColor(0.55f, 0.30f, 0.30f, 1f);
            box(w.x + W - PAD - CLOSE, w.y + (TH - CLOSE) / 2f, CLOSE, CLOSE, h);
            font.setColor(1f, 0.85f, 0.85f, 1f);
            font.draw(batch, "x", w.x + W - PAD - CLOSE + 5f, centre(w.y + (TH - CLOSE) / 2f, CLOSE, h));

            for (int r = 0; r < rows.size(); r++) {
                drawRow(w, rows.get(r), w.y + TH + r * RH, h);
            }
        }
        font.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawTitle(Win w, float h) {
        float baseline = centre(w.y, TH, h);
        if (w.system instanceof GasStorageSystem tank) {
            Gas g = tank.storedGas();
            font.setColor(g.r, g.g, g.b, 1f);
            font.draw(batch, g.label, w.x + 10f, baseline);
            layout.setText(font, g.label);
            font.setColor(0.90f, 0.80f, 0.35f, 1f); // "tank" in the tank's yellow
            font.draw(batch, " tank", w.x + 10f + layout.width, baseline);
        } else {
            font.setColor(0.85f, 0.92f, 1f, 1f);
            font.draw(batch, w.system.decal().label(), w.x + 10f, baseline);
        }
    }

    private void drawRow(Win w, Row row, float top, float h) {
        float baseline = centre(top, RH, h);
        if (row.mix != null) {
            drawMix(w, row.mix, top, h);
            return;
        }
        font.setColor(0.80f, 0.84f, 0.90f, 1f);
        font.draw(batch, row.label, w.x + 10f, baseline);
        if (row.bar) {
            float bx = w.x + 10f;
            float bw = W - 20f;
            float barTop = top + (RH - BAR_H) / 2f;
            batch.setColor(0.15f, 0.17f, 0.22f, 1f);
            box(bx, barTop, bw, BAR_H, h);
            batch.setColor(0.30f, 0.80f, 1f, 1f);
            box(bx, barTop, bw * row.barFrac, BAR_H, h);
            if (row.value != null && !row.value.isEmpty()) {
                font.setColor(0.90f, 0.94f, 1f, 1f);
                font.draw(batch, row.value, w.x + W - 10f - 90f, baseline);
            }
        } else if (row.minus != null) {
            float by = top + (RH - BTN) / 2f;
            float bl = centre(by, BTN, h);
            batch.setColor(0.22f, 0.26f, 0.34f, 1f);
            box(minusX(w), by, BTN, BTN, h);
            box(plusX(w), by, BTN, BTN, h);
            font.setColor(1f, 1f, 1f, 1f);
            font.draw(batch, "-", minusX(w) + 7f, bl);
            font.draw(batch, "+", plusX(w) + 6f, bl);
            font.setColor(0.90f, 0.94f, 1f, 1f);
            font.draw(batch, row.value, minusX(w) + BTN + 6f, bl);
        }
    }

    private void drawMix(Win w, MixModel mix, float top, float h) {
        float barX = w.x + 10f;
        float barW = W - 20f;
        float barTop = top + (RH - BAR_H) / 2f;
        double total = sumMix(mix);
        batch.setColor(0.13f, 0.15f, 0.19f, 1f);
        box(barX, barTop, barW, BAR_H, h);
        if (total <= 1e-9) {
            return;
        }
        Gas[] gases = Gas.values();
        double cum = 0;
        for (Gas g : gases) {
            float segX = barX + barW * (float) (cum / total);
            float segW = barW * (float) (mix.get(g) / total);
            batch.setColor(g.r, g.g, g.b, 1f);
            box(segX, barTop, segW, BAR_H, h);
            cum += mix.get(g);
        }
        // boundary handles
        cum = 0;
        for (int k = 0; k < gases.length - 1; k++) {
            cum += mix.get(gases[k]);
            float bx = barX + barW * (float) (cum / total);
            batch.setColor(0.95f, 0.95f, 1f, 1f);
            box(bx - 1f, barTop - 2f, 2f, BAR_H + 4f, h);
        }
    }

    /** y-up baseline that vertically centres text of height TXT in a band [topDown, topDown+bandH]. */
    private float centre(float topDown, float bandH, float screenH) {
        return screenH - topDown - (bandH - TXT) / 2f;
    }

    private void box(float sx, float syTop, float w, float h, float screenH) {
        batch.draw(pixel, sx, screenH - syTop - h, w, h);
    }

    // ---- per-system content ----

    private List<Row> build(Win w) {
        ShipSystem s = w.system;
        if (s instanceof EngineSystem e) {
            return engineRows(e);
        }
        if (s instanceof AtmosphericsSystem a) {
            return atmosRows(a);
        }
        if (s instanceof GasStorageSystem t) {
            return tankRows(t);
        }
        if (s.id().equals("battery")) {
            return batteryRows();
        }
        if (s.id().equals("helm")) {
            return helmRows();
        }
        return List.of(Row.text("No controls."));
    }

    private List<Row> engineRows(EngineSystem e) {
        MixModel mix = mixOf(e);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.adjust("Heat to", String.format("%.0fK", e.getHeatingTemperature()),
                () -> e.setHeatingTemperature(e.getHeatingTemperature() - 25),
                () -> e.setHeatingTemperature(e.getHeatingTemperature() + 25)));
        rows.add(Row.adjust("Cool above", String.format("%.0fK", e.getCoolingTemperature()),
                () -> e.setCoolingTemperature(e.getCoolingTemperature() - 25),
                () -> e.setCoolingTemperature(e.getCoolingTemperature() + 25)));
        rows.add(Row.adjust("Target pressure", String.format("%.0fkPa", sumMix(mix)),
                () -> scaleMix(mix, -25), () -> scaleMix(mix, 25)));
        rows.add(Row.mix(mix));
        rows.add(Row.adjust("Exhaust", e.isVentToSpace() ? "Space" : "Tank",
                e::toggleVentToSpace, e::toggleVentToSpace));
        rows.add(Row.text(String.format("Chamber  %.0fK  %.0fkPa", e.chamber().temperature(), e.chamberPressure())));
        rows.add(Row.text(String.format("Rated  %.0fK  %.0fkPa", e.getHeatThreshold(), e.getPressureThreshold())));
        rows.add(Row.text(String.format("Power  %+d / s", e.generationPerTick())));
        return rows;
    }

    private List<Row> atmosRows(AtmosphericsSystem a) {
        MixModel mix = mixOf(a);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.adjust("Target temp", String.format("%.0fK", a.getTargetTemperature()),
                () -> a.setTargetTemperature(a.getTargetTemperature() - 5),
                () -> a.setTargetTemperature(a.getTargetTemperature() + 5)));
        rows.add(Row.adjust("Target pressure", String.format("%.0fkPa", sumMix(mix)),
                () -> scaleMix(mix, -5), () -> scaleMix(mix, 5)));
        rows.add(Row.mix(mix));
        rows.add(Row.text(String.format("Power  %+d / s", a.generationPerTick())));
        return rows;
    }

    private List<Row> tankRows(GasStorageSystem t) {
        Gas g = t.storedGas();
        List<Row> rows = new ArrayList<>();
        rows.add(Row.adjust("Target temp", String.format("%.0fK", t.getTargetTemperature()),
                () -> t.setTargetTemperature(t.getTargetTemperature() - 5),
                () -> t.setTargetTemperature(t.getTargetTemperature() + 5)));
        rows.add(Row.text(String.format("Stores  %s  %.0f mol", g.label, t.tank().moles(g))));
        rows.add(Row.text(String.format("Heat  %.0f K", t.tank().temperature())));
        rows.add(Row.text(String.format("Pressure  %.0f kPa", t.tankPressure())));
        rows.add(Row.text(String.format("Rated  %.0fK  %.0fkPa", t.getHeatTolerance(), t.getPressureTolerance())));
        rows.add(Row.text(String.format("Power  %+d / s", t.generationPerTick())));
        return rows;
    }

    private List<Row> batteryRows() {
        int stored = ship.getStoredEnergy();
        int max = ship.getMaxStorage();
        float frac = max > 0 ? (float) stored / max : 0f;
        List<Row> rows = new ArrayList<>();
        rows.add(Row.text("Charge  " + stored + " / " + max));
        rows.add(Row.bar("", "", frac)); // bar on its own row so the readout isn't covered
        return rows;
    }

    private List<Row> helmRows() {
        List<ShipSystem> powered = new ArrayList<>();
        for (ShipSystem s : ship.getSystems()) {
            if (s.generationPerTick() != 0) {
                powered.add(s);
            }
        }
        powered.sort((p, q) -> Integer.compare(q.generationPerTick(), p.generationPerTick()));
        List<Row> rows = new ArrayList<>();
        for (ShipSystem s : powered) {
            rows.add(Row.text(String.format("%-12s %+d", s.decal().label(), s.generationPerTick())));
        }
        if (powered.isEmpty()) {
            rows.add(Row.text("No active systems."));
        }
        rows.add(Row.text(String.format("%-12s %+d", "TOTAL", ship.getProducedPerTick())));
        return rows;
    }

    private static MixModel mixOf(EngineSystem e) {
        return new MixModel() {
            public double get(Gas g) {
                return e.getTargetMix(g);
            }

            public void set(Gas g, double moles) {
                e.setTargetMix(g, moles);
            }
        };
    }

    private static MixModel mixOf(AtmosphericsSystem a) {
        return new MixModel() {
            public double get(Gas g) {
                return a.getTargetPerTile(g);
            }

            public void set(Gas g, double moles) {
                a.setTargetPerTile(g, moles);
            }
        };
    }

    private static double sumMix(MixModel mix) {
        double total = 0;
        for (Gas g : Gas.values()) {
            total += mix.get(g);
        }
        return total;
    }

    /** Scales every gas by the same factor to change the total while keeping proportions. */
    private static void scaleMix(MixModel mix, double step) {
        double total = sumMix(mix);
        double next = Math.max(0, total + step);
        if (total > 1e-9) {
            double factor = next / total;
            for (Gas g : Gas.values()) {
                mix.set(g, mix.get(g) * factor);
            }
        } else if (next > 0) {
            mix.set(Gas.AIR, next);
        }
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
