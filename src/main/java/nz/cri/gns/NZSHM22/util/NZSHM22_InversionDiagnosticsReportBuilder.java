package nz.cri.gns.NZSHM22.util;

import org.dom4j.DocumentException;

import java.io.IOException;

public class NZSHM22_InversionDiagnosticsReportBuilder {
    String name;
    String ruptureSetName;
    String outputDir;
    String faultFilter;

    public NZSHM22_InversionDiagnosticsReportBuilder() {
    }

    public NZSHM22_InversionDiagnosticsReportBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public NZSHM22_InversionDiagnosticsReportBuilder setRuptureSetName(String ruptureSetName) {
        this.ruptureSetName = ruptureSetName;
        return this;
    }

    public NZSHM22_InversionDiagnosticsReportBuilder setOutputDir(String outputDir) {
        this.outputDir = outputDir;
        return this;
    }
    
    public NZSHM22_InversionDiagnosticsReportBuilder setFaultFilter(String faultFilter) {
    	this.faultFilter = faultFilter;
    	return this;
    }

    public void generateRateDiagnosticsPlot() throws IOException, DocumentException {
        String[] args = new String[]
                {"--name", name,
                  "--rupture-set", ruptureSetName,
                  "--output-dir", outputDir};        
        NZSHM22_InversionRateDiagnosticsPlot.create(args).generatePage();
    }

    public void generateFilteredInversionDiagnosticsReport() throws IOException, DocumentException {
        String[] args = new String[]
                {"--name", name,
                  "--rupture-set", ruptureSetName,
                  "--output-dir", outputDir,
                  "--fault-name", faultFilter};
        NZSHM22_FilteredInversionDiagnosticsReport.parseArgs(args);
        NZSHM22_FilteredInversionDiagnosticsReport.getReportBuilder().generatePage();
        //        NZSHM22_FilteredInversionDiagnosticsReport.createReportBuilder(args).generatePage();
    }
    
    
    
}
