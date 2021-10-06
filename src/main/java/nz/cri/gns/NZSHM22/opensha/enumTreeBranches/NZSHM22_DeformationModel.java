package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.common.base.Preconditions;
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

public class NZSHM22_DeformationModel {

    public static NZSHM22_DeformationModel GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw = new NZSHM22_DeformationModel(
            "geodetic, no geological prior constraint, uniform std-dev 2010",
            "RmlsZTo4NTkuMDM2Z2Rw",
            "slip_deficit_rates_no_prior_uniform-stddev_2010_RmlsZTo4NTkuMDM2Z2Rw.dat");

    public static NZSHM22_DeformationModel GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY = new NZSHM22_DeformationModel(
            "geodetic, no geological prior constraint, uniform std-dev D90",
            "RmlsZTozMDMuMEJCOVVY",
            "slip_deficit_rates_no_prior_uniform-stddev_D90_RmlsZTozMDMuMEJCOVVY.dat");

    protected String rupSet;
    protected String fileName;
    protected String description;
    List<SlipDeformation> deformations = null;

    static Map<String, NZSHM22_DeformationModel> models;

    static {
        models = new HashMap<>();
        models.put("FAULT_MODEL", null);
        models.put("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw", GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw);
        models.put("GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY", GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY);
    }

    private final static String resourcePath = "/deformationModels/";

    public NZSHM22_DeformationModel(String description, String rupSet, String fileName) {
        this.description = description;
        this.rupSet = rupSet;
        this.fileName = fileName;
    }

    public static NZSHM22_DeformationModel fromString(String name) {
        Preconditions.checkArgument(models.containsKey(name), "Deformation model with name " + name + " does not exist.");
        NZSHM22_DeformationModel model = models.get(name);
        return model;
    }

    protected InputStream getStream(String fileName) {
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

    public List<SlipDeformation> getDeformations() {
        if (deformations == null) {
            try {
                deformations = loadDeformations(getStream(fileName));
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }
        return deformations;
    }

    public SlipDeformation getDeformation(FaultSection section) {
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
