package nz.cri.gns.NZSHM22.opensha.hazard;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GridSourceGenerator;
import org.dom4j.DocumentException;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.sourceFilters.params.MaxDistanceParam;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.gcim.imr.attenRelImpl.Bradley_2010_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.Bradley_ChchSpecific_2014_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedBradley_2010_AttenRel;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

/** Creates a NZSHM22_HazardCalculator */
public class NZSHM22_HazardCalculatorBuilder {

    // config
    ArchiveInput solutionFile;
    Double forecastTimespan;
    Double maxDistance; // in km, default is 200
    boolean linear = false;
    double intensityMeasurePeriod = 1; // default is SA with 1 second
    IncludeBackgroundOption backgroundOption = IncludeBackgroundOption.INCLUDE;
    ScalarIMR gmpe = null;

    /**
     * Sets the solution file.
     *
     * @param solutionFile the solution file
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setSolutionFile(File solutionFile) {
        try {
            this.solutionFile = new ArchiveInput.ZipFileInput(solutionFile);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        return this;
    }

    /**
     * Sets the solution file by using the file name
     *
     * @param solutionFileName the file name of the solution file
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setSolutionFile(String solutionFileName) {
        return setSolutionFile(new File(solutionFileName));
    }

    public NZSHM22_HazardCalculatorBuilder setSolution(ArchiveInput archiveInput) {
        this.solutionFile = archiveInput;
        return this;
    }

    /**
     * Sets the forecast timespan in years
     *
     * @param duration the duration in years
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setForecastTimespan(double duration) {
        this.forecastTimespan = duration;
        return this;
    }

    /**
     * Sets the GMPE Defaults to ASK_2104
     *
     * @param gmpe one of the values of the AttenRelRef enum, or one of Bradley_2010,
     *     Bradley_2010_int, Bradley_2010_Chch
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setGMPE(String gmpe) {
        switch (gmpe) {
            case "Bradley_2010":
                this.gmpe = new Bradley_2010_AttenRel(null);
                break;
            case "Bradley_2010_int":
                this.gmpe = new InterpolatedBradley_2010_AttenRel(null);
                break;
            case "Bradley_2010_Chch":
                this.gmpe = new Bradley_ChchSpecific_2014_AttenRel(null);
                break;
            default:
                this.gmpe = AttenRelRef.valueOf(gmpe).instance(null);
                break;
        }
        return this;
    }

    /**
     * Sets the maximum distance of the site to a rupture in km.
     *
     * @param distance the distance in km
     * @return this builder.
     */
    public NZSHM22_HazardCalculatorBuilder setMaxDistance(double distance) {
        this.maxDistance = distance;
        return this;
    }

    /**
     * Sets whether the hazard is returned as a linear or log curve.
     *
     * @param linear whether the result should be a linear curve
     * @return this builder.
     */
    public NZSHM22_HazardCalculatorBuilder setLinear(boolean linear) {
        this.linear = linear;
        return this;
    }

    /**
     * Sets the period of the intensity measure. If 0, the intensity measure is set to PGA,
     * otherwise to SA. Legal values are [0, 0.01, 0.02, 0.03, 0.05, 0.075, 0.1, 0.15, 0.2, 0.25,
     * 0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 7.5, 10.0]
     *
     * @param seconds period in seconds.
     * @return this builder.
     */
    public NZSHM22_HazardCalculatorBuilder setIntensityMeasurePeriod(double seconds) {
        intensityMeasurePeriod = seconds;
        return this;
    }

    /**
     * Sets to background option. Legal values are INCLUDE, EXCLUDE, ONLY
     *
     * @param backgroundOption
     * @return this builder
     */
    public NZSHM22_HazardCalculatorBuilder setBackgroundOption(String backgroundOption) {
        this.backgroundOption = IncludeBackgroundOption.valueOf(backgroundOption);
        return this;
    }

    protected FaultSystemSolution loadSolution() throws IOException {
        FaultSystemSolution fss = FaultSystemSolution.load(solutionFile);
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.fromContainer(fss.getRupSet());
        fss.getRupSet().addModule(branch);
        fss.removeModuleInstances(GridSourceProvider.class);
        fss.addAvailableModule(
                () -> new NZSHM22_GridSourceGenerator(fss), GridSourceProvider.class);
        return fss;
    }

