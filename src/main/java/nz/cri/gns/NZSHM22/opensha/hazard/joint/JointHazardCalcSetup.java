package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import java.awt.geom.Point2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.nshmp.shaded.gmm.NshmpGmm;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_AttenRelSupplier;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Sets up the OpenSHA machinery for a hazard calculation: the GMMs and the {@link SolHazardMapCalc}
 * that they are driven through. Which GMMs depends on the input's {@link JointHazardInput.GmmMode}.
 *
 * <p>In {@link JointHazardInput.GmmMode#JOINT_RUPTURE} the joint GMM is registered for a single
 * tectonic region type only. OpenSHA's {@code TRTUtils.getIMRforTRT} applies a single-entry IMR map
 * to every source regardless of the source's own TRT, which is what we want there: the joint IMR
 * does its own crustal/interface dispatch per rupture, so it must see all sources.
 *
 * <p>In {@link JointHazardInput.GmmMode#PER_TECTONIC_REGION} the map has one GMM per tectonic
 * region type and OpenSHA dispatches on the source's TRT instead. That only works if the rupture
 * set says what each rupture's TRT is, so the constructor makes sure it does.
 *
 * <p>The constructor applies the tectonic region types in {@link
 * JointHazardInput.GmmMode#JOINT_RUPTURE} too, even though nothing dispatches on them there. The
 * calculator's default source filter is {@code TectonicRegionDistCutoffFilter}, a per-region
 * distance cutoff, and without the module every source reaches it as {@link
 * TectonicRegionType#ACTIVE_SHALLOW} and is dropped beyond 300km instead of the 1000km that {@link
 * TectonicRegionType#SUBDUCTION_INTERFACE} allows. Subduction sources would then be culled well
 * inside their range, zeroing the hazard at sites that depend on a distant interface. Joint
 * ruptures are given {@link TectonicRegionType#SUBDUCTION_INTERFACE} so that they get the wider
 * cutoff; see {@link JointSolutions#tectonicRegimes(FaultSystemRupSet, TectonicRegionType)}.
 *
 * <p>Creating a setup locks its {@link JointHazardInput}.
 */
public class JointHazardCalcSetup {

    private final JointHazardInput input;

    private SolHazardMapCalc calc;

    public JointHazardCalcSetup(JointHazardInput input) {
        this.input = input;
        // the ERF reads source tectonic region types from this module, not from the sections, and
        // both the GMM dispatch and the source distance cutoffs are keyed off them
        JointSolutions.applyTectonicRegimes(
                input.getSolution().getRupSet(),
                input.getGmmMode() == JointHazardInput.GmmMode.PER_TECTONIC_REGION
                        ? null
                        : TectonicRegionType.SUBDUCTION_INTERFACE);
        input.lock();
    }

    public JointHazardInput getInput() {
        return input;
    }

    /**
     * Creates a configured instance of the experimental joint GMM. Parameter defaults are applied;
     * the intensity measure is set later, per period, by the calculation.
     */
    public static ScalarIMR buildGmm() {
        JointRuptureExperimentalIMR gmm = new JointRuptureExperimentalIMR();
        gmm.setParamDefaults();
        return gmm;
    }

    /** The crustal component GMM of the joint GMM, on its own. */
    public static ScalarIMR buildCrustalGmm() {
        return buildComponentGmm(JointRuptureExperimentalIMR.DEFAULT_CRUSTAL_GMM);
    }

    /** The interface component GMM of the joint GMM, on its own. */
    public static ScalarIMR buildInterfaceGmm() {
        return buildComponentGmm(JointRuptureExperimentalIMR.DEFAULT_INTERFACE_GMM);
    }

    protected static ScalarIMR buildComponentGmm(NshmpGmm gmm) {
        ScalarIMR imr = new NSHMP_AttenRelSupplier(gmm).get();
        imr.setParamDefaults();
        return imr;
    }

    /**
     * The GMM map for {@link JointHazardInput.GmmMode#JOINT_RUPTURE}. It deliberately holds a
     * single entry: a single-entry map is applied to every source regardless of the source's
     * tectonic region type, so the joint GMM sees crustal, interface and joint ruptures alike and
     * dispatches internally.
     */
    public static Map<TectonicRegionType, Supplier<ScalarIMR>> gmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map = new EnumMap<>(TectonicRegionType.class);
        map.put(TectonicRegionType.ACTIVE_SHALLOW, JointHazardCalcSetup::buildGmm);
        return map;
    }

    /**
     * The GMM map for {@link JointHazardInput.GmmMode#PER_TECTONIC_REGION}: the crustal and the
     * interface component of the joint GMM, each under its own tectonic region type. Using the
     * joint GMM's own components keeps the two modes comparable.
     *
     * <p>Unlike the single-entry map above, a map with more than one entry is a strict lookup by
     * source tectonic region type, so it has to cover every type the ERF contains — and the ERF
     * only reports anything other than ACTIVE_SHALLOW if the rupture set carries a {@code
     * RupSetTectonicRegimes} module. See {@link JointSolutions#applyTectonicRegimes}.
     */
    public static Map<TectonicRegionType, Supplier<ScalarIMR>> perTrtGmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map = new EnumMap<>(TectonicRegionType.class);
        map.put(TectonicRegionType.ACTIVE_SHALLOW, JointHazardCalcSetup::buildCrustalGmm);
        map.put(TectonicRegionType.SUBDUCTION_INTERFACE, JointHazardCalcSetup::buildInterfaceGmm);
        return map;
    }

    /** The GMM map for this setup's mode. */
    public Map<TectonicRegionType, Supplier<ScalarIMR>> gmmSuppliers() {
        return input.getGmmMode() == JointHazardInput.GmmMode.PER_TECTONIC_REGION
                ? perTrtGmmSupplierMap()
                : gmmSupplierMap();
    }

    /**
     * Instantiates this setup's GMMs and sets them to the given period. The map has the same shape
     * as {@link #gmmSuppliers()}, so OpenSHA dispatches sources to GMMs exactly the way the map
     * calculation does.
     *
     * @param period 0 for PGA, -1 for PGV, a positive value for an SA period in seconds
     */
    public EnumMap<TectonicRegionType, ScalarIMR> buildGmmMap(double period) {
        EnumMap<TectonicRegionType, ScalarIMR> gmms = new EnumMap<>(TectonicRegionType.class);
        for (Map.Entry<TectonicRegionType, Supplier<ScalarIMR>> entry : gmmSuppliers().entrySet()) {
            gmms.put(entry.getKey(), entry.getValue().get());
        }
        FaultSysHazardCalcSettings.setIMforPeriod(gmms, period);
        return gmms;
    }

    /**
     * A site at the given location, carrying the default reference site parameters (Vs30 and the
     * like) of every GMM in this setup. The parameters are the union over the GMMs, so the same
     * site can be handed to whichever GMM a source dispatches to.
     */
    public Site buildSite(Location location) {
        Site site = new Site(location);
        for (Parameter<?> siteParam :
                FaultSysHazardCalcSettings.getDefaultRefSiteParams(gmmSuppliers())) {
            site.addParameter((Parameter<?>) siteParam.clone());
        }
        return site;
    }

    /**
     * The source filters that hazard calculations use, i.e. the OpenSHA defaults. Chiefly {@code
     * TectonicRegionDistCutoffFilter}, which drops a source once it is further from the site than
     * the cutoff for its tectonic region type. Sources dropped by these filters contribute nothing
     * and are invisible to anything downstream, including {@link SiteSourceExplorer}.
     */
    public static List<SourceFilter> sourceFilters() {
        return FaultSysHazardCalcSettings.getDefaultSourceFilters().getEnabledFilters();
    }

    /**
     * The underlying map calculator, built on first use. Fault sources only; the joint rupture
     * solutions do not carry a grid source provider.
     */
    public SolHazardMapCalc getCalc() {
        if (calc == null) {
            calc =
                    new SolHazardMapCalc(
                            input.getSolution(),
                            gmmSuppliers(),
                            input.getRegion(),
                            IncludeBackgroundOption.EXCLUDE,
                            input.getPeriods());
            calc.setXVals(mapXVals());
        }
        return calc;
    }

    /** How far below the standard USGS grid the map curves are extended, as a factor on the IML. */
    static final double IML_EXTENSION_FACTOR = 0.1;

    /**
     * x values for the map curves: the standard USGS SA function, extended one decade downwards so
     * that low hazard sites still have usable curves.
     *
     * <p>{@code SolHazardMapCalc.buildMap} reports no hazard at all for a site whose curve does not
     * reach the map's return period, i.e. where the probability of exceeding the lowest IML in the
     * grid is still below {@code ReturnPeriods.oneYearProb}. The extra decade lifts the top of the
     * curve above that threshold for marginal sites.
     *
     * <p>One decade is enough. A hazard curve tends to {@code 1 - exp(-totalRate)} as the IML tends
     * to zero, where {@code totalRate} is the rate of every rupture that passes the source distance
     * filter, and it reaches that asymptote within a decade of the standard grid's lowest IML.
     * Extending further cannot raise the top of the curve and so cannot rescue any further site.
     */
    static ArbitrarilyDiscretizedFunc mapXVals() {
        ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
        for (Point2D pt : IMT_Info.getUSGS_SA_Function()) {
            xVals.set(pt);
        }
        xVals.set(xVals.getMinX() * IML_EXTENSION_FACTOR, 1d);
        return xVals;
    }
}
