package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class DownDipTestPermutationStrategyTest {

    @Test
    public void testUnconstrainedPermutations() {

        // single section
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 1, 1);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        DownDipPermutationStrategy strategy =
                new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);

        List<FaultSubsectionCluster> actual =
                strategy.getVariations(cluster, builder.getSubSect(0, 0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        assertEquals(expected, simplifyPermutations(actual));

        // two columns
        builder = mockDownDipBuilder(0, 1, 2);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        assertEquals(expected, simplifyPermutations(actual));

        // three columns: go up and down columns
        builder = mockDownDipBuilder(0, 1, 3);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        assertEquals(expected, simplifyPermutations(actual));

        // two rows
        builder = mockDownDipBuilder(0, 2, 1);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        assertEquals(expected, simplifyPermutations(actual));

        // three rows: go up and down rows
        builder = mockDownDipBuilder(0, 3, 1);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);

        actual = strategy.getVariations(cluster, builder.getSubSect(1, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        assertEquals(expected, simplifyPermutations(actual));

        // three rows and columns: go up and down rows and columns
        builder = mockDownDipBuilder(0, 3, 3);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);

        actual = strategy.getVariations(cluster, builder.getSubSect(1, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(4));
        expected.add(Lists.newArrayList(4, 5));
        expected.add(Lists.newArrayList(4, 3));
        expected.add(Lists.newArrayList(4, 7));
        expected.add(Lists.newArrayList(4, 5, 7, 8));
        expected.add(Lists.newArrayList(4, 3, 7, 6));
        expected.add(Lists.newArrayList(4, 1));
        expected.add(Lists.newArrayList(4, 5, 1, 2));
        expected.add(Lists.newArrayList(4, 3, 1, 0));
        assertEquals(expected, simplifyPermutations(actual));

        // same cluster, different start subSect for more variety of shapes
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 4));
        expected.add(Lists.newArrayList(1, 2, 4, 5));
        expected.add(Lists.newArrayList(1, 0, 4, 3));
        expected.add(Lists.newArrayList(1, 4, 7));
        expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));
    }

    @Test
    public void testWithConstraint() {
        // 3x3 downDip with hole
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 3, 3, 0, 2);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());

        // without minFill we take everything
        DownDipPermutationStrategy strategy =
                new DownDipPermutationStrategy(DownDipConstraint.ALWAYS);
        List<FaultSubsectionCluster> actual =
                strategy.getVariations(cluster, builder.getSubSect(0, 1));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 3));
        expected.add(Lists.newArrayList(1, 3, 4));
        expected.add(Lists.newArrayList(1, 0, 3, 2));
        expected.add(Lists.newArrayList(1, 3, 6));
        expected.add(Lists.newArrayList(1, 3, 4, 6, 7));
        expected.add(Lists.newArrayList(1, 0, 3, 2, 6, 5));
        assertEquals(expected, simplifyPermutations(actual));

        // minFill is used to weed out ruptures with the hole
        DownDipConstraint constraint = DownDipConstraint.minFillConstraint(1);
        strategy = new DownDipPermutationStrategy(constraint);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        // expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 3));
        //   expected.add(Lists.newArrayList(1, 3, 4));
        expected.add(Lists.newArrayList(1, 0, 3, 2));
        expected.add(Lists.newArrayList(1, 3, 6));
        //  expected.add(Lists.newArrayList(1, 3, 4, 6, 7));
        expected.add(Lists.newArrayList(1, 0, 3, 2, 6, 5));
        assertEquals(expected, simplifyPermutations(actual));
    }

    public DownDipSubSectBuilder mockDownDipBuilder(int parentId, int numRows, int numCols) {
        return mockDownDipBuilder(parentId, numRows, numCols, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public DownDipSubSectBuilder mockDownDipBuilder(
            int parentId, int numRows, int numCols, int holeRow, int holeCol) {
        FaultSectionList sections = new FaultSectionList();
        try {
            NZSHM22_FaultModels.SBD_0_4_HKR_LR_30.fetchFaultSections(sections);
        } catch (IOException | DocumentException x) {
            throw new IllegalStateException(x);
        }
        FaultSection dummySection = sections.get(0);
        DownDipSubSectBuilder builder = mock(DownDipSubSectBuilder.class);
        when(builder.getParentID()).thenReturn(parentId);
        when(builder.getNumRows()).thenReturn(numRows);
        when(builder.getNumCols()).thenReturn(numCols);
        sections = new FaultSectionList();
        when(builder.getSubSectsList()).thenReturn(sections);
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                if (holeRow == r && holeCol == c) {
                    when(builder.getSubSect(r, c)).thenReturn(null);
                } else {
                    FaultSectionPrefData prefData = new FaultSectionPrefData();
                    prefData.setFaultSectionPrefData(dummySection);
                    prefData.setParentSectionId(parentId);
                    prefData.setSectionId(sections.getSafeId());
                    prefData.setAveDip(10);

                    FaultSection section = new GeoJSONFaultSection(prefData);
                    FaultSectionProperties props = new FaultSectionProperties(section);
                    props.setDownDipBuilder(builder);
                    sections.add(section);
                    when(builder.getRow(section)).thenReturn(r);
                    when(builder.getColumn(section)).thenReturn(c);
                    when(builder.getSubSect(r, c)).thenReturn(section);
                }
            }
        }
        return builder;
    }

    public static List<List<Integer>> simplifyPermutations(
            List<FaultSubsectionCluster> permutations) {
        List<List<Integer>> result = new ArrayList<>();
        for (FaultSubsectionCluster cluster : permutations) {
            List<Integer> rupture = new ArrayList<>();
            result.add(rupture);
            for (FaultSection section : cluster.subSects) {
                rupture.add(section.getSectionId());
            }
        }
        return result;
    }

    public FaultSection mockSection(int parentID, int id, DownDipSubSectBuilder builder) {
        GeoJSONFaultSection section = mock(GeoJSONFaultSection.class);
        when(section.getParentSectionId()).thenReturn(parentID);
        when(section.getSectionId()).thenReturn(id);
        when(section.getAveDip()).thenReturn(10.0);
        when(section.getProperty(FaultSectionProperties.DOWNDIP_BUILDER)).thenReturn(builder);
        return section;
    }
}
