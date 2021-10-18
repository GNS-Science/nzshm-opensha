package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum NZSHM22_DeformationModel implements LogicTreeNode {

    FAULT_MODEL(
            "Fault Model",
            "Use deformation model as provided by the fault model",
            null,
            null),

    GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw(
            "GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw",
            "geodetic, no geological prior constraint, uniform std-dev 2010",
            "RmlsZTo4NTkuMDM2Z2Rw",
            "slip_deficit_rates_no_prior_uniform-stddev_2010_RmlsZTo4NTkuMDM2Z2Rw.dat"),

    GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY(
            "GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY",
            "geodetic, no geological prior constraint, uniform std-dev D90",
            "RmlsZTozMDMuMEJCOVVY",
            "slip_deficit_rates_no_prior_uniform-stddev_D90_RmlsZTozMDMuMEJCOVVY.dat");

    String name;
    String rupSet;
    String description;
    String fileName;
    DeformationHelper helper;

    private final static String resourcePath = "/deformationModels/";

    NZSHM22_DeformationModel(String name, String description, String rupSet, String fileName) {
        this.name = name;
        this.description = description;
        this.rupSet = rupSet;
        this.fileName = fileName;
        this.helper = new DeformationHelper(fileName);
    }

    public static class DeformationHelper {
        List<SlipDeformation> deformations = null;
        String fileName;

        public DeformationHelper(String fileName) {
            this.fileName = fileName;
        }

        protected InputStream getStream() {
            return getClass().getResourceAsStream(resourcePath + fileName);
        }

        public static class SlipDeformation {
            int sectionId;
            int parentId;
            double slip;
            double stdv;
        }

        protected static List<SlipDeformation> loadDeformations(InputStream deformationsFile) throws IOException {
            List<SlipDeformation> result = new ArrayList<>();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(deformationsFile))) {
                String line = null;
                int rowNum = 0;
                while ((line = in.readLine()) != null) {
                    try {
                        if (line.startsWith("%")) {
                            continue;
                        }
                        String[] data = line.split(",");
                        SlipDeformation deformation = new SlipDeformation();
                        deformation.sectionId = Integer.parseInt(data[0].trim());
                        deformation.parentId = Integer.parseInt(data[1].trim());
                        deformation.slip = Double.parseDouble(data[2].trim());
                        deformation.stdv = Double.parseDouble(data[3].trim());
                        result.add(deformation);
                    } catch (Exception x) {
                        System.out.println("Error parsing deformation model at line " + rowNum);
                        x.printStackTrace();
                        System.out.println(x);
                        throw x;
                    }
                    rowNum++;
                }
                return result;
            }
        }

        protected List<SlipDeformation> getDeformations() {
            if (deformations == null) {
                try {
                    deformations = loadDeformations(getStream());
                } catch (IOException x) {
                    throw new RuntimeException(x);
                }
            }
            return deformations;
        }

        protected SlipDeformation getDeformation(FaultSection section) {
            return getDeformations().get(section.getSectionId());
        }

        public void applyTo(FaultSystemRupSet rupSet) {
            Preconditions.checkArgument(getDeformations().size() == rupSet.getNumSections(), "Deformation model length does not match number of sections.");
            for (FaultSection section : rupSet.getFaultSectionDataList()) {
                SlipDeformation deformation = getDeformation(section);
                Preconditions.checkArgument(deformation.sectionId == section.getSectionId(), "Deformation section id does not match section id.");
                Preconditions.checkArgument(deformation.parentId == section.getParentSectionId(), "Deformation parent id does not match section parent id.");
                section.setAveSlipRate(deformation.slip);
                section.setSlipRateStdDev(deformation.stdv);
            }
            rupSet.removeModuleInstances(SectSlipRates.class);
            SectSlipRates rates = SectSlipRates.fromFaultSectData(rupSet);
            rupSet.addModule(SectSlipRates.precomputed(rupSet, rates.getSlipRates(), rates.getSlipRateStdDevs()));
        }
    }

    public void applyTo(FaultSystemRupSet rupSet) {
        if (fileName != null) {
            helper.applyTo(rupSet);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getShortName() {
        return getName();
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    public static LogicTreeLevel<LogicTreeNode> level() {
        return LogicTreeLevel.forEnumUnchecked(NZSHM22_DeformationModel.class, "NZSHM22_DeformationModel", "NZSHM22_DeformationModel");
    }

}
