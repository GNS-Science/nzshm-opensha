package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class MFDCell extends CSVCell {
    /**
     * Creates a new MFD plot.
     *
     * @param prefix
     */
    public MFDCell(String prefix) {
        super(prefix, "magnitude", new ArrayList<>());
    }

    // TODO: ensure that all MFD buckets align.

    /**
     * Add an MFD to the plot.
     *
     * @param name The display name of the MFD
     * @param mfd The MFD data
     */
    public void addMFD(String name, IncrementalMagFreqDist mfd) {

        if (csv.isEmpty()) {
            // fill index with x values
            // first element is empty to allow for header row
            csv.add(new ArrayList<>(List.of("magnitude")));
            for (double x : mfd.xValues()) {
                csv.add(new ArrayList<>(List.of(x)));
            }
        }
        // stick mfd y values into the next column
        // with name as the header
        csv.get(0).add(name);
        List<Double> ys = mfd.yValues();
        for (int i = 0; i < ys.size(); i++) {
            csv.get(i + 1).add(ys.get(i));
        }
    }

    /** Method for rendering the cell. */
    @Override
    public String getSource() {
        String source = super.getSource();
        source += "%prefix%.plot.line().set_yscale('log');".replace("%prefix%", prefix);
        return source;
    }
}
