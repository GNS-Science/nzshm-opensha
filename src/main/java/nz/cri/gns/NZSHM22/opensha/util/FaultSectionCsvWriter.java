package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * A utility to write the fault sections of a rupture set to CSV. This is a helper for creators of
 * deformation model files, who need the section ids, parent ids and current slip rates of a rupture
 * set. It is the CSV counterpart of SimpleGeoJsonBuilder.
 */
public class FaultSectionCsvWriter {

    // this class only has static methods
    protected FaultSectionCsvWriter() {}

    /** The CSV header of the fault section dump. */
    public static final String FAULT_SECTION_CSV_HEADER =
            "FaultID,FaultName,DipDeg,Rake,LowDepth,UpDepth,DipDir,AseismicSl,CouplingCo,SlipRate,ParentID,ParentName,SlipRateSt";

    /**
     * Quotes a CSV value if it contains a comma or a quote.
     *
     * @param value the value to quote
     * @return the value, quoted if necessary
     */
    protected static String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Dumps the fault sections of a rupture set as CSV. This is a helper for creators of
     * deformation model files, who need the section ids, parent ids and current slip rates of a
     * rupture set.
     *
     * @param rupSet the rupture set to dump
     * @param out the writer to write the CSV to. Not closed by this method.
     * @throws IOException if writing fails
     */
    public static void dumpFaultSections(FaultSystemRupSet rupSet, Writer out) throws IOException {
        out.write(FAULT_SECTION_CSV_HEADER);
        out.write("\n");
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            out.write(
                    String.join(
                            ",",
                            "" + section.getSectionId(),
                            quote(section.getSectionName()),
                            "" + section.getAveDip(),
                            "" + section.getAveRake(),
                            "" + section.getAveLowerDepth(),
                            "" + section.getOrigAveUpperDepth(),
                            "" + section.getDipDirection(),
                            "" + section.getAseismicSlipFactor(),
                            "" + section.getCouplingCoeff(),
                            "" + section.getOrigAveSlipRate(),
                            "" + section.getParentSectionId(),
                            quote(section.getParentSectionName()),
                            "" + section.getOrigSlipRateStdDev()));
            out.write("\n");
        }
        out.flush();
    }

    /**
     * Dumps the fault sections of a rupture set as CSV into the specified file.
     *
     * @param rupSet the rupture set to dump
     * @param file the file to write to
     * @throws IOException if writing fails
     */
    public static void dumpFaultSections(FaultSystemRupSet rupSet, File file) throws IOException {
        try (Writer out = new FileWriter(file)) {
            dumpFaultSections(rupSet, out);
        }
    }
}
