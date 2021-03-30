package nz.cri.gns.NSHM.util;

import org.dom4j.DocumentException;

import java.io.IOException;

public class NSHMInversionDiagnosticsReportBuilder {
    String name;
    String ruptureSetName;
    String outputDir;
    String faultFilter;

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
    
    public NSHMInversionDiagnosticsReportBuilder setFaultFilter(String faultFilter) {
    	this.faultFilter = faultFilter;
    	return this;
    }

    public void generateRateDiagnosticsPlot() throws IOException, DocumentException {
        String[] args = new String[]
                {"--name", name,
                  "--rupture-set", ruptureSetName,
                  "--output-dir", outputDir};
        NSHM_InversionRateDiagnosticsPlot.create(args).generatePage();
    }

    public void generateFilteredInversionDiagnosticsReport() throws IOException, DocumentException {
        String[] args = new String[]
                {"--name", name,
                  "--rupture-set", ruptureSetName,
                  "--output-dir", outputDir,
                  "--fault-name", faultFilter};
        NSHM_FilteredInversionDiagnosticsReport.create(args).generatePage();
    }
    
    
    
}
