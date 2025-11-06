package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LinePlotCell extends CSVCell {

    public enum CONFIG {
        X_LOG,
        Y_LOG
    }

    final Set<CONFIG> config;

    public LinePlotCell(String prefix, String indexCol, CONFIG... config) {
        super(prefix, indexCol, new ArrayList<>());
        this.config = Set.of(config);
    }

    public void setXValues(List<?> xValues) {
        setIndex(xValues);
    }

    public void addSeries(String name, List<?> series) {
        addColumn(name, series);
    }

    @Override
    public String getSource() {
        String source = finaliseCSV();
        source += "%prefix%_axes = %prefix%.plot.line()\n";
        if (config.contains(CONFIG.X_LOG)) {
            source += "%prefix%_axes.set_xscale('log')\n";
        }
        if (config.contains(CONFIG.Y_LOG)) {
            source += "%prefix%_axes.set_yscale('log')\n";
        }
        source = source.stripTrailing() + ";";
        return source.replace("%prefix%", prefix);
    }
}
