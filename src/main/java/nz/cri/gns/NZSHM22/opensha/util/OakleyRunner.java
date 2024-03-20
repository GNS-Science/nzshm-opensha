package nz.cri.gns.NZSHM22.opensha.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OakleyRunner {

    String inputPath = "INPUT/";
    String outputPath = "OUTPUT/";

    PythonGatewayJsonRunner.MapWithPrimitives arguments;

    public OakleyRunner(PythonGatewayJsonRunner.MapWithPrimitives arguments) {
        this.arguments = arguments;
    }

    void ensurePaths() throws IOException {
        Path inPath = Paths.get(inputPath);
        if (Files.notExists(inPath)) {
            System.err.println("Creating input path " + inputPath);
            Files.createDirectories(inPath);
        }
        Path outPath = Paths.get(outputPath);
        if (Files.notExists(outPath)) {
            System.err.println("Creating output path " + outputPath);
            Files.createDirectories(outPath);
        }

        if (arguments.containsKey("rupture_set")) {
            Path ruptureSetPath = Paths.get(inputPath, arguments.get("rupture_set"));
            if (Files.notExists(ruptureSetPath)) {
                System.err.println("Rupture set file does not exist at " + ruptureSetPath);
                if (arguments.containsKey("rupture_set_file_id")) {
                    System.err.println("Try to download the rupture set from http://simple-toshi-ui.s3-website-ap-southeast-2.amazonaws.com/FileDetail/" + arguments.get("rupture_set_file_id"));
                }
            }
        }
    }

    static void runCrustalInversion(PythonGatewayJsonRunner.MapWithPrimitives arguments)

    public static void main(String[] args) throws IOException {
        OakleyRunner runner = new OakleyRunner(NZSHM22_Parameters.INVERSION_CRUSTAL.getParameters());
        runner.ensurePaths();


    }
}
