package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** A cell that can write a CSV file. Used by the addCSV() method. */
public class CSVCell extends JupyterNotebook.CodeCell {
    List<List<Object>> csv;
    String prefix;
    String indexCol;
    boolean showTable = true;

    public CSVCell(String prefix, String indexCol, List<List<Object>> csv) {
        this.prefix = JupyterLogger.logger().uniquePrefix(prefix);
        this.csv = csv;
        this.indexCol = indexCol;
    }

    /**
     * This returns the name of the dataFrame holding the CSV
     *
     * @return
     */
    public String getPrefix() {
        return prefix;
    }

    public CSVCell showTable(boolean showTable) {
        this.showTable = showTable;
        return this;
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
        if (csv.isEmpty()) {
            for (Object v : values) {
                csv.add(new ArrayList<>());
            }
        }
        if (values.size() + 1 != csv.size()) {
            throw new IllegalArgumentException(
                    "column must have exactly one value per index value");
        }
        csv.get(0).add(name);
        for (int i = 0; i < values.size(); i++) {
            csv.get(i + 1).add(values.get(i));
        }
    }

    /**
     * Adds a row to the CSV. It is not recommended to mix addColumn() and addRow() calls for one
     * CSV.
     *
     * @param row
     */
    public void addrow(List<Object> row) {
        csv.add(row);
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
                        ? "%prefix% = pd.read_csv('%fileName%')"
                        : "%prefix% = pd.read_csv('%fileName%', index_col='%indexCol%')"
                                .replace("%indexCol%", indexCol);
        return template.replace("%fileName%", fileName);
    }

    @Override
    public String getSource() {
        String source = finaliseCSV();
        if (showTable) {
            source += "\n%prefix%";
        }
        return source.replace("%prefix%", prefix);
    }
}
