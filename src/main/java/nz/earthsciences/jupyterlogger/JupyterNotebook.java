package nz.earthsciences.jupyterlogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Basic class to create Jupyter Notebook files. Based on the <a
 * href="https://nbformat.readthedocs.io/en/latest/format_description.html">Notebook file format
 * 5.10</a>.
 */
public class JupyterNotebook {

    List<Cell> cells = new ArrayList<>();
    public Map<String, Object> metaData = new HashMap<>();

    /**
     * Appends the provided cell to the notebook.
     *
     * @param cell a cell
     * @return this notebook
     */
    public JupyterNotebook add(Cell cell) {
        cell.id = cells.size() + "";
        cells.add(cell);
        return this;
    }

    /**
     * Returns a JSON representation of this notebook.
     *
     * @return a JSON representation of this notebook.
     */
    public String toJson() {
        try {
            Gson gson = new GsonBuilder().create();
            StringWriter writer = new StringWriter();
            JsonWriter out = gson.newJsonWriter(writer);
            out.beginObject();
            out.name("nbformat");
            out.value(4);
            out.name("nbformat_minor");
            out.value(0);
            out.name("metadata");
            gson.toJson(metaData, Map.class, out);
            out.name("cells");
            out.beginArray();
            for (Cell cell : cells) {
                cell.writeJson(out, gson);
            }
            out.endArray();
            out.endObject();
            out.close();
            return writer.toString();
        } catch (IOException x) {
            // Because we operate in memory, writing should not throw, and we don't expect callers
            // to handle exceptions. In case it does, we throw a RunTimeException.
            throw new RuntimeException(x);
        }
    }

    /** A abstract Jupyter notebook cell. Use MarkdownCell or CodeCell to create new cells. */
    protected abstract static class Cell {
        protected String id;
        protected final String cellType;
        // LinkedHashMap for reproducibility when writing JSON in tests
        protected final Map<String, Object> metaData = new LinkedHashMap<>();
        protected String source;

        /**
         * Creates a new cell.
         *
         * @param cellType
         */
        protected Cell(String cellType) {
            this.cellType = cellType;
        }

        /**
         * Sets the source
         *
         * @param source the source
         * @return this cell
         */
        public Cell setSource(String source) {
            this.source = source;
            return this;
        }

        /**
         * Returns the source
         *
         * @return the source
         */
        public String getSource() {
            return source;
        }

        /**
         * Sets a metadata entry
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this cell
         */
        public Cell setMetaData(String key, Object value) {
            metaData.put(key, value);
            return this;
        }

        /**
         * Sets a namespaces metadata entry
         *
         * @param namespace the metadata namespace
         * @param key the metadata key
         * @param value the metadata value
         * @return this cell
         */
        public Cell setMetaData(String namespace, String key, Object value) {
            Map<String, Object> innerMeta = (Map<String, Object>) metaData.get(namespace);
            if (innerMeta == null) {
                innerMeta = new LinkedHashMap<>();
                metaData.put(namespace, innerMeta);
            }
            innerMeta.put(key, value);
            return this;
        }

        /**
         * Hide (collapse) the source of the cell
         *
         * @return this cell
         */
        public Cell hideSource() {
            return setMetaData("jupyter", "source_hidden", true);
        }

        public Cell hideOutputs() {
            return setMetaData("jupyter", "outputs_hidden", true);
        }

        public Cell collapseOutput() {
            return setMetaData("collapsed", true);
        }

        public Cell preventDeletion() {
            return setMetaData("deletable", false);
        }

        public Cell preventEdits() {
            return setMetaData("editable", false);
        }

        protected void writeJson(JsonWriter out, Gson gson) throws IOException {
            out.beginObject();
            out.name("cell_type");
            out.value(cellType);
            out.name("id");
            out.value(id);
            out.name("metadata");
            gson.toJson(metaData, Map.class, out);
            out.name("source");
            out.value(getSource());
            writeExtraJson(out);
            out.endObject();
        }

        public abstract void writeExtraJson(JsonWriter out) throws IOException;
    }

    /** A notebook cell that represents markdown */
    public static class MarkdownCell extends Cell {

        /** Creates an empty markdown cell. */
        public MarkdownCell() {
            super("markdown");
        }

        /**
         * Creates a markdown cell with the provided source.
         *
         * @param source a string representing markdown
         */
        public MarkdownCell(String source) {
            this();
            setSource(source);
        }

        @Override
        public void writeExtraJson(JsonWriter out) throws IOException {}
    }

    /** A notebook code cell. */
    public static class CodeCell extends Cell {

        /** Creates an empty code cell. */
        public CodeCell() {
            super("code");
        }

        /**
         * Creates a code cell with the specified source.
         *
         * @param source the code string
         */
        public CodeCell(String source) {
            this();
            setSource(source);
        }

        @Override
        public void writeExtraJson(JsonWriter out) throws IOException {
            out.name("execution_count");
            out.value(0);
            out.name("outputs");
            out.beginArray();
            out.endArray();
        }
    }
}
