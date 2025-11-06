package nz.cri.gns.NZSHM22.opensha.util;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import nz.earthsciences.jupyterlogger.JupyterNotebook;
import org.junit.Test;

public class JupyterNotebookTest {

    /**
     * Returns a map with a fixed iteration order. Useful for writing maps to JSON in a guaranteed
     * order.
     *
     * @param args args must come in key/value pairs.
     * @return the map
     */
    public static Map<String, Object> map(Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("arguments must be key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            map.put(args[i].toString(), args[i + 1]);
        }
        return map;
    }

    public static String notebook(Map<String, Object>... cells) {
        return stringify(
                map(
                        "nbformat",
                        4,
                        "nbformat_minor",
                        0,
                        "metadata",
                        map(),
                        "cells",
                        Arrays.asList(cells)));
    }

    public static String stringify(Map<String, Object> notebook) {
        try {
            Gson gson = new GsonBuilder().create();
            StringWriter writer = new StringWriter();
            JsonWriter out = gson.newJsonWriter(writer);
            gson.toJson(notebook, Map.class, out);
            out.close();
            return writer.toString();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Test
    public void emptyTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        String actual = notebook.toJson();
        String expected = notebook();
        assertEquals(expected, actual);
    }

    @Test
    public void metadataTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        notebook.metaData.put("the-key", map("sub-key", "sub-value"));
        String actual = notebook.toJson();

        Map<String, Object> metadata = map("the-key", map("sub-key", "sub-value"));
        String expected =
                stringify(
                        map(
                                "nbformat",
                                4,
                                "nbformat_minor",
                                0,
                                "metadata",
                                metadata,
                                "cells",
                                List.of()));

        assertEquals(expected, actual);
    }

    @Test
    public void markdownTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        notebook.add(new JupyterNotebook.MarkdownCell("hello"));
        String actual = notebook.toJson();
        Map<String, Object> cell =
                map("cell_type", "markdown", "id", "0", "metadata", map(), "source", "hello");
        String expected = notebook(cell);
        assertEquals(expected, actual);
    }

    @Test
    public void codeTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        notebook.add(new JupyterNotebook.CodeCell("print(\"hello\");"));
        String actual = notebook.toJson();
        Map<String, Object> cell =
                map(
                        "cell_type",
                        "code",
                        "id",
                        "0",
                        "metadata",
                        map(),
                        "source",
                        "print(\"hello\");",
                        "execution_count",
                        0,
                        "outputs",
                        List.of());
        String expected = notebook(cell);
        assertEquals(expected, actual);
    }

    @Test
    public void cellIds() {
        JupyterNotebook notebook = new JupyterNotebook();
        notebook.add(new JupyterNotebook.MarkdownCell("cell 1"));
        notebook.add(new JupyterNotebook.MarkdownCell("cell 2"));
        String actual = notebook.toJson();

        Map<String, Object> cell1 =
                map("cell_type", "markdown", "id", "0", "metadata", map(), "source", "cell 1");
        Map<String, Object> cell2 =
                map("cell_type", "markdown", "id", "1", "metadata", map(), "source", "cell 2");

        String expected = notebook(cell1, cell2);
        assertEquals(expected, actual);
    }

    @Test
    public void cellMetaDataTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        JupyterNotebook.Cell markdownCell = new JupyterNotebook.MarkdownCell("hello");
        markdownCell
                .setMetaData("key1", "value1")
                .setMetaData("namespace1", "key2", "value2")
                .hideSource()
                .hideOutputs()
                .collapseOutput()
                .preventDeletion()
                .preventEdits();
        notebook.add(markdownCell);
        String actual = notebook.toJson();

        Map<String, Object> metadata =
                map(
                        "key1",
                        "value1",
                        "namespace1",
                        map("key2", "value2"),
                        "jupyter",
                        map("source_hidden", true, "outputs_hidden", true),
                        "collapsed",
                        true,
                        "deletable",
                        false,
                        "editable",
                        false);
        Map<String, Object> cell =
                map("cell_type", "markdown", "id", "0", "metadata", metadata, "source", "hello");
        String expected = notebook(cell);
        assertEquals(expected, actual);
    }
}
