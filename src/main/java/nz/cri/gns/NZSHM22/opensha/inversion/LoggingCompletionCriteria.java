package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import nz.cri.gns.NZSHM22.util.DataLogger;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;

/**
 * Can be used to wrap a CompletionCriteria to log all InversionState instances that are passed in.
 * Note that this will not work as a sub CompletionCriteria if the inner criteria relies on
 * iteration count. Logs will be broken up into zip files.
 */
public class LoggingCompletionCriteria implements CompletionCriteria, Closeable {

    protected final CompletionCriteria innerCriteria;

    protected InversionStateLog solutionLog;

    /**
     * Creates a new LoggingCompletionCriteria
     *
     * @param innerCriteria the isSatisfied() method will be forwarded to the innerCriteria
     * @param basePath a path to a folder. Does not need to exist. All log files will be stored in
     *     this folder.
     * @param maxMB log files will be split up if they reach this size (uncompressed)
     * @throws IOException
     */
    public LoggingCompletionCriteria(CompletionCriteria innerCriteria, String basePath, int maxMB)
            throws IOException {
        this.innerCriteria = innerCriteria;
        this.solutionLog = new InversionStateLog(basePath, maxMB);

        Files.createDirectories(new File(basePath).toPath());
    }

    @Override
    public boolean isSatisfied(InversionState state) {

        solutionLog.log(state);

        return innerCriteria.isSatisfied(state);
    }

    @Override
    public void close() throws IOException {
        solutionLog.close();
    }

    public void setConstraintRanges(List<ConstraintRange> constraintRanges) {
        AnnealingProgress progress = AnnealingProgress.forConstraintRanges(constraintRanges);
        String energiesHeader = String.join(",", progress.getEnergyTypes());
        solutionLog.addHeader("energy", energiesHeader + "\n");
    }

    protected static class InversionStateLog implements Closeable {

        DataLogger.MultiZipLog log;

        public InversionStateLog(String basePath, int maxMB) {
            log = new DataLogger.MultiZipLog(basePath, "inversionState", ((long) maxMB) * 1024 * 1024);
            addHeader(
                    "meta",
                    "iterations,elapsedTimeMillis,numPerturbsKept,numWorseValuesKept,numNonZero\n");
        }

        public void addHeader(String file, String header) {
            log.addHeader(file, header);
        }

        public void log(InversionState state) {
            log.nextIndex(state.iterations);

            String meta =
                    state.iterations
                            + ","
                            + state.elapsedTimeMillis
                            + ","
                            + state.numPerturbsKept
                            + ","
                            + state.numWorseValuesKept
                            + ","
                            + state.numNonZero
                            + "\n";

            log.log("meta", meta);
            log.log("solution", state.bestSolution);
            log.log("energy", state.energy);
            log.log("misfits", state.misfits);
            log.log("misfits_ineq", state.misfits_ineq);
        }

        @Override
        public void close() throws IOException {
            log.close();
        }
    }
}
