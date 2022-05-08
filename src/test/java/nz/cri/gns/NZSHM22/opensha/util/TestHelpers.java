package nz.cri.gns.NZSHM22.opensha.util;

import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public class TestHelpers {

    public static FileBackedModule serialiseDeserialise(FileBackedModule module) throws IOException {
        File file = File.createTempFile("archive", ".zip");

        ModuleArchive<FileBackedModule> archive = new ModuleArchive<>();
        archive.addModule(module);
        archive.write(file);

        archive = new ModuleArchive<>(new ZipFile(file), module.getClass());

        return archive.getModule(module.getClass());
    }
}
