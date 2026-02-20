package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;

public class DownDipSafeCumulativeAzimuthChangeFilter extends CumulativeAzimuthChangeFilter {

    protected float threshold;

    public DownDipSafeCumulativeAzimuthChangeFilter(
            JumpAzimuthChangeFilter.AzimuthCalc calc, float threshold) {
        super(calc, threshold);
        this.threshold = threshold;
    }

    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        // we only need to check the first cluster because we have a filter that prevents
        // combinations of crustal and downDip
        FaultSectionProperties props = new FaultSectionProperties(rupture.clusters[0].startSect);
        if (props.getPartition() != PartitionPredicate.CRUSTAL) {
            return PlausibilityResult.PASS;
        } else {
            return super.apply(rupture, verbose);
        }
    }

    @Override
    public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
        return new DownDipSafeCumulativeAzimuthChangeFilter.Adapter();
    }

    public static class Adapter extends TypeAdapter<PlausibilityFilter> {

        public Adapter() {}

        @Override
        public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
            out.beginObject()
                    .name("threshold")
                    .value(((DownDipSafeCumulativeAzimuthChangeFilter) value).threshold)
                    .endObject();
        }

        @Override
        public PlausibilityFilter read(JsonReader in) throws IOException {
            double threshold = 0;

            in.beginObject();
            while (in.hasNext()) {
                in.nextName();
                threshold = in.nextDouble();
            }
            in.endObject();
            return new DownDipSafeCumulativeAzimuthChangeFilter(null, (float) threshold);
        }
    }
}
