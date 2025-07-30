package nz.cri.gns.NZSHM22.opensha.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JupyterNotebook {

    List<Cell> cells = new ArrayList<>();
    public Map<String, Object> metaData = new HashMap<>();

    public JupyterNotebook add(Cell cell) {
        cell.id = cells.size() + "";
        cells.add(cell);
        return this;
    }

    public String toJson() throws IOException {
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
    }

    public abstract static class Cell {
        public String id;
        public final String cellType;
        public final Map<String, Object> metaData = new HashMap<>();
        public String source;

        public Cell(String cellType) {
            this.cellType = cellType;
        }

        public Cell setSource(String source) {
            this.source = source;
            return this;
        }

        public Cell setMetaData(String key, Object value) {
            metaData.put(key, value);
            return this;
        }

        public Cell setMetaData(String namespace, String key, Object value) {
            Map<String, Object> innerMeta = (Map<String, Object>) metaData.get(namespace);
            if (innerMeta == null) {
                innerMeta = new HashMap<>();
                metaData.put(namespace, innerMeta);
            }
            innerMeta.put(key, value);
            return this;
        }

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

        public void writeJson(JsonWriter out, Gson gson) throws IOException {
            out.beginObject();
            out.name("cell_type");
            out.value(cellType);
            out.name("id");
            out.value(id);
            out.name("metadata");
            gson.toJson(metaData, Map.class, out);
            out.name("source");
            out.value(source);
            writeExtraJson(out);
            out.endObject();
        }

        public abstract void writeExtraJson(JsonWriter out) throws IOException;
    }

    public static class MarkdownCell extends Cell {

        public MarkdownCell() {
            super("markdown");
        }

        public MarkdownCell(String source) {
            this();
            this.source = source;
        }

        @Override
        public void writeExtraJson(JsonWriter out) throws IOException {}
    }

    public static class CodeCell extends Cell {

        public CodeCell() {
            super("code");
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
