package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.StiffnessCalcModule;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * not a proper filter yet, for gathering data only
 */
public class SelfStiffnessCoulombFilter {

    StiffnessCalcModule stiffnessCalcModule;
    AggregatedStiffnessCalculator stiffnessCalculator;

    DecimalFormat fmt3 = new DecimalFormat("0.000");

    public SelfStiffnessCoulombFilter(StiffnessCalcModule stiffnessCalcModule) {
        this.stiffnessCalcModule = stiffnessCalcModule;
        this.stiffnessCalculator = new AggregatedStiffnessCalculator(
                SubSectStiffnessCalculator.StiffnessType.CFF,
                stiffnessCalcModule.stiffnessCalc,
                true,
                AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM);
    }

    public String statsHeader() {
        return "all->sub, all->cru, all->all";
    }

    public String stats(RsqSimEventLoader.Event event) {
        double[] stats = statsData(event);
        return Arrays.stream(stats).mapToObj(fmt3::format).collect(Collectors.joining(", "));
    }

    public double[] statsData(RsqSimEventLoader.Event event) {
        List<FaultSection> fromSections = event.jump.fromRupture.buildOrderedSectionList();
        List<FaultSection> toSections = event.jump.toRupture.buildOrderedSectionList();
        List<FaultSection> allSections = event.sections;

        double from = stiffnessCalculator.calc(allSections, fromSections);
        double to = stiffnessCalculator.calc(allSections, toSections);
        double self = stiffnessCalculator.calc(allSections, allSections);

        return new double[]{from, to, self};
    }

}
