package nz.cri.gns.NSHM.opensha.ruptures.downDip;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

import java.io.IOException;

/**
 * This filter ensures that ruptures with a subduction fault only consist of that fault and no other fault.
 */
public class FaultTypeSeparationFilter implements PlausibilityFilter {

    final DownDipRegistry registry;

    public FaultTypeSeparationFilter(DownDipRegistry registry) {
        this.registry = registry;
    }

    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        boolean crustal = false;
        boolean downdip = false;
        int downdipId = Integer.MIN_VALUE;
        for (FaultSubsectionCluster cluster : rupture.clusters) {
            if (registry.isDownDip(cluster)) {
                if (downdip && downdipId != cluster.parentSectionID) {
                    // trying to combine two downdip faults
                    return PlausibilityResult.FAIL_HARD_STOP;
                } else {
                    downdip = true;
                    downdipId = cluster.parentSectionID;
                }
            } else {
                crustal = true;
            }
            if (downdip && crustal) {
                // trying to combine downdip and crustal faults
                return PlausibilityResult.FAIL_HARD_STOP;
            }
        }
        return PlausibilityResult.PASS;
    }

    @Override
    public String getShortName() {
        return "FaultTypeSeparation";
    }

    @Override
    public String getName() {
        return "Fault Type Separation Filter";
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
