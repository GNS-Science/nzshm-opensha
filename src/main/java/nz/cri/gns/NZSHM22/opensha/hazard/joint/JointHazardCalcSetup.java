package nz.cri.gns.NZSHM22.opensha.hazard.joint;

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
import org.opensha.sha.util.TectonicRegionType;

/**
 * Sets up the OpenSHA machinery for a joint hazard calculation: the experimental joint GMM and the
 * {@link SolHazardMapCalc} that it is driven through.
 *
 * <p>The GMM is registered for a single tectonic region type only. OpenSHA's {@code
 * TRTUtils.getIMRforTRT} applies a single-entry IMR map to every source regardless of the source's
 * own TRT, which is what we want here: the joint IMR does its own crustal/interface dispatch per
 * rupture, so it must see all sources.
 *
 * <p>Creating a setup locks its {@link JointHazardInput}.
 */
public class JointHazardCalcSetup {

    private final JointHazardInput input;

    private SolHazardMapCalc calc;

    public JointHazardCalcSetup(JointHazardInput input) {
        this.input = input;
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

    /**
     * The GMM map handed to OpenSHA's hazard calculators. It deliberately holds a single entry: a
     * single-entry map is applied to every source regardless of the source's tectonic region type,
     * so the joint GMM sees crustal, interface and joint ruptures alike and dispatches internally.
     */
    public static Map<TectonicRegionType, Supplier<ScalarIMR>> gmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map = new EnumMap<>(TectonicRegionType.class);
        map.put(TectonicRegionType.ACTIVE_SHALLOW, JointHazardCalcSetup::buildGmm);
        return map;
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
                            gmmSupplierMap(),
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
