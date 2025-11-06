package nz.earthsciences.jupyterlogger;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OpenShaLogger extends JupyterLogger {

    public static void initialise(String basePath) {
        synchronized (lock) {
            if (instance != null && !(instance instanceof NoOpLogger)) {
                throw new IllegalStateException("JupyterLogger is already initialised");
            }
            try {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-M-d_HH-mm-ss");
                String path = String.valueOf(Path.of(basePath, formatter.format(new Date())));
                instance = new OpenShaLogger(path);
                Runtime.getRuntime().addShutdownHook(new Thread(JupyterLogger::shutdownHook));
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }
    }

    public OpenShaLogger(String path) throws IOException {
        super(path);
    }

    public static OpenShaLogger logger() {
        if (instance == null) {
            instance = new NoOpLogger();
        }
        return (OpenShaLogger) instance;
    }

    /**
     * Adds an empty MFD plot to the notebook.
     *
     * @param prefix the prefix to use for Python variables
     * @return an MFDPlot that can be used to add MFDs
     */
    public MFDCell addMFD(String prefix) {
        MFDCell cell = new MFDCell(prefix);
        cell.hideSource();
        notebook.add(cell);
        return cell;
    }
}