    @SuppressWarnings("unchecked")
    protected FaultSystemSolutionERF loadERF() throws IOException {
        FaultSystemSolution fss = loadSolution();

        FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fss);
        if (forecastTimespan != null) {
            erf.getTimeSpan().setDuration(forecastTimespan); // 50 years
        }
        erf.getParameter(IncludeBackgroundParam.NAME).setValue(backgroundOption);
        erf.updateForecast();
        //        System.out.println("ERF has " + erf.getNumSources() + " sources");
        return erf;
    }

    protected ScalarIMR setUpGmpe() {
        if (gmpe == null) {
            setGMPE("ASK_2014");
        }
        gmpe.setParamDefaults();
        if (intensityMeasurePeriod == 0) {
            gmpe.setIntensityMeasure(PGA_Param.NAME);
        } else {
            gmpe.setIntensityMeasure(SA_Param.NAME);
            SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), intensityMeasurePeriod);
        }
        return gmpe;
    }

    /**
     * Builds and NZSHM22_HazardCalculator based on the settings.
     *
     * @return the NZSHM22_HazardCalculator
     * @throws IOException
     * @throws DocumentException
     */
    public NZSHM22_HazardCalculator build() throws IOException, DocumentException {
        FaultSystemSolutionERF erf = loadERF();
        ScalarIMR gmpe = setUpGmpe();
        return new ConcreteNSHMHazardCalculator(erf, gmpe);
    }

    class ConcreteNSHMHazardCalculator extends NZSHM22_HazardCalculator {

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

            //            System.out.println("Site parameters:");
            for (Parameter<?> siteParam : gmpe.getSiteParams()) {

                siteParam = (Parameter<?>) siteParam.clone();
                // custom Vs30
                //            if (siteParam.getName().equals(Vs30_Param.NAME))
                //                // set Vs30 to 600 m/s
                //                ((Parameter<Double>) siteParam).setValue(new Double(600d));
                site.addParameter(siteParam);
                //                System.out.println(siteParam.getName() + ": " +
                // siteParam.getValue());
            }

            // x-values for the hazard curve
            DiscretizedFunc xValues =
                    new IMT_Info().getDefaultHazardCurve(gmpe.getIntensityMeasure());
            //            System.out.println("Default x-values:\n" + xValues);

            // need natural log x-values for curve calculation
            DiscretizedFunc logHazCurve = new ArbitrarilyDiscretizedFunc();
            for (Point2D pt : xValues)
                logHazCurve.set(Math.log(pt.getX()), 1d); // y values don't matter yet

            HazardCurveCalculator calc = new HazardCurveCalculator();

            if (maxDistance != null) {
                calc.getAdjustableParams()
                        .getParameter(Double.class, MaxDistanceParam.NAME)
                        .setValue(maxDistance);
            }

            // Calculate the curve
            //            System.out.println("Calculating hazard curve");
            // this actually stores the y-values directly in logHazCurve
            calc.getHazardCurve(logHazCurve, site, gmpe, erf);

            if (linear) {
                // can convert back to linear if you want
                DiscretizedFunc linearHazCurve = new ArbitrarilyDiscretizedFunc();
                for (Point2D pt : logHazCurve) linearHazCurve.set(Math.exp(pt.getX()), pt.getY());
                return linearHazCurve;
            } else {
                return logHazCurve;
            }

            // this was a 50 year curve, if you want to pull out the 2% value you can do this
            //        double imlAt2percent =
            // linearHazCurve.getFirstInterpolatedX_inLogXLogYDomain(0.02);
            //        System.out.println("2% in " + (float) erf.getTimeSpan().getDuration() + " yr
            // hazard: " + imlAt2percent);
        }
    }

    public static void main(String[] args) throws DocumentException, IOException {
        NZSHM22_HazardCalculatorBuilder builder = new NZSHM22_HazardCalculatorBuilder();
        //
        // builder.setSolutionFile("C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjI0NTZaeXhVeQ==.zip")
        //                .setLinear(true)
        //                .setForecastTimespan(50);
        builder.setSolutionFile(
                        "C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6MTAwMDE3.zip")
                .setLinear(true)
                .setForecastTimespan(50)
                .setIntensityMeasurePeriod(10)
                .setGMPE("Bradley_2010")
                .setBackgroundOption("INCLUDE");

        NZSHM22_HazardCalculator calculator = builder.build();

        System.out.println(calculator.calc(-41.288889, 174.777222));
        System.out.println(calculator.tabulariseCalc(-41.288889, 174.777222));
    }
}
