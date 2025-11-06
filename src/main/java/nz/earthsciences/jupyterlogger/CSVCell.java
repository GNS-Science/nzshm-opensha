package nz.earthsciences.jupyterlogger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A cell that can write a CSV file. Used by the addCSV() method.
 */
public class CSVCell extends JupyterNotebook.CodeCell {
    List<List<Object>> csv;
    String prefix;
    String indexCol;

    public CSVCell(String prefix, String indexCol, List<List<Object>> csv) {
        this.prefix = JupyterLogger.logger().uniquePrefix(prefix);
        this.csv = csv;
        this.indexCol = indexCol;
    }

    protected static String safeCSVString(Object value) {
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
                    row.stream().map(CSVCell::safeCSVString).collect(Collectors.joining(","));
            builder.append(stringRow);
            builder.append('\n');
        }
        return JupyterLogger.logger().makeFile(fileName, builder.toString());
    }

    @Override
    public String getSource() {
        String fileName = writeCSV(prefix, csv);
        String template = indexCol == null ? "%name% = pd.read_csv('%fileName%')\n%name%\n" :
                "%name% = pd.read_csv('%fileName%', index_col='%indexCol%')\n%name%\n".replace("%indexCol%", indexCol);
        return template.replace("%name%", prefix).replace("%fileName%", fileName);
    }
}
