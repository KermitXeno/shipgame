package model.system;

/**
 * A system's floor marker, declared in the system's own class: either a texture path
 * (loaded if it exists) or an RGBA tint used to generate a placeholder marker.
 */
public record SystemDecal(String texturePath, float r, float g, float b, float a, String label) {
    public static SystemDecal color(String label, float r, float g, float b) {
        return new SystemDecal(null, r, g, b, 1f, label);
    }

    public static SystemDecal texture(String texturePath, String label) {
        return new SystemDecal(texturePath, 1f, 1f, 1f, 1f, label);
    }
}
