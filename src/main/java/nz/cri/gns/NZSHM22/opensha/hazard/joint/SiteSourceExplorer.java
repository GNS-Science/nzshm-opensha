package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.util.EnumMap;
import java.util.List;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Works out which ruptures of a solution drive the hazard at a single site: a disaggregation by
 * source, kept in solution rupture indices so that the answer can be drawn on a map. See {@link
 * SiteSourceContributions} for what a contribution is and {@link SiteSourceMapPlotter} for the map.
 *
 * <p>This is a prototype for exploring the sources of a site's hazard, so it deliberately keeps the
 * whole per-rupture vector rather than a top-N list.
 *
 * <p>It reuses the ERF, the GMMs and the source filters of {@link JointHazardCalcSetup}, so the
 * numbers are consistent with the maps and site curves of {@link JointHazardMapCalculator} — the
 * contributions of a site sum to that site's rate of exceedance.
 *
 * <p>Under the hood this is OpenSHA's {@link DisaggregationCalculator}. That calculator returns a
 * contribution for <em>every</em> source that survives the distance filters, sorted by
 * contribution; its {@code numSourcesToShow} only truncates the human-readable text report, not the
 * list. The ERF is a fault-system ERF with the background excluded, so one source is one solution
 * rupture and the source list is a per-rupture list.
 */
public class SiteSourceExplorer {

    /**
     * Magnitude range of the disaggregation's internal magnitude/distance/epsilon histogram: bins
     * of 0.5 from 5.0 up to 11.0. The histogram is not used here, but ruptures outside its bounds
     * are reported as out of range, so the bounds are set wide enough to cover a joint rupture set.
     * They do not affect the per-source contributions.
     */
    public static final double MIN_MAG = 5d;

    public static final int NUM_MAG_BINS = 12;
    public static final double DELTA_MAG = 0.5d;

    /** Distance range of that histogram: bins of 20km out to 600km. See {@link #MIN_MAG}. */
    public static final double MIN_DIST = 0d;

    public static final int NUM_DIST_BINS = 30;
    public static final double DELTA_DIST = 20d;

    private final JointHazardCalcSetup setup;
    private final JointHazardMapCalculator calculator;

    public SiteSourceExplorer(JointHazardCalcSetup setup) {
        this.setup = setup;
        this.calculator = new JointHazardMapCalculator(setup);
    }

    public SiteSourceExplorer(JointHazardInput input) {
        this(new JointHazardCalcSetup(input));
    }

    public SiteSourceExplorer(FaultSystemSolution solution) {
        this(new JointHazardInput(solution));
    }

    public JointHazardCalcSetup getSetup() {
        return setup;
    }

    /** The hazard curve at the site, in linear IML against annual probability of exceedance. */
    public DiscretizedFunc siteCurve(Location location, double period) {
        return calculator.calcSiteCurve(location, period);
    }

    /**
     * The intensity measure level a site's hazard curve reaches at a return period, i.e. the value
     * that a hazard map at that return period would show for the site.
     *
     * @param location the site
     * @param period the calculation period, 0 for PGA
     * @param returnPeriod the return period to read the curve at
     * @return the intensity measure level in linear units (g, or cm/s for PGV)
     * @throws IllegalStateException if the curve never reaches the return period, i.e. the site's
     *     hazard is too low for the level to be defined
     */
    public double imlForReturnPeriod(Location location, double period, ReturnPeriods returnPeriod) {
        return imlForReturnPeriod(siteCurve(location, period), returnPeriod);
    }

    /**
     * {@link #imlForReturnPeriod(Location, double, ReturnPeriods)} for an already computed curve.
     */
    public static double imlForReturnPeriod(DiscretizedFunc curve, ReturnPeriods returnPeriod) {
        double poe = returnPeriod.oneYearProb;
        Preconditions.checkState(
                poe <= curve.getMaxY(),
                "The curve does not reach %s (%s per year); its largest probability of exceedance is"
                        + " %s. The site's hazard is too low for this return period.",
                returnPeriod.label,
                poe,
                curve.getMaxY());
        if (poe < curve.getMinY()) {
            // saturated: the curve runs off the top of the IML grid, so take its largest IML
            return curve.getMaxX();
        }
        return curve.getFirstInterpolatedX_inLogXLogYDomain(poe);
    }

    /**
     * Per-rupture contributions to the hazard at a site, at the intensity measure level the site's
     * own hazard curve reaches at the given return period. This is the usual framing: "what drives
     * the 10% in 50 year shaking at Wellington".
     */
    public SiteSourceContributions explore(
            Location location, double period, ReturnPeriods returnPeriod) {
        return exploreAtIml(location, period, imlForReturnPeriod(location, period, returnPeriod));
    }

    /**
     * Per-rupture contributions to the hazard at a site, at a given intensity measure level.
     *
     * @param location the site
     * @param period the calculation period, 0 for PGA
     * @param iml the intensity measure level, in linear units (g, or cm/s for PGV)
     * @throws IllegalStateException if no rupture contributes anything at the level
     */
    public SiteSourceContributions exploreAtIml(Location location, double period, double iml) {
        FaultSystemSolution solution = setup.getInput().getSolution();
        BaseFaultSystemSolutionERF erf = setup.getCalc().getERF();
        EnumMap<TectonicRegionType, ScalarIMR> gmms = setup.buildGmmMap(period);
        Site site = setup.buildSite(location);

        DisaggregationCalculator disagg = new DisaggregationCalculator();
        disagg.setMagRange(MIN_MAG, NUM_MAG_BINS, DELTA_MAG);
        disagg.setDistanceRange(MIN_DIST, NUM_DIST_BINS, DELTA_DIST);
        // the text report is not used; we keep the source list instead
        disagg.setNumSourcesToShow(0);

        // the GMMs work in natural log space, and so does the level the disaggregation is given
        boolean success =
                disagg.disaggregate(
                        Math.log(iml),
                        site,
                        gmms,
                        erf,
                        JointHazardCalcSetup.sourceFilters(),
                        DisaggregationCalculator.getDefaultParams());
        Preconditions.checkState(
                success,
                "Nothing contributes to exceeding %s at %s. Either the level is above anything the"
                        + " solution can produce at this site, or every source was dropped by the"
                        + " distance filters.",
                iml,
                location);

        return new SiteSourceContributions(
                solution, location, period, iml, rupRates(erf, disagg, solution));
    }

    /**
     * Turns the disaggregation's per-source contributions into per-rupture contributions, indexed
     * by rupture index in the solution.
     *
     * <p>A fault system ERF only holds sources for ruptures with a non-zero rate, so the source
     * index is not the rupture index and has to be mapped back through {@link
     * BaseFaultSystemSolutionERF#getFltSysRupIndexForSource}. Sources past the fault system ones
     * are gridded seismicity; the joint solutions carry no grid source provider so there should be
     * none, but they are skipped rather than mapped in case a caller supplies a solution that does.
     */
    protected static double[] rupRates(
            BaseFaultSystemSolutionERF erf,
            DisaggregationCalculator disagg,
            FaultSystemSolution solution) {
        double[] rupRates = new double[solution.getRupSet().getNumRuptures()];
        int numFaultSources = erf.getNumFaultSystemSources();
        List<DisaggregationSourceRuptureInfo> sources = disagg.getDisaggregationSourceList();
        for (DisaggregationSourceRuptureInfo source : sources) {
            if (source.getId() >= numFaultSources) {
                continue;
            }
            rupRates[erf.getFltSysRupIndexForSource(source.getId())] += source.getRate();
        }
        return rupRates;
    }
}
