package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import gov.usgs.earthquake.nshmp.Faults;
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

    public double calc(FaultSection a, FaultSection b) {
        return stiffnessCalculator.calc(List.of(a), List.of(b));
    }

    public double[] statsData(RsqSimEventLoader.Event event) {
        List<FaultSection> crustal = event.sections.stream().filter(s -> !s.getSectionName().contains("row:")).collect(Collectors.toList());
        double crustalArea = crustal.stream().mapToDouble(s -> s.getArea(false)).sum();
        if(crustalArea< 100000000) {
            return new double[]{0,0,0};
        }
        List<FaultSection> subduction = event.sections.stream().filter(s -> s.getSectionName().contains("row:")).collect(Collectors.toList());
        List<FaultSection> allSections = event.sections;

        double from = stiffnessCalculator.calc(allSections, subduction);
        double to = stiffnessCalculator.calc(allSections, crustal);
        double self = stiffnessCalculator.calc(allSections, allSections);

        return new double[]{from, to, self};
    }



}
