package nz.cri.gns.NZSHM22.util;

import java.io.IOException;
import java.nio.file.*;

public class ZipTools {

    /**
     * Extracts a specific file from a ZIP file
     *
     * @param zipFilePath path to the ZIP file
     * @param sourcePath the file path inside the ZIP file
     * @param targetPath the file path that the file is to be copied to.
     * @param copyOptions copy options
     * @throws IOException if anything goes wrong.
     */
    public static void copyFromZipFile(
            Path zipFilePath, String sourcePath, Path targetPath, StandardCopyOption copyOptions)
            throws IOException {
        try (FileSystem fileSystem = FileSystems.newFileSystem(zipFilePath, null)) {
            Path fileToExtract = fileSystem.getPath(sourcePath);
            Files.copy(fileToExtract, targetPath, copyOptions);
        }
    }
}
