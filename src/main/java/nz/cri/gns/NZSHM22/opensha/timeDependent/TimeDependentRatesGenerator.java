package nz.cri.gns.NZSHM22.opensha.timeDependent;

import com.google.gson.GsonBuilder;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.erf.FaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class TimeDependentRatesGenerator {

    MREData mreData = MREData.CFM_1_1;
    // in years
    long forecastTimespan = 50;
    double histOpenInterval = 200;
    int currentYear = 2022;

    String solutionFileName;
    String outputFileName;

    public TimeDependentRatesGenerator setSolutionFileName(String solutionFileName) {
        this.solutionFileName = solutionFileName;
        return this;
    }

    public TimeDependentRatesGenerator setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
        return this;
    }

    public TimeDependentRatesGenerator setMREData(String mreName) {
        mreData = MREData.valueOf(mreName);
        return this;
    }

    /**
     * in years
     *
     * @param forecastTimespan
     * @return
     */
    public TimeDependentRatesGenerator setForecastTimespan(long forecastTimespan) {
        this.forecastTimespan = forecastTimespan;
        return this;
    }

    /**
     * in years
     *
     * @param histOpenInterval
     * @return
     */
    public TimeDependentRatesGenerator setHistOpenInterval(double histOpenInterval) {
        this.histOpenInterval = histOpenInterval;
        return this;
    }

    /**
     * in years, default is 2022
     *
     * @param currentYear
     * @return
     */
    public TimeDependentRatesGenerator setCurrentYear(int currentYear) {
        this.currentYear = currentYear;
        return this;
    }

    public FaultSystemSolutionERF loadERF(FaultSystemSolution solution) {
        FaultSystemSolutionERF erf = new FaultSystemSolutionERF(solution);
        erf.getTimeSpan().setDuration(forecastTimespan); // 50 years
        erf.updateForecast();
        return erf;
    }

    private String getMetaData() {
        Map<String, String> meta = new HashMap<>();
        meta.put("MREdata", mreData.name());
        meta.put("forecastTimespan", "" + forecastTimespan);
        meta.put("histOpenInterval", "" + histOpenInterval);
        meta.put("currentYear", "" + currentYear);
        return new GsonBuilder().create().toJson(meta);
    }

    private String getWarning() {
        return "# Attention\n\n" +
                "This Inversion Solution archive has been modified\n" +
                "using the TimeDependentRatesGenerator tool.\n\n" +
                "That rates in the solution are modified and stored in a backup file:\n" +
                "    - /solution/rates.csv has the modified rates\n" +
                "    - /solution/old-rates.csv has the original rates.\n";
    }

    private void updateSolutionFile(String newRates) throws IOException {
        Path originalFile = Paths.get(solutionFileName);
        Path modifiedFile = Paths.get(outputFileName);
        Files.copy(originalFile, modifiedFile);
        try (FileSystem fs = FileSystems.newFileSystem(modifiedFile, null)) {

            Path rates = fs.getPath("/solution/rates.csv");
            Path oldRates = fs.getPath("/solution/old-rates.csv");
            Files.move(rates, oldRates);

            Path metaFile = fs.getPath("/solution/mre.json");
            Files.writeString(metaFile, getMetaData());

            Path warningFile = fs.getPath("/WARNING.md");
            Files.writeString(warningFile, getWarning());

            Files.writeString(rates, newRates);
        }
    }

    public String generateRates(FaultSystemSolution solution) throws IOException {
        FaultSystemSolutionERF erf = loadERF(solution);
        mreData.apply(solution, currentYear);
        long currentDate = MREData.yearsAgoInMillis(currentYear, 0);
        FaultSystemRupSet rupSet = solution.getRupSet();
        ProbabilityModelsCalc probabilityModelsCalc = new ProbabilityModelsCalc(solution, erf.getLongTermRateOfFltSysRupInERF(), MagDependentAperiodicityOptions.MID_VALUES);
        StringBuilder result = new StringBuilder();
        result.append("Rupture Index,Annual Rate\n");
        ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            double rate = solution.getRateForRup(r);
            boolean rupTooSmall = NZSHM22_FaultSystemRupSetCalc.isRuptureBelowSectMinMag(rupSet, r, minMags);
            if (rate > 0 && !rupTooSmall) {
                double probGain = probabilityModelsCalc.getU3_ProbGainForRup(r, histOpenInterval, false, true, true, currentDate, forecastTimespan);
                double rupProb = probGain * rate * forecastTimespan;
                double rupRate = -Math.log(1 - rupProb) / forecastTimespan;
                result.append(r).append(",").append(rupRate).append("\n");
            } else {
                result.append(r).append(",").append(rate).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Generates the new rates and creates a new solution archive. Returns the new file name.
     *
     * @return the new file name
     * @throws IOException
     */
    public void generate() throws IOException {
        FaultSystemSolution solution = FaultSystemSolution.load(new File(solutionFileName));
        String rates = generateRates(solution);
        updateSolutionFile(rates);
    }

    public static void main(String[] args) throws IOException {
        TimeDependentRatesGenerator generator =
                new TimeDependentRatesGenerator()
                        .setSolutionFileName("C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6MTA1MTE1.zip")
                        .setOutputFileName("C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6MTA1MTE1-mre.zip")
                        .setCurrentYear(2022)
                        .setMREData(MREData.CFM_1_1.name())
                        .setForecastTimespan(50);

        generator.generate();
    }
}
