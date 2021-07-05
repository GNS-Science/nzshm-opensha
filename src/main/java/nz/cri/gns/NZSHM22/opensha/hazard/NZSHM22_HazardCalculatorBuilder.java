package nz.cri.gns.NZSHM22.opensha.hazard;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;

import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemSolution;
import org.dom4j.DocumentException;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.params.MaxDistanceParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

/**
 * Creates a NZSHM22_HazardCalculator
 */
public class NZSHM22_HazardCalculatorBuilder {

    // config
    File solutionFile;
    Double forecastTimespan;
    Double maxDistance; // in km, default is 200
    boolean linear = false;

    /**
     * Sets the solution file.
     * @param solutionFile the solution file
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setSolutionFile(File solutionFile) {
        this.solutionFile = solutionFile;
        return this;
    }

    /**
     * Sets the solution file by using the file name
     * @param solutionFileName the file name of the solution file
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setSolutionFile(String solutionFileName) {
        return setSolutionFile(new File(solutionFileName));
    }

    /**
     * Sets the forecast timespan in years
     * @param duration the duration in years
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setForecastTimespan(double duration) {
        this.forecastTimespan = duration;
        return this;
    }

    /**
     * Sets the maximum distance of the site to a rupture in km.
     * @param distance the distance in km
     * @return this builder.
     */
    public NZSHM22_HazardCalculatorBuilder setMaxDistance(double distance) {
        this.maxDistance = distance;
        return this;
    }

    /**
     * Sets whether the hazard is returned as a linear or log curve.
     * @param linear whether the result should be a linear curve
     * @return this builder.
     */
    public NZSHM22_HazardCalculatorBuilder setLinear(boolean linear) {
        this.linear = linear;
        return this;
    }

    protected FaultSystemSolutionERF loadERF() throws IOException, DocumentException {
        FaultSystemSolution fss = NZSHM22_InversionFaultSystemSolution.fromFile(solutionFile);

        FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fss);
        if (forecastTimespan != null) {
            erf.getTimeSpan().setDuration(forecastTimespan); // 50 years
        }
        erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
        erf.updateForecast();
//        System.out.println("ERF has " + erf.getNumSources() + " sources");
        return erf;
    }

    protected ScalarIMR createGmpe() {
        ScalarIMR gmpe = AttenRelRef.ASK_2014.instance(null);
        gmpe.setParamDefaults();
        // for PGA (units: g)
//		gmpe.setIntensityMeasure(PGA_Param.NAME);
        // for 1s SA (units: g)
        gmpe.setIntensityMeasure(SA_Param.NAME);
        SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), 1d);
        return gmpe;
    }

    /**
     * Builds and NZSHM22_HazardCalculator based on the settings.
     * @return the NZSHM22_HazardCalculator
     * @throws IOException
     * @throws DocumentException
     */
    public NZSHM22_HazardCalculator build() throws IOException, DocumentException {
        FaultSystemSolutionERF erf = loadERF();
        ScalarIMR gmpe = createGmpe();
        return new ConcreteNSHMHazardCalculator(erf, gmpe);
    }

    class ConcreteNSHMHazardCalculator implements NZSHM22_HazardCalculator {

        FaultSystemSolutionERF erf;
        ScalarIMR gmpe;

        ConcreteNSHMHazardCalculator(FaultSystemSolutionERF erf, ScalarIMR gmpe) {
            this.erf = erf;
            this.gmpe = gmpe;
        }

        public DiscretizedFunc calc(double lat, double lon) {
            Site site = new Site(new Location(lat, lon));

            // simplest: use GMPE defaults
//		site.addParameterList(gmpe.getSiteParams());

            // need to clone it if you're going to have multiple sites in memory at once

            System.out.println("Site parameters:");
            for (Parameter<?> siteParam : gmpe.getSiteParams()) {

                siteParam = (Parameter<?>) siteParam.clone();
                // custom Vs30
//            if (siteParam.getName().equals(Vs30_Param.NAME))
//                // set Vs30 to 600 m/s
//                ((Parameter<Double>) siteParam).setValue(new Double(600d));
                site.addParameter(siteParam);
                System.out.println(siteParam.getName() + ": " + siteParam.getValue());
            }

            // x-values for the hazard curve
            DiscretizedFunc xValues = new IMT_Info().getDefaultHazardCurve(gmpe.getIntensityMeasure());
            System.out.println("Default x-values:\n" + xValues);

            // need natural log x-values for curve calculation
            DiscretizedFunc logHazCurve = new ArbitrarilyDiscretizedFunc();
            for (Point2D pt : xValues)
                logHazCurve.set(Math.log(pt.getX()), 1d); // y values don't matter yet

            HazardCurveCalculator calc = new HazardCurveCalculator();

            if (maxDistance != null) {
                calc.getAdjustableParams().getParameter(Double.class, MaxDistanceParam.NAME).setValue(maxDistance);
            }

            // Calculate the curve
            System.out.println("Calculating hazard curve");
            // this actually stores the y-values directly in logHazCurve
            calc.getHazardCurve(logHazCurve, site, gmpe, erf);

            if (linear) {
                // can convert back to linear if you want
                DiscretizedFunc linearHazCurve = new ArbitrarilyDiscretizedFunc();
                for (Point2D pt : logHazCurve)
                    linearHazCurve.set(Math.exp(pt.getX()), pt.getY());
                return linearHazCurve;
            } else {
                return logHazCurve;
            }

            // this was a 50 year curve, if you want to pull out the 2% value you can do this
//        double imlAt2percent = linearHazCurve.getFirstInterpolatedX_inLogXLogYDomain(0.02);
//        System.out.println("2% in " + (float) erf.getTimeSpan().getDuration() + " yr hazard: " + imlAt2percent);
        }
    }
}
