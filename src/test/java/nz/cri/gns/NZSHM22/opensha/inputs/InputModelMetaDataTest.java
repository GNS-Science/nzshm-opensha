package nz.cri.gns.NZSHM22.opensha.inputs;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.junit.Test;

public class InputModelMetaDataTest {

    static InputModelMetaData parse(String json) throws IOException {
        return InputModelMetaData.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    static void writeCached(String path, String content) throws IOException {
        Path file = InputModel.CACHE_DIR.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    public void testGetters() throws IOException {
        InputModelMetaData meta = parse("{\"a\": \"x\", \"b\": true, \"c\": 3, \"d\": null}");
        assertTrue(meta.has("a"));
        assertFalse(meta.has("d"));
        assertFalse(meta.has("missing"));
        assertEquals("x", meta.getString("a", "y"));
        assertEquals("y", meta.getString("missing", "y"));
        assertTrue(meta.getBoolean("b", false));
        assertFalse(meta.getBoolean("missing", false));
        assertEquals(3, meta.getInt("c", 0));
        assertEquals(7, meta.getInt("d", 7));
        assertNull(meta.get("missing"));
    }

    @Test
    public void testInputModelsGetMetaData() throws IOException {
        writeCached("test/meta.xml" + InputModel.META_DATA_SUFFIX, "{\"name\": \"n\"}");
        InputModelMetaData meta = InputModel.getMetaData("test/meta.xml");
        assertEquals("n", meta.getString("name", null));
    }

    @Test(expected = IllegalStateException.class)
    public void testRequiredKeyMissing() throws IOException {
        parse("{}").getString("missing");
    }

    @Test
    public void testFaultModelsUsesMetaData() throws IOException {
        writeCached(
                FaultModel.MODELS_PATH + "metaTest.csv" + InputModel.META_DATA_SUFFIX,
                "{\"modelName\": \"fromMeta\", \"crustal\": false, \"parentSectionId\": 10000,"
                        + " \"partition\": \"HIKURANGI\"}");
        FaultModel model = new FaultModel("metaTest.csv");
        assertEquals("fromMeta", model.getModelName());
        assertFalse(model.isCrustal());
        assertNull(model.getTvzDomain());
        assertEquals(10000, model.getParentSectionId());
        assertEquals(PartitionPredicate.HIKURANGI, model.getPartition());
    }

    @Test
    public void testCustomFaultModel() {
        FaultModel model = new FaultModel(InputModelMetaData.parse(FaultModel.CUSTOM_META_DATA));
        assertNull(model.getFileName());
        assertTrue(model.isCrustal());
        assertEquals("4", model.getTvzDomain());
        assertEquals(-1, model.getParentSectionId());
        assertEquals(PartitionPredicate.CRUSTAL, model.getPartition());
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingMetaData() throws IOException {
        writeCached(FaultModel.MODELS_PATH + "noMeta.xml" + InputModel.META_DATA_SUFFIX, "{}");
        new FaultModel("noMeta.xml").getModelName();
    }
}
