package nz.earthsciences.jupyterlogger;

import java.util.List;

public class HistogramCell extends PlotCell {

    final int bins;

    public HistogramCell(String prefix, int bins, CSVCell csvCell) {
        super("hist", prefix, csvCell);
        this.bins = bins;
    }

    @Override
    public List<String> getArguments() {
        return List.of("bins=" + bins);
    }
}
