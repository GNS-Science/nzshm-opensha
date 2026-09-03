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
 * <p>The colour of a section is {@link SiteSourceContributions#getSectionRates()} itself, an
 * absolute rate of exceedance, reported over a round number of years so that the legend does not
 * read as a row of zeroes. It is deliberately not a share of the site's total: two solutions have
 * two different totals, so a share would colour the same amount of hazard differently on each of a
 * pair of maps meant to be read side by side, and a share threshold would drop sections from one
 * map that its neighbour still draws. {@link #setUnitYears} and {@link #setMaxRate} let a caller
 * fix the unit and the top of the scale across both.
 *
 * <p>The map answers "how much of this site's hazard passes through this section", which means the
 * contributions overlap: a rupture is credited to every section it runs over, so the section rates
 * sum to well over the site's total. That overlap is the point — a long multi-fault rupture reaches
 * the site through all of it, and drawing the whole footprint is what shows which ruptures the
 * hazard comes from.
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
 * carries {@link CPT#setLog10}, so the scalars stay linear rates and the legend is labelled in
 * rates rather than in their logarithms.
 *
 * <p>The top of the scale is anchored on {@link #setMaxRate}, or on the largest contribution on the
 * map, rounded up to a whole decade; the bottom is either {@link #setOmitBelowRate} or {@link
 * #setNumDecades} decades below the top.
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
    private double omitBelowRate = Double.NaN;
    private double maxRate = Double.NaN;
    private double unitYears = Double.NaN;

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
     * Sets the contribution, in 1/yr, below which a section is left off the map. The threshold
     * becomes the bottom of the colour scale, so it takes the place of {@link #setNumDecades}.
     *
     * <p>Unset by default, in which case every section that is a source for the site is drawn, the
     * scale runs {@link #setNumDecades} decades below the largest contribution, and anything below
     * that is clamped to the bottom colour.
     *
     * @param omitBelowRate the threshold in 1/yr, or {@link Double#NaN} to draw every source
     */
    public SiteSourceMapPlotter setOmitBelowRate(double omitBelowRate) {
        Preconditions.checkArgument(
                Double.isNaN(omitBelowRate) || omitBelowRate > 0, "omitBelowRate must be positive");
        this.omitBelowRate = omitBelowRate;
        return this;
    }

    /**
     * Sets the contribution the top of the colour scale is anchored on, in 1/yr. Defaults to the
     * largest contribution on this map, which frames each map on its own sources; pass the largest
     * over several solutions to put them all on one scale, which is what makes two of these maps
     * comparable at a glance.
     */
    public SiteSourceMapPlotter setMaxRate(double maxRate) {
        Preconditions.checkArgument(
                Double.isNaN(maxRate) || maxRate > 0, "maxRate must be positive");
        this.maxRate = maxRate;
        return this;
    }

    /**
     * Sets the number of years the rates are reported over on the legend. Defaults to whatever
     * {@link HazardLabels#rateUnitYears} makes of this map's own largest contribution; set it
     * explicitly so that two maps meant to be read together are labelled in the same unit.
     */
    public SiteSourceMapPlotter setUnitYears(double unitYears) {
        Preconditions.checkArgument(
                Double.isNaN(unitYears) || unitYears > 0, "unitYears must be positive");
        this.unitYears = unitYears;
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
        double[] rates = contributions.getSectionRates();
        double largest = max(rates);
        Preconditions.checkState(
                largest > 0, "No section contributes anything, so there is nothing to draw");
        Preconditions.checkState(
                Double.isNaN(omitBelowRate) || omitBelowRate < largest,
                "Every section contributes less than the %s /yr threshold; the largest is %s",
                omitBelowRate,
                largest);

        // the rates are scaled to a round number of years before anything else, so that the
        // threshold, the scale and the legend are all in the one unit the caller asked for
        double years = Double.isNaN(unitYears) ? HazardLabels.rateUnitYears(largest) : unitYears;
        double[] values = perUnit(rates, years);
        double omitBelow = omitBelowRate * years;

        // anchor the scale on the largest contribution, rounded up to a whole decade so that the
        // legend reads in round numbers. Anchoring on a rate given by the caller instead puts
        // several maps of the same site on one scale.
        double logMax =
                Math.ceil(Math.log10(Double.isNaN(maxRate) ? largest * years : maxRate * years));
        double logMin =
                Double.isNaN(omitBelowRate) ? logMax - numDecades : Math.log10(omitBelow);
        // with a threshold nothing drawn falls below the scale, so the clamp only bites when there
        // is none and the bottom decade has to hold whatever is under it
        List<FaultSection> drawn = drawn(rupSet, values, omitBelow);
        double[] scalars = scalars(values, omitBelow, Math.pow(10, logMin));

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
                HazardLabels.SECTION_HAZARD
                        + " ("
                        + HazardLabels.rateUnit(years)
                        + "; Sections Overlap)");

        mapMaker.setScatterSymbol(
                PlotSymbol.INV_TRIANGLE, 10f, PlotSymbol.INV_TRIANGLE, Color.BLACK);
        mapMaker.plotScatters(List.of(contributions.getSite()), Color.WHITE);

        mapMaker.plot(outputDir, prefix, title(contributions, siteName));
        return new File(outputDir, prefix + ".png");
    }

    /** The rates converted from 1/yr to per {@code unitYears} years. */
    protected static double[] perUnit(double[] rates, double unitYears) {
        double[] scaled = new double[rates.length];
        for (int i = 0; i < rates.length; i++) {
            scaled[i] = rates[i] * unitYears;
        }
        return scaled;
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
     * @param value the section's contribution, in the map's own rate unit
     * @param omitBelow the threshold in the same unit, or {@link Double#NaN} to draw every source
     */
    protected static boolean isDrawn(double value, double omitBelow) {
        return value > 0 && (Double.isNaN(omitBelow) || value >= omitBelow);
    }

    /**
     * The scalars the map is coloured by: the contribution itself, aligned with {@link #drawn} and
     * clamped at the bottom of the scale.
     *
     * <p>These stay linear rates even though the scale is logarithmic. A log10 {@link CPT} takes
     * linear values and logs them itself, which is what lets the legend be labelled in rates.
     *
     * @param omitBelow the threshold, or {@link Double#NaN} to draw every source
     * @param clampAt bottom of the colour scale, in the same unit
     */
    protected static double[] scalars(double[] values, double omitBelow, double clampAt) {
        double[] scalars = new double[numDrawn(values, omitBelow)];
        int i = 0;
        for (double value : values) {
            if (isDrawn(value, omitBelow)) {
                scalars[i++] = Math.max(clampAt, value);
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
            FaultSystemRupSet rupSet, double[] values, double omitBelow) {
        List<FaultSection> drawn = new ArrayList<>(numDrawn(values, omitBelow));
        for (int s = 0; s < values.length; s++) {
            if (isDrawn(values[s], omitBelow)) {
                drawn.add(rupSet.getFaultSectionData(s));
            }
        }
        return drawn;
    }

    /** How many sections the map draws. */
    protected static int numDrawn(double[] values, double omitBelow) {
        int count = 0;
        for (double value : values) {
            if (isDrawn(value, omitBelow)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The palette rescaled onto a logarithmic scale running from {@code 10^logMin} to {@code
     * 10^logMax}.
     *
     * <p>The CPT's own values are the logarithms — that is what it is rescaled onto — but {@link
     * CPT#setLog10} tells it that, so it logs the linear rates handed to it and reports its bounds
     * as rates. OpenSHA then draws the legend on a logarithmic axis labelled in rates.
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

    protected static String title(SiteSourceContributions contributions, String siteName) {
        return siteName
                + " "
                + HazardLabels.SECTION_HAZARD
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
