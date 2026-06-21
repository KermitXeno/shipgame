package model.system;

/** Placeholder cockpit: a single fixed 1x2 room, one per ship, no behaviour yet. */
public class HelmSystem extends ShipSystem {
    private static final Dimension SIZE = new Dimension(1, 2);

    @Override
    public String id() {
        return "helm";
    }

    @Override
    public Dimension minDimension() {
        return SIZE;
    }

    @Override
    public Dimension maxDimension() {
        return SIZE;
    }

    @Override
    public int maxPerShip() {
        return 1;
    }

    @Override
    public SystemDecal decal() {
        return SystemDecal.color("HELM", 0.45f, 0.70f, 0.95f);
    }

    @Override
    public double equipmentMass() {
        return 300;
    }
}
