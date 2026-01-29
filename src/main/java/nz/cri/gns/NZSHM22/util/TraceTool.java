package nz.cri.gns.NZSHM22.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility to find out call sites for a method. Add TraceTool.trace() to the method you want to
 * trace. Call TraceTool.getTraces() before shutdown.
 */
public class TraceTool {

    static Set<String> traces = new HashSet<>();

    public static void trace() {
        String trace =
                Arrays.stream(Thread.currentThread().getStackTrace())
                        .skip(2)
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));
        traces.add(trace);
    }

    public static String getTraces() {
        return String.join("\n\n", traces);
    }
}
