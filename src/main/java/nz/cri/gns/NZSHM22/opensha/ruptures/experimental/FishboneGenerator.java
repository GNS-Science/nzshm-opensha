package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.bbn.openmap.geo.Geo;
import com.bbn.openmap.geo.Rotation;
import com.google.common.base.Preconditions;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.*;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

/** Generates fishbone plots for joint ruptures. Experimental. */
public class FishboneGenerator {

    public static class FishbonePlot {
        public List<XY_DataSet> sectFuncs;
        public List<PlotCurveCharacterstics> sectChars;

        public FishbonePlot() {
            sectFuncs = new ArrayList<>();
            sectChars = new ArrayList<>();
        }

        public static void plotRotatedSection(
                FishboneGeometry.RotatedSection sect,
                List<XY_DataSet> funcs,
                List<PlotCurveCharacterstics> chars,
                PlotCurveCharacterstics traceChar,
                PlotCurveCharacterstics outlineChar) {
            if (sect.section.getAveDip() < 90d) {
                DefaultXY_DataSet xy = new DefaultXY_DataSet();
                for (Geo g : sect.rotatedSurface) {
                    xy.set(g.getLongitude(), g.getLatitude());
                }
                xy.set(xy.get(0));
                funcs.add(xy);
                chars.add(outlineChar);
            }

            DefaultXY_DataSet xy = new DefaultXY_DataSet();
            for (Geo g : sect.rotatedTrace) {
                xy.set(g.getLongitude(), g.getLatitude());
            }
            funcs.add(xy);
            chars.add(traceChar);
        }

        public static void plotSection(
                FishboneGeometry.RotatedSection sect,
                List<XY_DataSet> funcs,
                List<PlotCurveCharacterstics> chars,
                PlotCurveCharacterstics traceChar,
                PlotCurveCharacterstics outlineChar) {
            if (sect.section.getAveDip() < 90d) {
                DefaultXY_DataSet xy = new DefaultXY_DataSet();
                for (Location g : sect.surface) {
                    xy.set(g.getLongitude(), g.getLatitude());
                }
                xy.set(xy.get(0));
                funcs.add(xy);
                chars.add(outlineChar);
            }

            DefaultXY_DataSet xy = new DefaultXY_DataSet();
            for (Location g : sect.section.getFaultTrace()) {
                xy.set(g.getLongitude(), g.getLatitude());
            }
            funcs.add(xy);
            chars.add(traceChar);
        }

        protected void plotFishboneComponent(
                FishboneGeometry.RotatedSection section, PlotCurveCharacterstics traceChar) {
            DefaultXY_DataSet xy = new DefaultXY_DataSet();
            for (int s = 0; s < section.surface.size(); s++) {
                Geo g = section.rotatedSurface.get(s);
                Location l = section.surface.get(s);
                xy.set((g.getLatitude() - section.getLatitudeOrigin()) * 111.1, -l.depth);
            }
            xy.set(xy.get(0));
            sectFuncs.add(xy);
            sectChars.add(traceChar);
        }

        public void plotFishbone(FishboneGeometry geometry) {

            PlotCurveCharacterstics subductionChar =
                    new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED);
            PlotCurveCharacterstics outlineChar =
                    new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);

