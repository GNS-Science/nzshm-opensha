package nz.cri.gns.NZSHM22.opensha.timeDependent;

public enum Aperiodicity {

    UCERF3_MID(new double[]{0.5, 0.4, 0.3, 0.2}, new double[]{6.7, 7.2, 7.7}),
    NZSHM22(new double[]{0.8, 0.7, 0.6, 0.5}, new double[]{6.8, 7.3, 7.8});

    private double[] aperMagBoundariesArray;    // the magnitude boundaries; must have one less element than the next array
    private double[] aperValuesArray;

    Aperiodicity(double[] aperValuesArray, double[] aperMagBoundariesArray) {
        this.aperValuesArray = aperValuesArray;
        this.aperMagBoundariesArray = aperMagBoundariesArray;
    }

    /**
     * This is an array of aperiodicity values for the magnitudes ranges defined by
     * what's returned by getAperMagThreshArray().
     * @return
     */
    public double[] getAperValuesArray(){
        return aperValuesArray;
    }

    /**
     * The magnitude boundaries for the different aperoidicities returned by getAperValuesArray(),
     * where null is returned if the latter has only one value.
     * @return
     */
    public double[] getAperMagBoundariesArray(){
        return aperMagBoundariesArray;
    }
}
