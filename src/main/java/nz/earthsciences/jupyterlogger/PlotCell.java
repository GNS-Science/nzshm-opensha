package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;

/**
 * A few basic plots are supported. Plots rely on CSVCells which use Pandas. Data is organised in
 * columns, with some plots requiring column names to select data. For example, ScatterPlotCell
 * requires the names of the x and y columns, and optionally the value column.
 *
 * <p>If anything more fancy is required, this can be done manually in the notebook, or a new logger
 * Cell type can be created.
 */
public abstract class PlotCell extends JupyterNotebook.CodeCell {

    // color map helpers
    // sequential
    public static final String VIRIDIS = "viridis";
    public static final String INFERNO = "inferno";
    // divergent
    public static final String RDYLBU = "RdYlBu";
    public static final String SEISMIC = "seismic";

    final String prefix;
    final String type;
    String csvPrefix;
    boolean xLog = false;
    boolean yLog = false;
    String colorMap;
    Double alpha;

    public PlotCell(String type, String prefix, CSVCell csvCell) {
        this.type = type;
        this.prefix = JupyterLogger.logger().uniquePrefix(prefix);
        this.csvPrefix = csvCell.getPrefix();
    }

    public PlotCell setColorMap(String colorMap) {
        this.colorMap = colorMap;
        return this;
    }

    public PlotCell setXLog() {
        xLog = true;
        return this;
    }

    public PlotCell setYLog() {
        yLog = true;
        return this;
    }

    public PlotCell setAlpha(double alpha) {
        this.alpha = alpha;
        return this;
    }

    abstract List<String> getArguments();

    @Override
    public String getSource() {
        source = "%prefix%_axes = %csvPrefix%.plot.%type%(";
        List<String> arguments = new ArrayList<>(getArguments());
        if (colorMap != null) {
            arguments.add("colormap='%colorMap%'");
        }
        if (alpha != null) {
            arguments.add("alpha=%alpha%");
        }
        source += String.join(",", arguments);
        source += ")\n";
        if (xLog) {
            source += "%prefix%_axes.set_xscale('log')\n";
        }
        if (yLog) {
            source += "%prefix%_axes.set_yscale('log')\n";
        }
        source = source.stripTrailing() + ";";
        return source.replace("%type%", type)
                .replace("%prefix%", prefix)
                .replace("%csvPrefix%", csvPrefix)
                .replace("%alpha%", String.valueOf(alpha))
                .replace("%colorMap%", String.valueOf(colorMap));
    }
}
