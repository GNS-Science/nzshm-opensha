package nz.cri.gns.NZSHM22.opensha.demo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.*;

public class Demo04_RupSetDiagnostics {

    @SuppressWarnings("unused")
    public static void main(String[] args) throws IOException, DocumentException {

		File mainOutputDir = new File("./data/ruptureSets/reports");
        File rupSetsDir = new File("./data/ruptureSets");

        String inputName = "CFM maxJump:1km minSects:2";
        File inputFile = new File(rupSetsDir, "CFM_crustal_1km_s2_all.zip");
        String compName = "CFM maxJump:1km minSects:5";
        File compareFile = new File(rupSetsDir, "CFM_crustal_1km_s5_all.zip");


        List<String> argz = new ArrayList<>();
        argz.add("--reports-dir");
        argz.add(mainOutputDir.getAbsolutePath());
//        argz.add("--dist-az-cache");
//        argz.add(new File(rupSetsDir, "fm3_1_dist_az_cache.csv").getAbsolutePath());
//        argz.add("--coulomb-cache-dir");
//        argz.add(rupSetsDir.getAbsolutePath());
        argz.add("--rupture-set");
        argz.add(inputFile.getAbsolutePath());
        if (inputName != null) {
            argz.add("--name");
            argz.add(inputName);
        }
        if (compareFile != null) {
            argz.add("--comp-rupture-set");
            argz.add(compareFile.getAbsolutePath());
            if (compName != null)
                argz.add("--comp-name");
            argz.add(compName);
        }
        RupSetDiagnosticsPageGen.main(argz.toArray(new String[0]));
    }
}