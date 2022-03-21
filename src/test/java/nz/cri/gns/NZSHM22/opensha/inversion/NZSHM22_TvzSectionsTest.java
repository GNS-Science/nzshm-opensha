package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;
import org.junit.Test;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipFile;

public class NZSHM22_TvzSectionsTest {

    public static FileBackedModule serialiseDeserialise(FileBackedModule module) throws IOException {
        File file = File.createTempFile("archive", ".zip");

        ModuleArchive<FileBackedModule> archive = new ModuleArchive<>();
        archive.addModule(module);
        archive.write(file);

        archive = new ModuleArchive<>(new ZipFile(file), module.getClass());

        return archive.getModule(module.getClass());
    }

    @Test
    public void testSerialisation() throws IOException {
        NZSHM22_TvzSections sections = new NZSHM22_TvzSections();
        sections.sections = Set.of(3, 7, 42);

        NZSHM22_TvzSections actual = (NZSHM22_TvzSections) serialiseDeserialise(sections);

        assertEquals(sections.getSections(), actual.getSections());

    }
}
