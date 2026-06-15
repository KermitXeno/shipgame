package model.system;

/** A width x height footprint in tiles, with a rotation-aware bounds check. */
public record Dimension(int width, int height) {
    /** True if this footprint fits [min, max] in either orientation (1x2 and 2x1 are equivalent). */
    public boolean fitsWithin(Dimension min, Dimension max) {
        return within(width, height, min, max) || within(height, width, min, max);
    }

    private static boolean within(int w, int h, Dimension min, Dimension max) {
        return w >= min.width && w <= max.width && h >= min.height && h <= max.height;
    }
}
