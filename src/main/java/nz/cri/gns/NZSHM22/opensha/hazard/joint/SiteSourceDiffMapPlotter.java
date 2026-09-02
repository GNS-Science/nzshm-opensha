package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Draws a {@link SiteSourceComparison} as a map: the sections that are a source for the site in
 * either solution, coloured by how much the hazard reaching the site through each of them changed.
 * The counterpart of {@link SiteSourceMapPlotter}, which draws one solution on its own, and it
 * takes its weighting from the comparison so that a participation map and an influence map each get
 * their own difference map.
 *
 * <p>The colour is the change itself — {@link SiteSourceComparison#getDifferences()}, the
 * comparison solution's contribution minus the reference solution's — on a diverging scale centred
 * on no change. A section's colour is therefore the rate of exceedance the new solution routes
 * through it that the old one did not, which makes the colours additive: they say where the change
 * in the site's total came from, and a section carrying a thousandth of the site's hazard cannot
 * look like one carrying a tenth of it however much it moved in relative terms.
 *
 * <p>The scale is linear and fitted to the map, each side rounded outwards on its own to a round
 * number and neither side clipped. A difference is always finite, including for a section that only
 * one of the two solutions has, so unlike a ratio there is nothing here that a scale cannot hold.
 *
 * <p>Changes are drawn per a round number of years rather than per year — see {@link #unitYears} —
 * because the rates involved are of the order of a thousandth per year, and a colour bar labelled
 * in those reads as a row of zeroes.
 *
 * <p>Sections that are negligible in <em>both</em> solutions are left off the map entirely; see
 * {@link #setOmitBelowPercent}. The single solution maps grey those out instead, because there
 * "small" is still a value worth placing, but on a difference map they carry no change worth
 * looking at and only clutter the sections that do.
 */
public class SiteSourceDiffMapPlotter {

    private double omitBelowPercent = Double.NaN;
    private double bufferKm = SiteSourceMapPlotter.DEFAULT_BUFFER_KM;
    private Region region;
    private CPT cpt;

    /**
     * Sets the share of its own solution's hazard that a section has to reach, in at least one of
     * the two solutions, to appear on the map at all. Unset by default, in which case every section
     * that is a source in either solution is drawn.
     *
     * @param omitBelowPercent the threshold in percent, so 0.2 means "leave out unless the section
     *     carries 0.2% of the site's hazard in one solution or the other", or {@link Double#NaN} to
     *     draw everything
     */
    public SiteSourceDiffMapPlotter setOmitBelowPercent(double omitBelowPercent) {
        Preconditions.checkArgument(
                Double.isNaN(omitBelowPercent) || omitBelowPercent > 0,
                "omitBelowPercent must be positive");
        this.omitBelowPercent = omitBelowPercent;
        return this;
    }

    /** Sets the map region. Defaults to a buffer around the sections that are drawn. */
    public SiteSourceDiffMapPlotter setRegion(Region region) {
        this.region = region;
        return this;
    }

    /** Sets the padding around those sections used when no region has been set. */
    public SiteSourceDiffMapPlotter setBufferKm(double bufferKm) {
        this.bufferKm = bufferKm;
        return this;
    }

    /** Sets the colour palette. It has to be a diverging one for the map to read correctly. */
    public SiteSourceDiffMapPlotter setCPT(CPT cpt) {
        this.cpt = cpt;
        return this;
    }

    /** The palette, {@link GMT_CPT_Files#DIVERGING_BLUE_RED_UNIFORM} unless one has been set. */
    public CPT getCPT() throws IOException {
        if (cpt == null) {
            cpt = GMT_CPT_Files.DIVERGING_BLUE_RED_UNIFORM.instance();
        }
        return cpt;
    }

    /**
     * Writes the difference map.
     *
     * @param outputDir directory that the map is written to
     * @param prefix file name prefix, without an extension
     * @param comparison what to draw
     * @param siteName the site's name, used in the map title
     * @return the png that was written
     */
    public File plot(
            File outputDir, String prefix, SiteSourceComparison comparison, String siteName)
            throws IOException {
        Preconditions.checkState(
                outputDir.exists() || outputDir.mkdirs(),
                "Could not create output directory %s",
                outputDir.getAbsolutePath());

        List<FaultSection> sections = sections(comparison, omitBelowPercent);
        Preconditions.checkState(
                !sections.isEmpty(),
                "No section reaches the %s percent threshold, so there would be nothing to draw",
                omitBelowPercent);

        double[] rates = differences(comparison, omitBelowPercent);
        double unitYears = unitYears(rates);
        double[] scalars = perUnit(rates, unitYears);

        GeographicMapMaker mapMaker = new RupSetMapMaker(sections, region(sections, comparison));
        // the sections come from two independently numbered rupture sets, so section ids are
        // not unique here and the GeoJSON writer keys its outline features by section id
        mapMaker.setWriteGeoJSON(false);
        mapMaker.setScalarThickness(3f);
        // sort by how far from unchanged a section is, so the biggest changes end up on top
        mapMaker.plotSectScalars(
                toList(scalars),
                toList(sortables(scalars)),
                differenceCPT(scalars),
                comparison.getWeighting().getLabel() + " Change (" + rateUnit(unitYears) + ")");

        mapMaker.setScatterSymbol(
                PlotSymbol.INV_TRIANGLE, 10f, PlotSymbol.INV_TRIANGLE, Color.BLACK);
        mapMaker.plotScatters(List.of(comparison.getSite()), Color.WHITE);

        mapMaker.plot(outputDir, prefix, title(comparison, siteName));
        return new File(outputDir, prefix + ".png");
    }

    /**
     * Whether a section carries enough of the site's hazard, in one solution or the other, to be
     * worth drawing.
     *
     * @param maxPercentage the section's larger of its two shares, see {@link
     *     SiteSourceComparison#getMaxPercentages()}
     * @param omitBelowPercent the threshold, or {@link Double#NaN} to draw everything
     */
    protected static boolean isDrawn(double maxPercentage, double omitBelowPercent) {
        return Double.isNaN(omitBelowPercent) || maxPercentage >= omitBelowPercent;
    }

    /** The sections the map draws, in the comparison's section order. */
    protected static List<FaultSection> sections(
            SiteSourceComparison comparison, double omitBelowPercent) {
        List<FaultSection> all = comparison.getSections();
        double[] maxPercentages = comparison.getMaxPercentages();
        List<FaultSection> drawn = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (isDrawn(maxPercentages[i], omitBelowPercent)) {
                drawn.add(all.get(i));
            }
        }
        return drawn;
    }

    /**
     * The change in contribution of each drawn section, in 1/yr, aligned with {@link #sections}.
     */
    protected static double[] differences(
            SiteSourceComparison comparison, double omitBelowPercent) {
        double[] all = comparison.getDifferences();
        double[] maxPercentages = comparison.getMaxPercentages();
        double[] drawn = new double[sections(comparison, omitBelowPercent).size()];
        int next = 0;
        for (int i = 0; i < all.length; i++) {
            if (isDrawn(maxPercentages[i], omitBelowPercent)) {
                drawn[next++] = all[i];
            }
        }
        return drawn;
    }

    /**
     * The number of years the changes are reported over: the smallest power of ten that puts the
     * largest change on the map at one or above.
     *
     * <p>Section contributions are rates of the order of a thousandth per year, and a colour bar
     * labelled in those comes out as a row of zeroes, because the axis is formatted to a few
     * decimal places. Reporting the same numbers over a thousand or ten thousand years puts them in
     * a range a legend can print without saying anything different.
     *
     * @param rates the changes in 1/yr
     */
    protected static double unitYears(double[] rates) {
        double largest = 0;
        for (double rate : rates) {
            if (Double.isFinite(rate)) {
                largest = Math.max(largest, Math.abs(rate));
            }
        }
        if (largest <= 0) {
            return 1d;
        }
        return Math.pow(10, Math.max(0, Math.ceil(-Math.log10(largest))));
    }

    /** The rates converted from 1/yr to per {@code unitYears} years. */
    protected static double[] perUnit(double[] rates, double unitYears) {
        double[] scaled = new double[rates.length];
        for (int i = 0; i < rates.length; i++) {
            scaled[i] = rates[i] * unitYears;
        }
        return scaled;
    }

    /** How a rate over the given number of years is named on the colour bar. */
    protected static String rateUnit(double unitYears) {
        return unitYears == 1d
                ? "1/yr"
                : "per " + String.format("%,d", (long) unitYears) + " years";
    }

    /**
     * Sort keys that draw the sections in order of how big their change is, so that the ones that
     * changed most end up on top.
     */
    protected static double[] sortables(double[] scalars) {
        double[] sortables = new double[scalars.length];
        for (int i = 0; i < scalars.length; i++) {
            sortables[i] = Math.abs(scalars[i]);
        }
        return sortables;
    }

    static List<Double> toList(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    /**
     * The palette laid out over the changes on the map, with no change on its neutral colour. Each
     * side is fitted to the data and rounded outwards on its own, so nothing is clipped and a map
     * whose sections all moved the same way still uses the whole ramp. See {@link DivergingCPT}.
     */
    protected CPT differenceCPT(double[] scalars) throws IOException {
        double smallest = 0;
        double largest = 0;
        for (double scalar : scalars) {
            if (Double.isFinite(scalar)) {
                smallest = Math.min(smallest, scalar);
                largest = Math.max(largest, scalar);
            }
        }
        double min = -DivergingCPT.niceCeiling(-smallest);
        double max = DivergingCPT.niceCeiling(largest);
        Preconditions.checkState(
                min < 0 || max > 0, "Nothing changed at this site, so there is nothing to draw");
        return DivergingCPT.centredOnZero(getCPT(), min, max);
    }

    /** A buffer around the drawn sections, or the region that was set. */
    protected Region region(List<FaultSection> sections, SiteSourceComparison comparison) {
        if (region != null) {
            return region;
        }
        Region sectRegion = GeographicMapMaker.buildBufferedRegion(sections, bufferKm, true);
        return sectRegion.contains(comparison.getSite())
                ? sectRegion
                : GeographicMapMaker.buildBufferedRegion(comparison.getSections());
    }

    protected static String title(SiteSourceComparison comparison, String siteName) {
        return siteName
                + " "
                + comparison.getWeighting().getLabel()
                + " Change, "
                + HazardLabels.periodLabel(comparison.getPeriod())
                + " > "
                + (float) comparison.getIml()
                + HazardLabels.periodUnits(comparison.getPeriod())
                + " ("
                + (float) comparison.getReference().getTotalRate()
                + " -> "
                + (float) comparison.getComparison().getTotalRate()
                + "/yr)";
    }
}
