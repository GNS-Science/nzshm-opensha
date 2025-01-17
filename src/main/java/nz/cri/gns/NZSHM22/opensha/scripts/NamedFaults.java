package nz.cri.gns.NZSHM22.opensha.scripts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.faultSurface.FaultSection;

public class NamedFaults {

    public static void createNamedFaultsFile(NZSHM22_FaultModels faultModel)
            throws DocumentException, IOException {

        FaultSectionList sections = new FaultSectionList();
        faultModel.fetchFaultSections(sections);
        Map<String, Integer> ids = new HashMap<>();
        for (FaultSection section : sections) {
            String name = section.getSectionName().toLowerCase();
            name = name.replaceAll("[\\W_]", "");
            ids.put(name, section.getSectionId());
        }

        try (PrintWriter out =
                new PrintWriter(
                        new FileWriter(
                                new File(
                                        "src/main/resources/faultModels/"
                                                + faultModel.getFileName()
                                                + ".FaultsByNameAlt.txt")))) {
            CSVFile<String> csv =
                    CSVFile.readStream(faultModel.getStream("namedFaultDefinitions.csv"), false);
            boolean first = true;
            for (List<String> row : csv) {
                if (row.get(0) != null && row.get(0).length() > 0) {
                    if (!first) {
                        out.print("\n");
                    } else {
                        first = false;
                    }
                    out.print(row.get(0));
                }
                out.print("\t");
                String name = row.get(1).toLowerCase().replaceAll("[\\W_]", "");
                if (!ids.containsKey(name)) {
                    System.out.println("Fault \"" + row.get(1) + "\" cannot be found in model");
                } else {
                    out.print(ids.get(name));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws DocumentException, IOException {
        createNamedFaultsFile(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        createNamedFaultsFile(NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ);
    }
}
