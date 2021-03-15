package nz.cri.gns.NSHM.util;

import org.dom4j.DocumentException;

import java.io.IOException;

public class NSHMInversionDiagnosticsReportBuilder {
    String name;
    String ruptureSetName;
    String outputDir;

    public NSHMInversionDiagnosticsReportBuilder() {
    }

    public NSHMInversionDiagnosticsReportBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public NSHMInversionDiagnosticsReportBuilder setRuptureSetName(String ruptureSetName) {
        this.ruptureSetName = ruptureSetName;
        return this;
    }

    public NSHMInversionDiagnosticsReportBuilder setOutputDir(String outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public void generate() throws IOException, DocumentException {
        String[] args = new String[]
                {"--name", name,
                        "--rupture-set", ruptureSetName,
                        "--output-dir", outputDir};
        NSHM_InversionDiagnosticsReport.create(args).generatePage();
    }

}
