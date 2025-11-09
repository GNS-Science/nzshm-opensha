package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;

public class BarPlotCell extends PlotCell {

    public BarPlotCell(String prefix, CSVCell csvCell) {
        super("bar", prefix, csvCell);
    }

    @Override
    public List<String> getArguments() {
        return new ArrayList<>();
    }
}
