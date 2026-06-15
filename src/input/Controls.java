package input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import model.crew.Crew;
import model.graph.Node;
import model.graph.Ship;
import model.system.ShipSystem;
import render.OrbitCameraController;
import render.ShipRenderer;
import ui.CrewListUi;
import ui.SystemWindows;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mouse/keyboard input: left click/drag selects crew, right click orders them to the
 * picked floor tile, middle drag orbits the camera, scroll zooms. Tiles are picked by
 * casting a ray from the cursor onto the floor plane.
 */
public class Controls extends InputAdapter {
    private static final float CREW_Y = 0.45f;
    private static final int DRAG_THRESHOLD = 6;
    private static final float PICK_RADIUS = 22f;

    private final PerspectiveCamera camera;
    private final OrbitCameraController orbit;
    private final Ship ship;
    private final Selection selection;
    private final CrewListUi ui;
    private final Bindings bindings;
    private SystemWindows windows;

    private final Map<InputAction, Runnable> actions = new EnumMap<>(InputAction.class);
    // y=0 ground plane that command-clicks are projected onto
    private final Plane floorPlane = new Plane(new Vector3(0f, 1f, 0f), 0f);
    private final Vector3 hit = new Vector3();
    private final Vector3 projected = new Vector3();
    private final Rectangle box = new Rectangle();

    private boolean leftDown;
    private boolean boxing;
    private int leftStartX;
    private int leftStartY;
    private int leftCurrentX;
    private int leftCurrentY;

    private boolean cameraDown;
    private int cameraLastX;
    private int cameraLastY;

    public Controls(PerspectiveCamera camera, OrbitCameraController orbit, Ship ship,
                    Selection selection, CrewListUi ui, Bindings bindings) {
        this.camera = camera;
        this.orbit = orbit;
        this.ship = ship;
        this.selection = selection;
        this.ui = ui;
        this.bindings = bindings;
    }

    public void on(InputAction action, Runnable handler) {
        actions.put(action, handler);
    }

    public void setWindows(SystemWindows windows) {
        this.windows = windows;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Keys.ESCAPE && windows != null && windows.closeTop()) {
            return true;
        }
        for (Map.Entry<InputAction, Runnable> entry : actions.entrySet()) {
            if (bindings.code(entry.getKey()) == keycode) {
                entry.getValue().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        if (button == bindings.code(InputAction.SELECT)) {
            if (windows != null && windows.touchDown(x, y)) {
                return true; // click landed on an open window
            }
            leftDown = true;
            boxing = false;
            leftStartX = leftCurrentX = x;
            leftStartY = leftCurrentY = y;
            return true;
        }
        if (button == bindings.code(InputAction.CAMERA_DRAG)) {
            cameraDown = true;
            cameraLastX = x;
            cameraLastY = y;
            return true;
        }
        if (button == bindings.code(InputAction.COMMAND)) {
            commandMove(x, y);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        if (windows != null && windows.touchDragged(x, y)) {
            return true;
        }
        if (cameraDown) {
            orbit.rotate(x - cameraLastX, y - cameraLastY);
            cameraLastX = x;
            cameraLastY = y;
            return true;
        }
        if (leftDown) {
            leftCurrentX = x;
            leftCurrentY = y;
            if (Math.abs(x - leftStartX) > DRAG_THRESHOLD || Math.abs(y - leftStartY) > DRAG_THRESHOLD) {
                boxing = true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        if (windows != null && windows.touchUp()) {
            return true;
        }
        if (button == bindings.code(InputAction.CAMERA_DRAG) && cameraDown) {
            cameraDown = false;
            return true;
        }
        if (button == bindings.code(InputAction.SELECT) && leftDown) {
            leftDown = false;
            boolean add = isAddToSelection();
            if (boxing) {
                boxSelect(add);
            } else {
                click(x, y, add);
            }
            boxing = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        orbit.zoom(amountY);
        return true;
    }

    public Rectangle getSelectionBox() {
        if (!boxing) {
            return null;
        }
        float bx = Math.min(leftStartX, leftCurrentX);
        float by = Math.min(leftStartY, leftCurrentY);
        box.set(bx, by, Math.abs(leftCurrentX - leftStartX), Math.abs(leftCurrentY - leftStartY));
        return box;
    }

    private void click(int x, int y, boolean add) {
        Crew uiCrew = ui.crewAt(x, y);
        if (uiCrew != null) {
            applySingle(uiCrew, add);
            return;
        }
        Crew worldCrew = pickCrew(x, y);
        if (worldCrew != null) {
            applySingle(worldCrew, add);
            return;
        }
        Node node = nodeAtScreen(x, y);
        ShipSystem system = node != null ? node.getSystem() : null;
        if (system != null && windows != null) {
            windows.openFor(system); // clicked a system tile -> open its window
        } else if (!add) {
            selection.clear();
        }
    }

    private void applySingle(Crew crew, boolean add) {
        if (add) {
            selection.toggle(crew);
        } else {
            selection.select(crew);
        }
    }

    private void boxSelect(boolean add) {
        float minX = Math.min(leftStartX, leftCurrentX);
        float maxX = Math.max(leftStartX, leftCurrentX);
        float minY = Math.min(leftStartY, leftCurrentY);
        float maxY = Math.max(leftStartY, leftCurrentY);
        if (!add) {
            selection.clear();
        }
        for (Crew crew : ship.getCrew()) {
            if (!projectToScreen(crew)) {
                continue;
            }
            float sx = projected.x;
            float sy = Gdx.graphics.getHeight() - projected.y;
            if (sx >= minX && sx <= maxX && sy >= minY && sy <= maxY) {
                selection.add(crew);
            }
        }
    }

    private Crew pickCrew(int x, int y) {
        Crew best = null;
        float bestDist = PICK_RADIUS;
        for (Crew crew : ship.getCrew()) {
            if (!projectToScreen(crew)) {
                continue;
            }
            float sx = projected.x;
            float sy = Gdx.graphics.getHeight() - projected.y;
            float d = (float) Math.hypot(sx - x, sy - y);
            if (d < bestDist) {
                bestDist = d;
                best = crew;
            }
        }
        return best;
    }

    private boolean projectToScreen(Crew crew) {
        projected.set(crew.getPosition().x * ShipRenderer.TILE, CREW_Y, crew.getPosition().y * ShipRenderer.TILE);
        camera.project(projected);
        return projected.z <= 1f;
    }

    private void commandMove(int x, int y) {
        Node node = nodeAtScreen(x, y);
        if (node == null) {
            return;
        }
        for (Crew crew : selection.getSelected()) {
            crew.setDestination(node);
        }
    }

    /** Ray-casts the cursor onto the floor plane and rounds the hit to the nearest tile. */
    private Node nodeAtScreen(int x, int y) {
        Ray ray = camera.getPickRay(x, y);
        if (Intersector.intersectRayPlane(ray, floorPlane, hit)) {
            int nx = Math.round(hit.x / ShipRenderer.TILE);
            int ny = Math.round(hit.z / ShipRenderer.TILE);
            return ship.nodeAt(nx, ny);
        }
        return null;
    }

    private boolean isAddToSelection() {
        return Gdx.input.isKeyPressed(bindings.code(InputAction.ADD_TO_SELECTION));
    }
}
