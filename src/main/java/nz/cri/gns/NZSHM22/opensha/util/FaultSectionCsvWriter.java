package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * A utility to write the fault sections of a rupture set to CSV. This is a helper for creators of
 * deformation model files, who need the section ids, parent ids and current slip rates of a rupture
 * set. It is the CSV counterpart of SimpleGeoJsonBuilder.
 */
public class FaultSectionCsvWriter {

    /** The CSV header of the fault section dump. */
    public static final List<String> HEADER =
            List.of(
                    "FaultID",
                    "FaultName",
                    "DipDeg",
                    "Rake",
                    "LowDepth",
                    "UpDepth",
                    "DipDir",
                    "AseismicSl",
                    "CouplingCo",
                    "SlipRate",
                    "ParentID",
                    "ParentName",
                    "SlipRateSt");

    // this class only has static methods
    protected FaultSectionCsvWriter() {}

    /**
     * Creates a CSV representation of the fault sections of a rupture set.
     *
     * @param rupSet the rupture set to dump
     * @return the fault sections as CSV
     */
    public static CSVFile<String> toCSV(FaultSystemRupSet rupSet) {
        CSVFile<String> csv = new CSVFile<>(true);
        csv.addLine(HEADER);
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            csv.addLine(
                    "" + section.getSectionId(),
                    section.getSectionName(),
                    "" + section.getAveDip(),
                    "" + section.getAveRake(),
                    "" + section.getAveLowerDepth(),
                    "" + section.getOrigAveUpperDepth(),
                    "" + section.getDipDirection(),
                    "" + section.getAseismicSlipFactor(),
                    "" + section.getCouplingCoeff(),
                    "" + section.getOrigAveSlipRate(),
                    "" + section.getParentSectionId(),
                    section.getParentSectionName(),
                    "" + section.getOrigSlipRateStdDev());
        }
        return csv;
    }

    /**
     * Dumps the fault sections of a rupture set as CSV into the specified file.
     *
     * @param rupSet the rupture set to dump
     * @param file the file to write to
     * @throws IOException if writing fails
     */
    public static void dumpFaultSections(FaultSystemRupSet rupSet, File file) throws IOException {
        toCSV(rupSet).writeToFile(file);
    }

    /**
     * Dumps the fault sections of a rupture set as CSV into the specified stream.
     *
     * @param rupSet the rupture set to dump
     * @param out the stream to write to
     * @throws IOException if writing fails
     */
    public static void dumpFaultSections(FaultSystemRupSet rupSet, OutputStream out)
            throws IOException {
        toCSV(rupSet).writeToStream(out);
    }
}
