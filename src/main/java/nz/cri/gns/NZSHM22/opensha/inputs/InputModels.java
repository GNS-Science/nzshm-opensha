package nz.cri.gns.NZSHM22.opensha.inputs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Reads input model files (fault models, deformation models, ...) from
 * https://github.com/voj/nzshm-input-models at a given commit. Files are downloaded once per run
 * into {@link #CACHE_DIR}.
 */
public class InputModels {

    static final String BASE_URL = "https://raw.githubusercontent.com/voj/nzshm-input-models/";

    /** Only full commit hashes are accepted: branches and tags can move. */
    static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    /** Temporary folder holding downloaded files, as {@code <commit>/<path>}. */
    public static final Path CACHE_DIR;

    static {
        try {
            CACHE_DIR = Files.createTempDirectory("nzshm-input-models");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private InputModels() {}

    /**
     * Opens an input model file, downloading it if necessary.
     *
     * @param commit full nzshm-input-models commit hash to read the file at
     * @param path path below the repo's models directory, e.g. "faultModels/cfm_1_0A_all.xml"
     * @return the file's content, or null if the file does not exist at that commit
     */
    public static InputStream getStream(String commit, String path) {
        if (commit == null || !COMMIT.matcher(commit).matches()) {
            throw new IllegalArgumentException(
                    "Expected a full nzshm-input-models commit hash, got: " + commit);
        }
        try {
            Path file = CACHE_DIR.resolve(commit).resolve(path);
            if (!Files.isRegularFile(file) && !download(commit, path, file)) {
                return null;
            }
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Downloads the file to target. Returns false if it does not exist on GitHub. */
    static boolean download(String commit, String path, Path target) throws IOException {
        String url = BASE_URL + commit + "/models/" + path;
        Files.createDirectories(target.getParent());
        // Download to a temp file and move it into place, so that another thread never reads a
        // partially downloaded file.
        Path tmp = Files.createTempFile(target.getParent(), null, ".part");
        try {
            HttpResponse<Path> response =
                    CLIENT.send(
                            HttpRequest.newBuilder(URI.create(url)).build(),
                            HttpResponse.BodyHandlers.ofFile(tmp));
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + url, e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
