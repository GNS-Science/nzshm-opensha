package nz.cri.gns.NZSHM22.opensha.util;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public class JupyterNotebookTest {

    Map<String,Object> baseNotebook = Map.of( "nbformat", 4,"nbformat-minor", 0, "metadata", new HashMap<>(), "cells", new ArrayList<>());

    public static String makeExpectedNotebook(Map<String, Object> metadata, List<Map<String, Object>> cells){
        Map<String, Object> notebook = new LinkedHashMap<>();
        notebook.put("nbformat", 4);
        notebook.put("nbformat-minor", 0);
        notebook.put("metadata", metadata);
        notebook.put("cells", cells);
    }

    public static String makeExpected(Map<String,Object> notebook) {
        try{
        Gson gson = new GsonBuilder().create();
        StringWriter writer = new StringWriter();
        JsonWriter out = gson.newJsonWriter(writer);

            gson.toJson(notebook, Map.class, out);
            out.close();
            return writer.toString();
        }catch(IOException x){
            throw new RuntimeException(x);
        }
    }

    @Test
    public void emptyTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        String actual = notebook.toJson();
        assertEquals(makeExpected(baseNotebook), actual);

    }

    @Test
    public void metadataTest() {
        JupyterNotebook notebook = new JupyterNotebook();
        notebook.metaData.put("the-key", Map.of("sub-key", "sub-value"));
        String actual = notebook.toJson();
        assertEquals(
                "{\"nbformat\":4,\"nbformat_minor\":0,\"metadata\":{\"the-key\":{\"sub-key\":\"sub-value\"}},\"cells\":[]}",
                actual);
    }
}
