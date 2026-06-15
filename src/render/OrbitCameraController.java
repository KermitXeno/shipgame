package render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Orbits the camera around a fixed target via spherical coords (azimuth, elevation, distance). */
public class OrbitCameraController {
    private final PerspectiveCamera camera;
    private final Vector3 target = new Vector3();

    private float distance = 6f;
    private float azimuth = 45f;
    private float elevation = 55f;

    private final float maxDistance = 25f;
    private final float minElevation = 15f;
    private final float maxElevation = 85f;
    private final float rotateSpeed = 0.3f;
    private final float zoomSpeed = 1f;

    public OrbitCameraController(PerspectiveCamera camera, Vector3 target) {
        this.camera = camera;
        this.target.set(target);
        update();
    }

    public void rotate(float deltaX, float deltaY) {
        azimuth -= deltaX * rotateSpeed;
        elevation = MathUtils.clamp(elevation + deltaY * rotateSpeed, minElevation, maxElevation);
        update();
    }

    public void zoom(float amount) {
        float minDistance = 2f;
        distance = MathUtils.clamp(distance + amount * zoomSpeed, minDistance, maxDistance);
        update();
    }

    /** Recomputes the camera position from the current angles and distance. */
    public void update() {
        float az = azimuth * MathUtils.degreesToRadians;
        float el = elevation * MathUtils.degreesToRadians;
        float horizontal = MathUtils.cos(el) * distance;
        camera.position.set(
                target.x + horizontal * MathUtils.sin(az),
                target.y + MathUtils.sin(el) * distance,
                target.z + horizontal * MathUtils.cos(az));
        camera.up.set(0f, 1f, 0f);
        camera.lookAt(target);
        camera.update();
    }
}
