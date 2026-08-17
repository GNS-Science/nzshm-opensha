package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import java.awt.geom.Point2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
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
 * <p>Creating a setup locks its {@link JointHazardInput}.
 */
public class JointHazardCalcSetup {

    private final JointHazardInput input;

    private SolHazardMapCalc calc;

    public JointHazardCalcSetup(JointHazardInput input) {
        this.input = input;
        if (input.getGmmMode() == JointHazardInput.GmmMode.PER_TECTONIC_REGION) {
            // the ERF reads source tectonic region types from this module, not from the sections
            JointSolutions.applyTectonicRegimes(input.getSolution().getRupSet());
        }
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

    private static ScalarIMR buildComponentGmm(Gmm gmm) {
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

    /**
     * x values for the map curves: the standard USGS SA function, extended downwards so that low
     * hazard sites still have usable curves.
     */
    static ArbitrarilyDiscretizedFunc mapXVals() {
        ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
        for (Point2D pt : IMT_Info.getUSGS_SA_Function()) {
            xVals.set(pt);
        }
        xVals.set(xVals.getMinX() * 0.1, 1d);
        xVals.set(xVals.getMinX() * 0.1, 1d);
        return xVals;
    }
}
