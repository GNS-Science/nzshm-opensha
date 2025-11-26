package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;

public class LinePlotCell extends PlotCell {

    public LinePlotCell(String prefix, CSVCell csvCell) {
        super("line", prefix, csvCell);
    }

    @Override
    public List<String> getArguments() {
        return new ArrayList<>();
    }
}