            for (FishboneGeometry.RotatedSection section : geometry.sections) {
                if (section.crossesPlane) {
                    if (FaultSectionProperties.isSubduction(section.section)) {
                        plotFishboneComponent(section, subductionChar);
                    } else {
                        plotFishboneComponent(section, outlineChar);
                    }
                }
            }
        }

        public void plotDebugSlice(FishboneGeometry geometry) {
            PlotCurveCharacterstics traceChar =
                    new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED);
            PlotCurveCharacterstics outlineChar =
                    new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);

            PlotCurveCharacterstics greytraceChar =
                    new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY);

            for (FishboneGeometry.RotatedSection section : geometry.sections) {
                plotSection(section, sectFuncs, sectChars, greytraceChar, outlineChar);
            }

            for (FishboneGeometry.RotatedSection section : geometry.sections) {
                if (section.crossesPlane) {
                    plotSection(section, sectFuncs, sectChars, traceChar, traceChar);
                }
            }
        }

        public static void plot(File outDior, String prefix, PlotSpec spec, boolean isLatLon)
                throws IOException {
            DataUtils.MinMaxAveTracker latTrack = new DataUtils.MinMaxAveTracker();
            DataUtils.MinMaxAveTracker lonTrack = new DataUtils.MinMaxAveTracker();
            for (PlotElement xy : spec.getPlotElems()) {
                for (Point2D pt : (XY_DataSet) xy) {
                    latTrack.addValue(pt.getY());
                    lonTrack.addValue(pt.getX());
                }
            }

            double minLon = lonTrack.getMin();
            double maxLon = lonTrack.getMax();
            double minLat = latTrack.getMin();
            double maxLat = latTrack.getMax();
            if (isLatLon) {
                maxLat += 0.05;
                minLat -= 0.05;
                maxLon += 0.05;
                minLon -= 0.05;
            } else {
                double extra = (maxLat - minLat) * 0.05;
                maxLat += extra;
                minLat -= extra;
                extra = (maxLon - minLon) * 0.05;
                maxLon += extra;
                minLon -= extra;
            }
            int width = 800;

            Range xRange = new Range(minLon, maxLon);
            Range yRange = new Range(minLat, maxLat);

            HeadlessGraphPanel gp = PlotUtils.initHeadless();

            gp.drawGraphPanel(spec, false, false, xRange, yRange);
            PlotUtils.setAxisVisible(gp, true, true);
            PlotUtils.setGridLinesVisible(gp, true, true);

            PlotUtils.writePlots(outDior, prefix, gp, width, isLatLon, true, false, false);
        }
    }

    public static class FishboneGeometry {
        public Rotation rotation;
        public double longitudePlane;
        public List<RotatedSection> sections;
        public double latitudeOrigin;

        public FishboneGeometry(ClusterRupture rupture, int slice) {
            FaultSection pivot =
                    ((DownDipFaultSubSectionCluster) rupture.clusters[0])
                            .getTraceSections()
                            .get(slice);
            Location first = pivot.getFaultTrace().first();
            Location last = pivot.getFaultTrace().last();

            double azimuth = LocationUtils.azimuth(first, last);
            this.rotation =
                    new Rotation(new Geo(first.lat, first.lon), Math.toRadians(azimuth + 90));

            Geo lastRot = rotation.rotate(new Geo(last.lat, last.lon));
            this.longitudePlane = first.lon + ((lastRot.getLongitude() - first.lon) / 2);
            this.latitudeOrigin = first.lat;
            this.sections =
                    rupture.buildOrderedSectionList().stream()
                            .map(RotatedSection::new)
                            .collect(Collectors.toList());
        }

        public Geo rotate(Location location) {
            return rotation.rotate(new Geo(location.lat, location.lon));
        }

        public boolean crossesPlane(List<Geo> locations) {
            boolean left = false;
            boolean right = false;
            for (Geo geo : locations) {
                if (geo.getLongitude() < longitudePlane) {
                    left = true;
                } else if (geo.getLongitude() > longitudePlane) {
                    right = true;
                }
                if (left && right) {
                    return true;
                }
            }
            return false;
        }

        public class RotatedSection {
            public List<Geo> rotatedSurface;
            public List<Geo> rotatedTrace;
            public List<Location> surface;
            public FaultSection section;
            public boolean crossesPlane;

            public RotatedSection(FaultSection section) {
                this.section = section;
                RuptureSurface surface = section.getFaultSurface(1, false, false);
                this.surface = surface.getPerimeter();
                rotatedSurface =
                        this.surface.stream()
                                .map(FishboneGeometry.this::rotate)
                                .collect(Collectors.toList());
                rotatedTrace =
                        section.getFaultTrace().stream()
                                .map(FishboneGeometry.this::rotate)
                                .collect(Collectors.toList());
                this.crossesPlane = crossesPlane(rotatedSurface);
            }

            public double getLatitudeOrigin() {
                return FishboneGeometry.this.latitudeOrigin;
            }
        }
    }

    public static PlotSpec buildTopDownDebug(ClusterRupture rupture, int slice) {
        Preconditions.checkArgument(rupture.clusters[0] instanceof DownDipFaultSubSectionCluster);

        FishboneGeometry geometry = new FishboneGeometry(rupture, slice);

        FishbonePlot overviewPlot = new FishbonePlot();
        overviewPlot.plotDebugSlice(geometry);

        return new PlotSpec(
                overviewPlot.sectFuncs,
                overviewPlot.sectChars,
                "fishbone debug",
                "Longitude",
                "Latitude");
    }

    public static void plotTopDownDebug(ClusterRupture rupture, int slice, File path, String prefix)
            throws IOException {
        PlotSpec spec = buildTopDownDebug(rupture, slice);
        FishbonePlot.plot(path, prefix, spec, true);
    }

    public static PlotSpec buildFishbone(ClusterRupture rupture, int slice) {
        Preconditions.checkArgument(rupture.clusters[0] instanceof DownDipFaultSubSectionCluster);

        FishboneGeometry geometry = new FishboneGeometry(rupture, slice);

        FishbonePlot fishbonePlot = new FishbonePlot();
        fishbonePlot.plotFishbone(geometry);

        return new PlotSpec(
                fishbonePlot.sectFuncs,
                fishbonePlot.sectChars,
                "Fishbone",
                "Horizontal Distance (km)",
                "Elevation (km)");
    }

    public static void plotFishbone(ClusterRupture rupture, int slice, File path, String prefix)
            throws IOException {
        PlotSpec spec = buildFishbone(rupture, slice);
        FishbonePlot.plot(path, prefix, spec, false);
    }

    /**
     * Generates a fishbone plot and a corresponding debug plot for each section in the topmost row
     * of the subduction cluster.
     *
     * @param rupture a joint rupture
     * @param path output directory
     * @param prefix output file prefix
     * @throws IOException
     */
    public static void plotAll(ClusterRupture rupture, File path, String prefix)
            throws IOException {
        DownDipFaultSubSectionCluster cluster = (DownDipFaultSubSectionCluster) rupture.clusters[0];
        for (int s = 0; s < cluster.getTraceSections().size(); s++) {
            plotFishbone(rupture, s, path, prefix + s);
            plotTopDownDebug(rupture, s, path, prefix + "debug" + s);
        }
    }
}
