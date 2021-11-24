package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import org.junit.Test;
import org.opensha.commons.logicTree.JsonAdapterHelper;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.*;

public class NZSHM22_ScalingRelationshipNodeTest {

    public void assertWriteReadJson(NZSHM22_ScalingRelationshipNode node) throws IOException {
        StringWriter dataWriter = new StringWriter();
        JsonWriter out = new JsonWriter(dataWriter);

        JsonAdapterHelper.writeAdapterValue(out, node);
        String data1 = dataWriter.toString();

        NZSHM22_ScalingRelationshipNode actual = (NZSHM22_ScalingRelationshipNode) JsonAdapterHelper.readAdapterValue(new JsonReader(new StringReader(data1)));

        // can reconstruct node from JSON
        assertEquals(node, actual);

        dataWriter = new StringWriter();
        out = new JsonWriter(dataWriter);
        JsonAdapterHelper.writeAdapterValue(out, actual);

        // JSON of original node and of reconstructed node are equal
        assertEquals(data1, dataWriter.toString());
    }

    @Test
    public void testU3ScalingJson() throws IOException {
        NZSHM22_ScalingRelationshipNode node = new NZSHM22_ScalingRelationshipNode();
        node.setScalingRelationship(ScalingRelationships.TMG_CRU_2017);

        assertWriteReadJson(node);
    }

    @Test
    public void testGenericJson() throws IOException {
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 5.0);

        NZSHM22_ScalingRelationshipNode node = new NZSHM22_ScalingRelationshipNode();
        node.setScalingRelationship(scaling);

        assertWriteReadJson(node);
    }
}
