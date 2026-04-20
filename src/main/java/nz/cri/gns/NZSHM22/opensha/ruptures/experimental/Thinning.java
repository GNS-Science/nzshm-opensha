package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

/** Orchestrates thinning of crustal and subduction rupture sets as inputs for RuptureMerger */
public class Thinning {

    public static class Config {
        public String ruptureSetFileName;
        public String outputFileName;
        public boolean hikurangiPosition = true;
        public Double hikurangiSize = 0.1;
        public boolean puysegurPosition = true;
        public Double puysegurSize = 0.1;
    }

    public static void apply(Config config) throws IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(config.ruptureSetFileName));

        List<Integer> crustalIds = ThinningCrustal.filterCrustal(rupSet);

        System.out.println("crustal after thinning " + crustalIds.size());

        ThinningSubduction hikurangiThinning =
                new ThinningSubduction(rupSet, s -> s.startsWith("Hikurangi"));
        if (config.hikurangiPosition) {
            hikurangiThinning.filterByPosition();
        }
        if (config.hikurangiSize != null) {
            hikurangiThinning.filterBySize(config.hikurangiSize);
        }

        System.out.println("hikurangi after thinning " + hikurangiThinning.getRuptures().size());

        ThinningSubduction puysegurThinning =
                new ThinningSubduction(rupSet, s -> s.startsWith("Puysegur"));
        if (config.puysegurPosition) {
            puysegurThinning.filterByPosition();
        }
        if (config.puysegurSize != null) {
            puysegurThinning.filterBySize(config.puysegurSize);
        }

        System.out.println("puysegur after thinning " + puysegurThinning.getRuptures().size());

        BufferedWriter writer = new BufferedWriter(new FileWriter(config.outputFileName));
        for (Integer r : crustalIds) {
            writer.write("" + r);
            writer.newLine();
        }
        for (Integer r : hikurangiThinning.getIds()) {
            writer.write("" + r);
            writer.newLine();
        }
        for (Integer r : puysegurThinning.getIds()) {
            writer.write("" + r);
            writer.newLine();
        }
        writer.close();
    }

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.ruptureSetFileName = "C:\\Users\\volkertj\\Code\\ruptureSets\\nzshm22_merged.zip";
        config.outputFileName = "/tmp/filteredRuptures.txt";
        Thinning.apply(config);
    }
}
