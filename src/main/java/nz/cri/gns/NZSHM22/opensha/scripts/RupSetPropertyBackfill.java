package nz.cri.gns.NZSHM22.opensha.scripts;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.util.TectonicRegionType;

/**
 * CLI tool to backfill {@link FaultSectionProperties} on rupture sets that were saved before
 * properties were introduced.
 */
public class RupSetPropertyBackfill {

    private static final Pattern COL_ROW_PATTERN = Pattern.compile("; col: (\\d+), row: (\\d+)");

    /**
     * Backfill fault section properties on a rupture set loaded from the given file.
     *
     * @param archiveFileName path to the rupture set zip file
     * @return the rupture set with properties populated
     */
    @SuppressWarnings("unchecked")
    public static FaultSystemRupSet backfill(String archiveFileName)
            throws IOException, DocumentException {

        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(archiveFileName));

        // Try to get fault model from logic tree branch for crustal lookups
        NZSHM22_LogicTreeBranch branch = rupSet.getModule(NZSHM22_LogicTreeBranch.class);
        NZSHM22_FaultModels faultModel =
                branch != null ? branch.getValue(NZSHM22_FaultModels.class) : null;

        FaultSectionList parentSections = null;
        if (faultModel != null && faultModel.isCrustal()) {
            parentSections = new FaultSectionList();
            faultModel.fetchFaultSections(parentSections);
        }

        List<FaultSection> sections = (List<FaultSection>) rupSet.getFaultSectionDataList();

        for (int sectionIndex = 0; sectionIndex < rupSet.getNumSections(); sectionIndex++) {
            FaultSection section = sections.get(sectionIndex);
            if (!(section instanceof GeoJSONFaultSection)) {
                section = GeoJSONFaultSection.fromFaultSection(section);
                sections.set(sectionIndex, section);
            }
            FaultSectionProperties props = new FaultSectionProperties(section);
            Matcher m = COL_ROW_PATTERN.matcher(section.getSectionName());
            if (m.find()) {
                // Subduction section
                props.setColIndex(Integer.parseInt(m.group(1)));
                props.setRowIndex(Integer.parseInt(m.group(2)));
                section.setAveRake(90);
                section.setTectonicRegionType(TectonicRegionType.SUBDUCTION_INTERFACE);
                if (section.getSectionName().contains("Hikurangi")) {
                    props.setPartition(PartitionPredicate.HIKURANGI);
                } else if (section.getSectionName().contains("Puysegur")) {
                    props.setPartition(PartitionPredicate.PUYSEGUR);
                }
            } else {
                // Crustal section
                props.setPartition(PartitionPredicate.CRUSTAL);
                section.setTectonicRegionType(TectonicRegionType.ACTIVE_SHALLOW);
                if (parentSections != null) {
                    NZFaultSection parent =
                            (NZFaultSection) parentSections.get(section.getParentSectionId());
                    props.setDomain(parent.getDomainNo());
                    if (parent.getDomainNo().equals(faultModel.getTvzDomain())) {
                        props.setTvz();
                    }
                }
            }
        }

        // Rebuild the rupture set so that rupture rakes are recalculated as area-weighted
        // averages of the (now corrected) section rakes.
        FaultSystemRupSet original = rupSet;
        rupSet =
                FaultSystemRupSet.builder(sections, original.getSectionIndicesForAllRups())
                        .rupMags(original.getMagForAllRups())
                        .rupAreas(original.getAreaForAllRups())
                        .rupLengths(original.getLengthForAllRups())
                        .build();
        for (OpenSHA_Module module : original.getModules(true)) {
            rupSet.addModule(module);
        }

        return rupSet;
    }

    public static void main(String[] args) throws IOException, DocumentException {
        if (args.length < 1) {
            System.err.println("Usage: RupSetPropertyBackfill <rupture-set.zip>");
            System.exit(1);
        }
        String fileName = args[0];
        FaultSystemRupSet rupSet = backfill(fileName);
        String outputName =
                fileName.endsWith(".zip")
                        ? fileName.substring(0, fileName.length() - 4) + ".backfilled.zip"
                        : fileName + ".backfilled.zip";
        rupSet.write(new File(outputName));
        System.out.println("Written to " + outputName);
    }
}
