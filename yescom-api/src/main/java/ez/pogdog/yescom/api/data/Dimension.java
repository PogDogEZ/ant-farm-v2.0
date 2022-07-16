package ez.pogdog.yescom.api.data;

/**
 * Nicer than using numbers
 */
public enum Dimension {
    OVERWORLD(0),
    NETHER(-1),
    END(1);

    public static Dimension fromMC(int mcDim) {
        return new Dimension[] { NETHER, OVERWORLD, END }[Math.min(2, Math.max(0, mcDim + 1))];
    }

    private final int mcDim;

    Dimension(int mcDim) {
        this.mcDim = mcDim;
    }

    public int getMCDim() {
        return mcDim;
    }
}
