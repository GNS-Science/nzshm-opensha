package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.scripts.RupSetPropertyBackfill;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.RuptureMerger;

public class Builder {

    public static File makeTempFile(FaultSystemRupSet rupSet) throws IOException {
        File tempFile = Files.createTempFile("rupset", ".zip").toFile();
        rupSet.write(tempFile);
        return tempFile;
    }

    public static void buildJointRuptures(List<String> ruptureSets, boolean backfill)
            throws IOException, DocumentException {
        List<FaultSystemRupSet> rupSets = new ArrayList<>();
        for (String fileName : ruptureSets) {
            if (backfill) {
                rupSets.add(RupSetPropertyBackfill.backfill(fileName));
            } else {
                rupSets.add(FaultSystemRupSet.load(new File(fileName)));
            }
        }

        FaultSystemRupSet accumulated = new RuptureAccumulator().addRupSets(rupSets).build();

        File accumulatedFile = makeTempFile(accumulated);

        Thinning.Config thinningConfig = new Thinning.Config();
        thinningConfig.ruptureSetFileName = accumulatedFile.getAbsolutePath();
        thinningConfig.outputFileName = Files.createTempFile("thinning", ".csv").toString();

        Thinning.apply(thinningConfig);

        RuptureMerger.Config ruptureMergerConfig = new RuptureMerger.Config();
        ruptureMergerConfig.ruptureSet = new File(thinningConfig.ruptureSetFileName);
        ruptureMergerConfig.filterFile = new File(thinningConfig.outputFileName);
        RuptureMerger.merge(ruptureMergerConfig);
    }
}
