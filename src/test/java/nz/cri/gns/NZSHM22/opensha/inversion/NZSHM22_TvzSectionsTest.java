package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.junit.Test;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipFile;

public class NZSHM22_TvzSectionsTest {

    @Test
    public void testSerialisation() throws IOException {
        NZSHM22_TvzSections sections = new NZSHM22_TvzSections();
        sections.sections = Set.of(3, 7, 42);

        NZSHM22_TvzSections actual = (NZSHM22_TvzSections) TestHelpers.serialiseDeserialise(sections);

        assertEquals(sections.getSections(), actual.getSections());

    }
}
