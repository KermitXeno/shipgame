import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import input.Bindings;
import input.Controls;
import input.Selection;
import model.graph.Ship;
import model.io.ShipLoader;
import render.AtmosphereRenderer;
import render.CrewRenderer;
import render.OrbitCameraController;
import render.ShipRenderer;
import render.SystemRenderer;
import ui.AtmosphereUi;
import ui.CrewListUi;
import ui.EnergyUi;
import ui.SystemWindows;

/**
 * Application entry point and game loop. Loads the ship from JSON, wires up the
 * renderers, UI and input, then each frame ticks the ship and draws in order:
 * solid geometry (ModelBatch) -> floor/crew decals (DecalBatch) -> 2D UI overlay.
 */
public class Main extends ApplicationAdapter {
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private DecalBatch decalBatch;
    private Environment environment;

    private Ship ship;
    private ShipRenderer shipRenderer;
    private CrewRenderer crewRenderer;
    private SystemRenderer systemRenderer;
    private AtmosphereRenderer atmosphereRenderer;
    private Selection selection;
    private CrewListUi ui;
    private EnergyUi energyUi;
    private AtmosphereUi atmosphereUi;
    private SystemWindows systemWindows;
    private Controls controls;

    @Override
    public void create() {
        ship = ShipLoader.load(Gdx.files.internal("assets/ships/basic_ship.json"));
        selection = new Selection();

        shipRenderer = new ShipRenderer(ship);
        crewRenderer = new CrewRenderer();
        systemRenderer = new SystemRenderer();
        ui = new CrewListUi(ship, selection);
        energyUi = new EnergyUi(ship);
        atmosphereUi = new AtmosphereUi(ship, selection);
        systemWindows = new SystemWindows(ship);

        camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 100f;
        OrbitCameraController orbit = new OrbitCameraController(camera, shipRenderer.getCenter());

        DefaultShader.Config shaderConfig = new DefaultShader.Config();
        shaderConfig.numPointLights = AtmosphereRenderer.MAX_LIGHTS;
        modelBatch = new ModelBatch(new DefaultShaderProvider(shaderConfig));
        decalBatch = new DecalBatch(new CameraGroupStrategy(camera));

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -0.5f, -1f, -0.3f));
        atmosphereRenderer = new AtmosphereRenderer(ship, environment);

        Bindings bindings = new Bindings();
        bindings.load(Gdx.files.internal("assets/config/controls.json"));

        controls = new Controls(camera, orbit, ship, selection, ui, bindings);
        controls.setWindows(systemWindows);
        Gdx.input.setInputProcessor(controls);
    }

    @Override
    public void render() {
        ship.tick(Gdx.graphics.getDeltaTime());

        atmosphereRenderer.updateLights(); // set gas lights before the hull is lit

        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        shipRenderer.render(modelBatch, environment);
        modelBatch.end();

        // floor decals first, then crew billboards; one shared flush depth-sorts them
        systemRenderer.render(decalBatch, camera, ship);
        crewRenderer.render(decalBatch, camera, ship, selection.getSelected());
        decalBatch.flush();

        // gas last, translucent and depth-write-off, so decals and crew show through it
        modelBatch.begin(camera);
        atmosphereRenderer.renderGas(modelBatch, environment);
        modelBatch.end();

        systemRenderer.renderLabels(camera, ship);

        ui.render(controls.getSelectionBox());
        energyUi.render();
        atmosphereUi.render();
        systemWindows.render();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        ui.resize(width, height);
        energyUi.resize(width, height);
        atmosphereUi.resize(width, height);
        systemWindows.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        decalBatch.dispose();
        shipRenderer.dispose();
        crewRenderer.dispose();
        systemRenderer.dispose();
        atmosphereRenderer.dispose();
        ui.dispose();
        energyUi.dispose();
        atmosphereUi.dispose();
        systemWindows.dispose();
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Ship Game");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new Main(), config);
    }
}
