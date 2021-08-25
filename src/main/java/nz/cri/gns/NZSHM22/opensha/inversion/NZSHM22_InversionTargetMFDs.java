package nz.cri.gns.NZSHM22.opensha.inversion;

import scratch.UCERF3.inversion.U3InversionTargetMFDs;

public abstract class NZSHM22_InversionTargetMFDs extends U3InversionTargetMFDs {
    public abstract double[] getPDF();
}
