package NSHM_NZ.inversion;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import nz.cri.gns.NSHM.opensha.ruptures.downDip.DownDipRegistry;
import nz.cri.gns.NSHM.opensha.ruptures.downDip.DownDipSubSectBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.downDip.DownDipPermutationStrategy;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveClusterPermuationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

/*
 * Build FaultSections from a CSV fixture containing 9 10km * 10km subsections of the Hikurangi Interface geometry.
 *
 * Together with latest work by Kevin in opensha-scratch.kevin.ucerf3
 *
 * @author chrisbc
 *
 */
public class InterfaceRuptureSetBuilderTest {

    private static final double grid_disc = 5d;
    private static List<FaultSection> subSections;

    //TODO move this helper to a utils.* class
    private static FaultSectionPrefData buildFSD(int sectionId, FaultTrace trace, double upper, double lower, double dip) {
        FaultSectionPrefData fsd = new FaultSectionPrefData();
        fsd.setSectionId(sectionId);
        fsd.setFaultTrace(trace);
        fsd.setAveUpperDepth(upper);
        fsd.setAveLowerDepth(lower);
        fsd.setAveDip(dip);
        fsd.setDipDirection((float) trace.getDipDirection());
        return fsd.clone();
    }

    private static FaultSection buildFaultSectionFromCsvRow(int sectionId, List<String> row) {
        // along_strike_index, down_dip_index, lon1(deg), lat1(deg), lon2(deg), lat2(deg), dip (deg), top_depth (km), bottom_depth (km),neighbours
        // [3, 9, 172.05718990191556, -43.02716092186062, 171.94629898533478, -43.06580050196082, 12.05019252859843, 36.59042136801586, 38.67810629370413, [(4, 9), (3, 10), (4, 10)]]
        FaultTrace trace = new FaultTrace("SubductionTile_" + (String) row.get(0) + "_" + (String) row.get(1));
        trace.add(new Location(Float.parseFloat((String) row.get(3)),
                Float.parseFloat((String) row.get(2)),
                Float.parseFloat((String) row.get(7)))
        );
        trace.add(new Location(Float.parseFloat((String) row.get(5)),    //lat
                Float.parseFloat((String) row.get(4)),                        //lon
                Float.parseFloat((String) row.get(7)))                        //top_depth (km)
        );

        return buildFSD(sectionId, trace,
                Float.parseFloat((String) row.get(7)), //top
                Float.parseFloat((String) row.get(8)), //bottom
                Float.parseFloat((String) row.get(6))); //dip
    }


    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        InputStream csvdata = InterfaceRuptureSetBuilderTest.class.getResourceAsStream("patch_4_10.csv");
        CSVFile<String> csv = CSVFile.readStream(csvdata, false);

        FaultSectionPrefData parentSection = new FaultSectionPrefData();
        parentSection.setSectionId(10000);
        parentSection.setSectionName("ParentSection 10000 - Test SubSect Down-Dip Fault\"");

        subSections = Lists.newArrayList();
        for (int row = 1; row < csv.getNumRows(); row++) {
            FaultSection fs = buildFaultSectionFromCsvRow(row - 1, csv.getLine(row));
            fs.setParentSectionId(parentSection.getSectionId());
            fs.setParentSectionName(parentSection.getSectionName());
            subSections.add(fs);
        }
    }

    @Test
    public void testOneSubSectionSetup() throws IOException {
        System.out.println("Have " + subSections.size() + " sub-sections"); // + parentSection.getSectionName());
        assertEquals(9, subSections.size());
    }

    @Test
    public void testBuildSubsectionsFromCSV() throws IOException {

        int startID = 0;
        FaultSection parentSection = new FaultSectionPrefData();
        parentSection.setSectionId(10000);
        parentSection.setSectionName("Test SubSect Down-Dip Fault");

        InputStream csvdata = InterfaceRuptureSetBuilderTest.class.getResourceAsStream("patch_4_10.csv");

        //	the builder
        DownDipSubSectBuilder downDipBuilder = new DownDipSubSectBuilder(
                parentSection.getSectionName(), parentSection, startID, csvdata);
        assertEquals(9, downDipBuilder.getSubSectsList().size());

    }

    @Test
    public void testBuildRectangularRuptuesFromCSV() throws IOException {
        String sectName = "Test SubSect Down-Dip Fault";
        int sectID = 0;
        int startID = 0;

        FaultSection parentSection = new FaultSectionPrefData();
        parentSection.setSectionId(10000);
        parentSection.setSectionName("ParentSection 10000 - Test SubSect Down-Dip Fault\"");

        InputStream csvdata = InterfaceRuptureSetBuilderTest.class.getResourceAsStream("patch_4_10.csv");
        DownDipSubSectBuilder downDipBuilder = new DownDipSubSectBuilder(sectName, parentSection, startID, csvdata);
        DownDipRegistry downDipRegistry = mock(DownDipRegistry.class);
        when(downDipRegistry.getBuilder(downDipBuilder.getParentID())).thenReturn(downDipBuilder);

        List<FaultSection> subSections = new ArrayList<>();
        subSections.addAll(downDipBuilder.getSubSectsList());
        assertEquals(9, subSections.size());

        for (int s = 0; s < subSections.size(); s++)
            Preconditions.checkState(subSections.get(s).getSectionId() == s,
                    "section at index %s has ID %s", s, subSections.get(s).getSectionId());

        // Azimuths & Distances
        SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);

        // this creates rectangular permutations only for our down-dip fault to speed up rupture building
        ClusterPermutationStrategy permutationStrategy = new DownDipPermutationStrategy(downDipRegistry, new ExhaustiveClusterPermuationStrategy());

        // connection strategy: parent faults connect at closest point, and only when dist <=5 km
        ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(subSections, distAzCalc, 5d);
        int maxNumSplays = 0; // don't allow any splays

        PlausibilityConfiguration config =
                PlausibilityConfiguration.builder(connectionStrategy, distAzCalc)
                        .maxSplays(maxNumSplays)
                        .build();

        ClusterRuptureBuilder builder = new ClusterRuptureBuilder(config);

        List<ClusterRupture> ruptures = builder.build(permutationStrategy);

        System.out.println("Built " + ruptures.size() + " total ruptures");
        assertEquals(36, ruptures.size());
    }

}
