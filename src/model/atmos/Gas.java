package model.atmos;

/**
 * The gases the atmosphere can contain. Each carries a base colour (for the room
 * overlay), a molar specific heat (J per mole per K, used for temperature) and a
 * visual opacity weight (how much it fogs the room when it dominates the mix).
 */
public enum Gas {
    AIR("Air", 0.55f, 0.78f, 1.00f, 20f, 0.22f),
    PHLOGISTON("Phlog", 0.35f, 0.05f, 0.55f, 200f, 0.55f),
    CO2("CO2", 0.50f, 0.50f, 0.52f, 30f, 0.70f);

    public final String label;
    public final float r;
    public final float g;
    public final float b;
    public final float specificHeat;
    public final float visualAlpha;

    Gas(String label, float r, float g, float b, float specificHeat, float visualAlpha) {
        this.label = label;
        this.r = r;
        this.g = g;
        this.b = b;
        this.specificHeat = specificHeat;
        this.visualAlpha = visualAlpha;
    }
}
