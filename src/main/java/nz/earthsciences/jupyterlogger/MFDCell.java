package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
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

    /**
     * Add an MFD to the plot.
     *
     * @param name The display name of the MFD
     * @param mfd The MFD data
     */
    public void addMFD(String name, IncrementalMagFreqDist mfd) {
        if (csv.isEmpty()) {
            addIndex(mfd.xValues());
        }

        addColumn(name, mfd.yValues());
    }

    /** Method for rendering the cell. */
    @Override
    public String getSource() {
        String source = super.getSource();
        source += "%prefix%.plot.line().set_yscale('log');".replace("%prefix%", prefix);
        return source;
    }
}
