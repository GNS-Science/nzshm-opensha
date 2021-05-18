package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

import java.io.IOException;

public class DownDipSafeCumulativeAzimuthChangeFilter extends CumulativeAzimuthChangeFilter {

    public DownDipSafeCumulativeAzimuthChangeFilter(JumpAzimuthChangeFilter.AzimuthCalc calc, float threshold) {
        super(calc, threshold);
    }

    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        // we only need to check the first cluster because we have a filter that prevents combinations of crustal and downDip
        if (DownDipFaultSection.isDownDip(rupture.clusters[0])) {
            return PlausibilityResult.PASS;
        } else {
            return super.apply(rupture, verbose);
        }
    }

    @Override
    public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
        return new TypeAdapter<PlausibilityFilter>() {
            @Override
            public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
                out.beginObject().endObject();
            }

            @Override
            public PlausibilityFilter read(JsonReader in) throws IOException {
                return null;
            }
        };
    }

}
