package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** A cell that can write a CSV file. Used by the addCSV() method. */
public class CSVCell extends JupyterNotebook.CodeCell {
    List<List<Object>> csv;
    String prefix;
    String indexCol;

    public CSVCell(String prefix, String indexCol, List<List<Object>> csv) {
        this.prefix = JupyterLogger.logger().uniquePrefix(prefix);
        this.csv = csv;
        this.indexCol = indexCol;
    }

    public void setIndex(List<?> index) {
        // fill index with values
        // first element is empty to allow for header row
        csv.add(new ArrayList<>(List.of(indexCol)));
        for (Object x : index) {
            csv.add(new ArrayList<>(List.of(x)));
        }
    }

    public void addColumn(String name, List<?> values) {
        if (values.size() + 1 != csv.size()) {
            throw new IllegalArgumentException(
                    "column must have exactly one value per index value");
        }
        csv.get(0).add(name);
        for (int i = 0; i < values.size(); i++) {
            csv.get(i + 1).add(values.get(i));
        }
    }

    protected static String escapeCSVString(Object value) {
        String raw = String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"")) {
            return '"' + raw.replace("\"", "\n") + '"';
        }
        return raw;
    }

    protected String writeCSV(String prefix, List<List<Object>> csv) {
        String fileName = prefix + ".csv";
        StringBuilder builder = new StringBuilder();
        for (List<Object> row : csv) {
            String stringRow =
                    row.stream().map(CSVCell::escapeCSVString).collect(Collectors.joining(","));
            builder.append(stringRow);
            builder.append('\n');
        }
        return JupyterLogger.logger().makeFile(fileName, builder.toString());
    }

    public String finaliseCSV() {
        String fileName = writeCSV(prefix, csv);
        String template =
                indexCol == null
                        ? "%prefix% = pd.read_csv('%fileName%')\n"
                        : "%prefix% = pd.read_csv('%fileName%', index_col='%indexCol%')\n"
                                .replace("%indexCol%", indexCol);
        return template.replace("%fileName%", fileName);
    }

    @Override
    public String getSource() {
        return (finaliseCSV() + "%prefix%").replace("%prefix%", prefix);
    }
}
