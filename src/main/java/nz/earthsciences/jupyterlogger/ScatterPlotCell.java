package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScatterPlotCell extends PlotCell {

    public enum CONFIG {
        COLOUR,
        VALUE_SIZE,
        COLOUR_SIZE
    }

    final Set<CONFIG> config;
    String xCol;
    String yCol;
    String valueCol;

    public ScatterPlotCell(
            String prefix,
            CSVCell csvCell,
            String xCol,
            String yCol,
            String valueCol,
            CONFIG... config) {
        super("scatter", prefix, csvCell);
        this.config = Set.of(config);
        this.xCol = xCol;
        this.yCol = yCol;
        this.valueCol = valueCol;
    }

    @Override
    public List<String> getArguments() {
        List<String> result = new ArrayList<>();
        result.add("x='%xCol%'".replace("%xCol%", xCol));
        result.add("y='%yCol%'".replace("%yCol%", yCol));

        if (config.contains(CONFIG.COLOUR) || config.contains(CONFIG.COLOUR_SIZE)) {
            result.add("c='%valCol%'".replace("%valCol%", valueCol));
        }
        if (config.contains(CONFIG.VALUE_SIZE)) {
            result.add("s='%valCol%'".replace("%valCol%", valueCol));
        }

        return result;
    }
}
