package nz.cri.gns.NSHM.opensha.scripts;

import nz.cri.gns.NSHM.opensha.hazard.NSHMHazardCalculator;
import nz.cri.gns.NSHM.opensha.hazard.NSHMHazardCalculatorBuilder;
import org.apache.commons.cli.*;
import org.dom4j.DocumentException;

import java.io.IOException;

public class HazardCalculatorScript {

    public static CommandLine parseCommandLine(String[] args) throws ParseException {

        Options options = new Options()
                .addRequiredOption("f", "solution", true, "an opensha solution file")
                .addRequiredOption("t", "timespan", true, "the forecast duration in years")
                .addOption("d", "maxDistance", true, "The maximum distance between site and rupture in km")
                .addRequiredOption("a", "lat", true, "site latitude")
                .addRequiredOption("o", "lon", true, "site longitude")
                .addOption("l", "linear", true, "whether the hazard curve is linear, must be 'true' or 'false'");
        return new DefaultParser().parse(options, args);
    }

    public static void main(String[] args) throws ParseException, IOException, DocumentException {
        CommandLine cmd = parseCommandLine(args);
        NSHMHazardCalculatorBuilder builder = new NSHMHazardCalculatorBuilder()
                .setForecastTimespan(Double.parseDouble(cmd.getOptionValue("t")))
                .setSolutionFile(cmd.getOptionValue("f"))
                .setLinear("true".equals(cmd.getOptionValue("linear", "false")));
        if (cmd.hasOption("maxDistance")) {
            builder.setMaxDistance(Double.parseDouble(cmd.getOptionValue("maxDistance")));
        }

        NSHMHazardCalculator calculator = builder.build();
        System.out.println(
                calculator.calc(Double.parseDouble(cmd.getOptionValue("lat")),
                        Double.parseDouble(cmd.getOptionValue("lon"))));
    }
}
