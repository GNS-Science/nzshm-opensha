package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Draws a {@link SiteSourceComparison} as a map: the sections that are a source for the site in
 * either solution, coloured by how much the hazard reaching the site through each of them changed.
 * The counterpart of {@link SiteSourceMapPlotter}, which draws one solution on its own.
 *
 * <p>The colour is the change itself — {@link SiteSourceComparison#getDifferences()}, the
 * comparison solution's contribution minus the reference solution's — on a diverging scale centred
 * on no change. A section's colour is therefore the rate of exceedance the new solution routes
 * through it that the old one did not, so the map says where the change in the site's total came
 * from and not merely which sections moved most in relative terms.
 *
 * <p>The scale is fitted to the map, each side rounded outwards on its own to a round number and
 * neither side clipped. A difference is always finite, including for a section that only one of the
 * two solutions has, so unlike a ratio there is nothing here that a scale cannot hold.
 *
 * <p>Colour follows the logarithm of the change, out from zero in both directions. A site's hazard
 * is usually dominated by one or two sections, and on a linear ramp their changes set the scale and
 * leave every other section in the first sliver of the palette, all but colourless — which is the
 * map at its least useful, because those are the sections a reader cannot get from the summary
 * numbers. Log spacing gives each decade of change the same share of the palette, so a section that
 * moved by a tenth of the largest change still has a colour worth reading.
 *
 * <p>What that costs is proportionality: on a log ramp one section's colour being twice as strong
 * as another's no longer means it carried twice the change, only that it was bigger. Colours stop
 * being additive in the eye, and a reader after the actual amounts wants the table or the CSV, not
 * the map. What the map keeps is the ordering and the order of magnitude, and it keeps them for
 * every section rather than for the largest one or two.
 *
 * <p>Changes smaller than {@link #setNoChangeRate} count as none and are drawn in one flat colour,
 * {@link #setZeroColor}, which says outright which sections did not really move. That rate defaults
 * to {@link #setOmitBelowRate}, i.e. the same amount of hazard that decides whether a section is
 * worth drawing at all, so a section is coloured only if it moved by as much as it had to carry to
 * be on the map in the first place. With neither set the ramp falls back to covering {@link
 * #DEFAULT_LOG_DECADES} decades below its largest change.
 *
 * <p>Changes are drawn per a round number of years rather than per year — see {@link #unitYears} —
 * because the rates involved are of the order of a thousandth per year, and a colour bar labelled
 * in those reads as a row of zeroes.
 *
 * <p>Sections that are negligible in <em>both</em> solutions are left off the map entirely; see
 * {@link #setOmitBelowRate}. The single solution maps grey those out instead, because there "small"
 * is still a value worth placing, but on a difference map they carry no change worth looking at and
 * only clutter the sections that do.
 */
public class SiteSourceDiffMapPlotter {

    /**
     * Decades of change the colour ramp covers when nothing says what counts as no change. Only a
     * fallback: a map that knows its own negligible rate uses that instead. See {@link
     * #setNoChangeRate}.
     */
    public static final double DEFAULT_LOG_DECADES = 3d;

    private double omitBelowRate = Double.NaN;
    private double noChangeRate = Double.NaN;
    private Color zeroColor = DivergingCPT.DEFAULT_ZERO_COLOR;
    private double bufferKm = SiteSourceMapPlotter.DEFAULT_BUFFER_KM;
    private Region region;
    private CPT cpt;

    /**
     * Sets the hazard a section has to carry, in at least one of the two solutions, to appear on
     * the map at all. Unset by default, in which case every section that is a source in either
     * solution is drawn.
     *
     * @param omitBelowRate the threshold in 1/yr, or {@link Double#NaN} to draw every section
     */
    public SiteSourceDiffMapPlotter setOmitBelowRate(double omitBelowRate) {
        Preconditions.checkArgument(
                Double.isNaN(omitBelowRate) || omitBelowRate > 0, "omitBelowRate must be positive");
        this.omitBelowRate = omitBelowRate;
        return this;
    }

    /**
     * Sets the change, in 1/yr, below which the map calls it no change: the floor of its log colour
     * ramp, and the half-width of the flat band drawn in {@link #setZeroColor}.
     *
     * <p>Defaults to {@link #setOmitBelowRate}, which is the amount of hazard a section has to
     * carry to be drawn at all, so that a section is given a colour only once it has moved by as
     * much as it needed to carry to be on the map. With neither set, the ramp covers {@link
     * #DEFAULT_LOG_DECADES} decades below its own largest change instead.
     *
     * @param noChangeRate the rate in 1/yr, or {@link Double#NaN} to fall back as above
     */
    public SiteSourceDiffMapPlotter setNoChangeRate(double noChangeRate) {
        Preconditions.checkArgument(
                Double.isNaN(noChangeRate) || noChangeRate > 0, "noChangeRate must be positive");
        this.noChangeRate = noChangeRate;
        return this;
    }

    /**
     * Sets the colour of the no-change band. Defaults to {@link DivergingCPT#DEFAULT_ZERO_COLOR};
     * pass null to leave the band in the palette's own neutral colour.
     */
    public SiteSourceDiffMapPlotter setZeroColor(Color zeroColor) {
        this.zeroColor = zeroColor;
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

        List<FaultSection> sections = sections(comparison, omitBelowRate);
        Preconditions.checkState(
                !sections.isEmpty(),
                "No section reaches the %s /yr threshold, so there would be nothing to draw",
                omitBelowRate);

        double[] rates = differences(comparison, omitBelowRate);
        double unitYears = unitYears(rates);
        double[] scalars = SiteSourceMaps.perUnit(rates, unitYears);

        GeographicMapMaker mapMaker = new RupSetMapMaker(sections, region(sections, comparison));
        // the sections come from two independently numbered rupture sets, so section ids are
        // not unique here and the GeoJSON writer keys its outline features by section id
        mapMaker.setWriteGeoJSON(false);
        mapMaker.setScalarThickness(3f);
        // sort by how far from unchanged a section is, so the biggest changes end up on top
        mapMaker.plotSectScalars(
                toList(scalars),
                toList(sortables(scalars)),
                differenceCPT(scalars, unitYears),
                HazardLabels.SECTION_HAZARD + " Change (" + HazardLabels.rateUnit(unitYears) + ")");

        SiteSourceMaps.markSite(mapMaker, comparison.getSite());

        mapMaker.plot(outputDir, prefix, title(comparison, siteName));
        return new File(outputDir, prefix + ".png");
    }

    /**
     * Whether a section carries enough of the site's hazard, in one solution or the other, to be
     * worth drawing.
     *
     * <p>The test is on an absolute rate rather than on a share of the site's total, because the
     * two solutions have different totals: a share threshold cuts the two sides at different
     * amounts of hazard and drops sections from one map that its neighbour still draws.
     *
     * @param maxRate the larger of the section's two contributions, see {@link
     *     SiteSourceComparison#getMaxRates()}
     * @param omitBelowRate the threshold in 1/yr, or {@link Double#NaN} to draw everything
     */
    protected static boolean isDrawn(double maxRate, double omitBelowRate) {
        return Double.isNaN(omitBelowRate) || maxRate >= omitBelowRate;
    }

    /** The sections the map draws, in the comparison's section order. */
    protected static List<FaultSection> sections(
            SiteSourceComparison comparison, double omitBelowRate) {
        List<FaultSection> all = comparison.getSections();
        double[] maxRates = comparison.getMaxRates();
        List<FaultSection> drawn = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (isDrawn(maxRates[i], omitBelowRate)) {
                drawn.add(all.get(i));
            }
        }
        return drawn;
    }

    /**
     * The change in contribution of each drawn section, in 1/yr, aligned with {@link #sections}.
     */
    protected static double[] differences(SiteSourceComparison comparison, double omitBelowRate) {
        double[] all = comparison.getDifferences();
        double[] maxRates = comparison.getMaxRates();
        double[] drawn = new double[numDrawn(maxRates, omitBelowRate)];
        int next = 0;
        for (int i = 0; i < all.length; i++) {
            if (isDrawn(maxRates[i], omitBelowRate)) {
                drawn[next++] = all[i];
            }
        }
        return drawn;
    }

    /** How many sections the map draws. */
    protected static int numDrawn(double[] maxRates, double omitBelowRate) {
        int count = 0;
        for (double maxRate : maxRates) {
            if (isDrawn(maxRate, omitBelowRate)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The number of years the changes are reported over. See {@link HazardLabels#rateUnitYears}.
     */
    protected static double unitYears(double[] rates) {
        return HazardLabels.rateUnitYears(SiteSourceMaps.maxAbs(rates));
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
     * The palette laid out over the changes on the map, logarithmically out from no change in both
     * directions. Each side is fitted to the data and rounded outwards on its own, so nothing is
     * clipped and a map whose sections all moved the same way still uses the whole ramp; the two
     * sides are coloured at the same rate, so an increase and a decrease of the same size look
     * equally strong. Changes below {@link #logFloor} are the no-change band. See {@link
     * DivergingCPT}.
     *
     * <p>If nothing changed at all, the ramp runs from -1 to 1 so that every section is drawn in
     * the no-change colour, which is the answer for two solutions that agree.
     *
     * @param scalars the changes, in the units the map is drawn in
     * @param unitYears the years those units are per, used to convert the no-change rate
     */
    protected CPT differenceCPT(double[] scalars, double unitYears) throws IOException {
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
        if (min == 0 && max == 0) {
            min = -1d;
            max = 1d;
        }
        return DivergingCPT.ramp(getCPT(), min, max)
                .logFloor(logFloor(min, max, unitYears))
                .zeroColor(zeroColor)
                .build();
    }

    /**
     * The magnitude the colour ramp bottoms out at, in the units the map is drawn in: {@link
     * #setNoChangeRate} if it has been set, otherwise {@link #setOmitBelowRate}, otherwise {@link
     * #DEFAULT_LOG_DECADES} decades below the larger side of the ramp.
     *
     * <p>A floor at or above the ramp's own extent leaves the whole map in the no-change colour.
     * That is the honest answer when no section moved by as much as the map calls negligible, and
     * {@link DivergingCPT} draws it as such rather than treating it as an error.
     */
    protected double logFloor(double min, double max, double unitYears) {
        double absolute = Double.isNaN(noChangeRate) ? omitBelowRate : noChangeRate;
        if (!Double.isNaN(absolute)) {
            return absolute * unitYears;
        }
        return Math.max(-min, max) / Math.pow(10, DEFAULT_LOG_DECADES);
    }

    /** A buffer around the drawn sections, or the region that was set. */
    protected Region region(List<FaultSection> sections, SiteSourceComparison comparison) {
        return SiteSourceMaps.region(
                region, sections, bufferKm, comparison.getSite(), comparison.getSections());
    }

    protected static String title(SiteSourceComparison comparison, String siteName) {
        return siteName
                + " "
                + HazardLabels.SECTION_HAZARD
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
