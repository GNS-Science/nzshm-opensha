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
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Draws {@link SiteSourceContributions} as a map: the fault sections of the solution, coloured by
 * how much of the site's hazard reaches it through each of them, with the site itself marked.
 *
 * <p>The colour of a section is its percentage contribution, from {@link
 * SiteSourceContributions#getSectionRates(SectionWeighting)}. Which question the map answers — "how
 * much hazard passes through this section" or "how much did this section shake the site" — is the
 * {@link SectionWeighting}; see there, because the two give very different maps for a rupture set
 * with long multi-fault ruptures.
 *
 * <p>Only the sections that matter are drawn. A section that no rupture reaching the site's
 * intensity level runs over is not a source for the site at all, and {@link #setOmitBelowPercent}
 * leaves out the ones that are a source but a negligible one. Both are left off rather than shaded,
 * because the map maker draws an outline for every section it is given and a rupture set that
 * reaches most of the country would otherwise bury the sections the map is about.
 *
 * <p>Sections are drawn in order of contribution, so a section carrying a large share of the site's
 * hazard is never hidden under a smaller one that happens to cross it. This is why the scalars are
 * also passed to the map maker as sort keys: without them it plots the surface fills of dipping
 * sections in the reverse of the order it plots their traces, which buries the strongest interface
 * sections under the weakest.
 *
 * <p>The colour scale is logarithmic, which is not cosmetic: contributions span many orders of
 * magnitude, so on a linear scale a single dominant fault would saturate the map and everything
 * else would be one colour. It is the <em>scale</em> that is logarithmic, not the values — the CPT
 * carries {@link CPT#setLog10}, so the scalars stay percentages and the legend is labelled in
 * percentages ("0.1%") rather than in their logarithms ("-1").
 *
 * <p>The top of the scale is anchored on the largest contribution, rounded up to a whole decade;
 * the bottom is either {@link #setGreyBelowPercent} or {@link #setNumDecades} decades below the
 * top. Anchoring on the data rather than on a fixed range keeps the map readable across sites whose
 * absolute hazard differs widely, at the cost of making colours incomparable between two such maps.
 */
public class SiteSourceMapPlotter {

    /** Default number of decades of contribution that the colour scale covers. */
    public static final int DEFAULT_NUM_DECADES = 3;

    /** Default padding, in km, around the contributing sections when the region is derived. */
    public static final double DEFAULT_BUFFER_KM = 50d;

    private int numDecades = DEFAULT_NUM_DECADES;
    private double bufferKm = DEFAULT_BUFFER_KM;
    private Region region;
    private CPT cpt;
    private SectionWeighting weighting = SectionWeighting.participation();
    private double omitBelowPercent = Double.NaN;

    /**
     * Sets how many decades of contribution the colour scale covers below the largest contributing
     * section. Defaults to {@link #DEFAULT_NUM_DECADES}.
     */
    public SiteSourceMapPlotter setNumDecades(int numDecades) {
        Preconditions.checkArgument(numDecades > 0, "numDecades must be positive");
        this.numDecades = numDecades;
        return this;
    }

    /**
     * Sets the contribution, as a percentage of the site's total, below which a section is left off
     * the map. The threshold becomes the bottom of the colour scale, so it takes the place of
     * {@link #setNumDecades}.
     *
     * <p>Unset by default, in which case every section that is a source for the site is drawn, the
     * scale runs {@link #setNumDecades} decades below the largest contribution, and anything below
     * that is clamped to the bottom colour.
     *
     * @param omitBelowPercent the threshold in percent, so 0.2 means "leave out below 0.2% of the
     *     site's hazard", or {@link Double#NaN} to draw every source
     */
    public SiteSourceMapPlotter setOmitBelowPercent(double omitBelowPercent) {
        Preconditions.checkArgument(
                Double.isNaN(omitBelowPercent) || omitBelowPercent > 0,
                "omitBelowPercent must be positive");
        this.omitBelowPercent = omitBelowPercent;
        return this;
    }

    /**
     * Sets the map region. Defaults to a {@link #setBufferKm} buffer around the sections that
     * contribute anything, which frames the map on the sources that matter rather than on the whole
     * solution.
     */
    public SiteSourceMapPlotter setRegion(Region region) {
        this.region = region;
        return this;
    }

    /** Sets the padding around the contributing sections used when no region has been set. */
    public SiteSourceMapPlotter setBufferKm(double bufferKm) {
        this.bufferKm = bufferKm;
        return this;
    }

    /**
     * Sets how each rupture's contribution is shared among its sections, which is what the map is
     * actually showing. Defaults to {@link SectionWeighting#participation()}.
     */
    public SiteSourceMapPlotter setWeighting(SectionWeighting weighting) {
        this.weighting = weighting;
        return this;
    }

    public SectionWeighting getWeighting() {
        return weighting;
    }

    /** Sets the colour palette. It is rescaled to the data, so pass an unscaled instance. */
    public SiteSourceMapPlotter setCPT(CPT cpt) {
        this.cpt = cpt;
        return this;
    }

    /** The colour palette, {@link GMT_CPT_Files#RAINBOW_UNIFORM} unless one has been set. */
    public CPT getCPT() throws IOException {
        if (cpt == null) {
            cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance();
        }
        return cpt;
    }

    /**
     * Writes the map. The ruptures behind it are written separately, by {@link
     * SiteSourceContributions#writeCSV}.
     *
     * @param outputDir directory that the map is written to
     * @param prefix file name prefix, without an extension
     * @param contributions what to draw
     * @param siteName the site's name, used in the map title
     * @return the png that was written
     */
    public File plot(
            File outputDir, String prefix, SiteSourceContributions contributions, String siteName)
            throws IOException {
        Preconditions.checkState(
                outputDir.exists() || outputDir.mkdirs(),
                "Could not create output directory %s",
                outputDir.getAbsolutePath());

        FaultSystemRupSet rupSet = contributions.getRupSet();
        double[] percentages = percentages(contributions);
        double max = max(percentages);
        Preconditions.checkState(
                max > 0, "No section contributes anything, so there is nothing to draw");
        Preconditions.checkState(
                Double.isNaN(omitBelowPercent) || omitBelowPercent < max,
                "Every section contributes less than the %s percent threshold; the largest is %s",
                omitBelowPercent,
                max);

        // anchor the scale on the largest contribution, rounded up to a whole decade so that the
        // legend reads in round numbers
        double logMax = Math.ceil(Math.log10(max));
        double logMin =
                Double.isNaN(omitBelowPercent) ? logMax - numDecades : Math.log10(omitBelowPercent);
        // with a threshold nothing drawn falls below the scale, so the clamp only bites when there
        // is none and the bottom decade has to hold whatever is under it
        List<FaultSection> drawn = drawn(rupSet, percentages, omitBelowPercent);
        double[] scalars = scalars(percentages, omitBelowPercent, Math.pow(10, logMin));

        // only the drawn sections are handed to the map maker, because it draws an outline for
        // every section it is given whether or not that section has a scalar
        GeographicMapMaker mapMaker =
                new RupSetMapMaker(drawn, region(drawn, rupSet, contributions));
        mapMaker.setScalarThickness(3f);
        // passing the scalars as sort keys as well makes every part of a section, fills included,
        // plot in contribution order, so the strongest sections end up on top
        mapMaker.plotSectScalars(
                scalars,
                scalars,
                logCPT(logMin, logMax),
                weighting.getLabel() + " (% of Site Hazard)");

        mapMaker.setScatterSymbol(
                PlotSymbol.INV_TRIANGLE, 10f, PlotSymbol.INV_TRIANGLE, Color.BLACK);
        mapMaker.plotScatters(List.of(contributions.getSite()), Color.WHITE);

        mapMaker.plot(outputDir, prefix, title(contributions, siteName));
        return new File(outputDir, prefix + ".png");
    }

    /** Each section's contribution as a percentage of the site's total rate of exceedance. */
    protected double[] percentages(SiteSourceContributions contributions) {
        double[] rates = contributions.getSectionRates(weighting);
        double total = contributions.getTotalRate();
        double[] percentages = new double[rates.length];
        for (int s = 0; s < rates.length; s++) {
            percentages[s] = 100 * rates[s] / total;
        }
        return percentages;
    }

    protected static double max(double[] values) {
        double max = 0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * Whether a section is worth drawing: it has to be a source for the site at all, and to clear
     * the threshold if there is one.
     *
     * @param percentage the section's share of the site's hazard
     * @param omitBelowPercent the threshold, or {@link Double#NaN} to draw every source
     */
    protected static boolean isDrawn(double percentage, double omitBelowPercent) {
        return percentage > 0 && (Double.isNaN(omitBelowPercent) || percentage >= omitBelowPercent);
    }

    /**
     * The scalars the map is coloured by: the percentage contribution itself, aligned with {@link
     * #drawn} and clamped at the bottom of the scale.
     *
     * <p>These stay linear percentages even though the scale is logarithmic. A log10 {@link CPT}
     * takes linear values and logs them itself, which is what lets the legend be labelled in
     * percentages.
     *
     * @param omitBelowPercent the threshold, or {@link Double#NaN} to draw every source
     * @param clampAtPercent bottom of the colour scale, in percent
     */
    protected static double[] scalars(
            double[] percentages, double omitBelowPercent, double clampAtPercent) {
        double[] scalars = new double[numDrawn(percentages, omitBelowPercent)];
        int i = 0;
        for (double percentage : percentages) {
            if (isDrawn(percentage, omitBelowPercent)) {
                scalars[i++] = Math.max(clampAtPercent, percentage);
            }
        }
        return scalars;
    }

    /**
     * The sections the map draws, in section order: the ones some rupture reaching the site's
     * intensity level runs over, less any that fall below the threshold. The rest are left off the
     * map entirely.
     */
    protected static List<FaultSection> drawn(
            FaultSystemRupSet rupSet, double[] percentages, double omitBelowPercent) {
        List<FaultSection> drawn = new ArrayList<>(numDrawn(percentages, omitBelowPercent));
        for (int s = 0; s < percentages.length; s++) {
            if (isDrawn(percentages[s], omitBelowPercent)) {
                drawn.add(rupSet.getFaultSectionData(s));
            }
        }
        return drawn;
    }

    /** How many sections the map draws. */
    protected static int numDrawn(double[] percentages, double omitBelowPercent) {
        int count = 0;
        for (double percentage : percentages) {
            if (isDrawn(percentage, omitBelowPercent)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The palette rescaled onto a logarithmic percentage scale running from {@code 10^logMin} to
     * {@code 10^logMax} percent.
     *
     * <p>The CPT's own values are the logarithms — that is what it is rescaled onto — but {@link
     * CPT#setLog10} tells it that, so it logs the linear percentages handed to it and reports its
     * bounds as percentages. OpenSHA then draws the legend on a logarithmic axis labelled in
     * percent.
     */
    protected CPT logCPT(double logMin, double logMax) throws IOException {
        CPT scaled = getCPT().rescale(logMin, logMax);
        scaled.setLog10(true);
        return scaled;
    }

    /**
     * The region to draw, either the one that was set or a buffer around the sections the map
     * draws. Falls back to the whole rupture set on the off chance that the site itself does not
     * land inside that buffer.
     */
    protected Region region(
            List<FaultSection> drawn,
            FaultSystemRupSet rupSet,
            SiteSourceContributions contributions) {
        if (region != null) {
            return region;
        }
        Region sectRegion = GeographicMapMaker.buildBufferedRegion(drawn, bufferKm, true);
        return sectRegion.contains(contributions.getSite())
                ? sectRegion
                : GeographicMapMaker.buildBufferedRegion(rupSet.getFaultSectionDataList());
    }

    protected String title(SiteSourceContributions contributions, String siteName) {
        return siteName
                + " "
                + weighting.getLabel()
                + ", "
                + HazardLabels.periodLabel(contributions.getPeriod())
                + " > "
                + (float) contributions.getIml()
                + HazardLabels.periodUnits(contributions.getPeriod())
                + " ("
                + (float) contributions.getTotalRate()
                + "/yr)";
    }
}
