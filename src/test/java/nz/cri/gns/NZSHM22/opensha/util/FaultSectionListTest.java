package nz.cri.gns.NZSHM22.opensha.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;

import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.List;

public class FaultSectionListTest {

    final static FaultSection section0 = mockFaultSection(0, 1);
    final static FaultSection section1 = mockFaultSection(1, 1);
    final static FaultSection section2 = mockFaultSection(2, 1);
    final static FaultSection section3 = mockFaultSection(3, 1);
    final static FaultSection section4 = mockFaultSection(4, 1);
    final static FaultSection section5 = mockFaultSection(5, 1);

    final static FaultSection section0Too = mockFaultSection(0, 1);
    final static FaultSection section0dip = mockFaultSection(6, 0);
    final static FaultSection section90dip = mockFaultSection(7, 90);
    final static FaultSection section91dip = mockFaultSection(8, 91);

    private static FaultSection mockFaultSection(int id, double dip) {
        FaultSection result = mock(FaultSection.class);
        when(result.getSectionId()).thenReturn(id);
        when(result.getAveDip()).thenReturn(dip);
        return result;
    }

    @Test
    public void testFromList() {
        FaultSectionList listA = FaultSectionList.fromList(Lists.newArrayList());
        assertEquals(0, listA.size());

        FaultSectionList listB = FaultSectionList.fromList(Lists.newArrayList(section2));
        assertEquals(1, listB.size());
        assertEquals(3, listB.getSafeId());
    }

    @Test
    public void testAdd() {
        FaultSectionList list = new FaultSectionList();
        assertEquals(0, list.size());
        assertEquals(0, list.getSafeId());

        list.add(section3);
        assertEquals("size is updated", 1, list.size());
        assertEquals("safe id is updated", 4, list.getSafeId());

        list.add(section0);
        assertEquals("size is updated", 2, list.size());
        assertEquals("safe id is unchanged", 4, list.getSafeId());

        String msg = null;
        try {
            list.add(section0Too);
        } catch (IllegalArgumentException x) {
            msg = x.getMessage();
        }
        assertEquals("Section 0 \"null\" reuses an id.", msg);

        try {
            list.add(section0dip);
        } catch (IllegalArgumentException x) {
            msg = x.getMessage();
        }
        assertEquals("Section 6 \"null\" does not have a dip greater than 0.", msg);

        list.add(section90dip); // no exception thrown

        try {
            list.add(section91dip);
        } catch (IllegalArgumentException x) {
            msg = x.getMessage();
        }
        assertEquals("Section 8 \"null\" has a dip greater than 90.", msg);
    }

    @Test
    public void testAddAll() {
        FaultSectionList list = new FaultSectionList();
        assertEquals(0, list.size());
        assertEquals(0, list.getSafeId());

        list.addAll(Lists.newArrayList(section3, section2));
        assertEquals("size is updated", 2, list.size());
        assertEquals("safe id is updated", 4, list.getSafeId());

        list.addAll(Lists.newArrayList(section1, section0));
        assertEquals("size is updated", 4, list.size());
        assertEquals("safe id is unchanged", 4, list.getSafeId());

        String msg = null;
        try {
            list.addAll(Lists.newArrayList(section0Too));
        } catch (IllegalArgumentException x) {
            msg = x.getMessage();
        }
        assertEquals("Section 0 \"null\" reuses an id.", msg);

        try {
            list.addAll(Lists.newArrayList(section0dip));
        } catch (IllegalArgumentException x) {
            msg = x.getMessage();
        }
        assertEquals("Section 6 \"null\" does not have a dip greater than 0.", msg);

        list.addAll(Lists.newArrayList(section90dip)); // no exception thrown

        try {
            list.addAll(Lists.newArrayList(section91dip));
        } catch (IllegalArgumentException x) {
            msg = x.getMessage();
        }
        assertEquals("Section 8 \"null\" has a dip greater than 90.", msg);
    }

    @Test
    public void testRemoveByIndex() {
        FaultSectionList list = FaultSectionList.fromList(Lists.newArrayList(section0, section3, section5));
        assertEquals(list, Lists.newArrayList(section0, section3, section5));
        assertEquals(3, list.size());
        assertEquals(6, list.getSafeId());

        assertEquals(section5, list.remove(2));

        assertEquals(list, Lists.newArrayList(section0, section3));
        assertEquals(2, list.size());
        assertEquals("list of ids is unchanged", 6, list.getSafeId());

        assertEquals(section0, list.remove(0));
        assertEquals(list, Lists.newArrayList(section3));
        assertEquals(1, list.size());

        assertEquals(section3, list.remove(0));
        assertEquals(list, Lists.newArrayList());
        assertEquals(0, list.size());
    }

    @Test
    public void testRemoveByReference() {
        FaultSectionList list = FaultSectionList.fromList(Lists.newArrayList(section0, section3, section5));
        assertEquals(list, Lists.newArrayList(section0, section3, section5));
        assertEquals(3, list.size());
        assertEquals(6, list.getSafeId());

        assertTrue(list.remove(section5));
        assertEquals(list, Lists.newArrayList(section0, section3));
        assertEquals(2, list.size());
        assertEquals("list of ids is unchanged", 6, list.getSafeId());

        assertFalse("can't remove element that's not in the list", list.remove(section2));
        assertEquals(list, Lists.newArrayList(section0, section3));


        assertTrue(list.remove(section0));
        assertEquals(list, Lists.newArrayList(section3));
        assertEquals(1, list.size());

        assertTrue(list.remove(section3));
        assertEquals(list, Lists.newArrayList());
        assertEquals(0, list.size());
    }

    @Test
    public void testRemoveIf() {

        FaultSectionList emptyList = new FaultSectionList();
        assertFalse(emptyList.removeIf(section -> true));
        assertEquals(Lists.newArrayList(), emptyList);

        List<FaultSection> sourceList = Lists.newArrayList(section0, section4, section1, section3, section2, section5);
        FaultSectionList unorderedList = FaultSectionList.fromList(sourceList);
        assertFalse("nothing removed",unorderedList.removeIf(section -> section.getSectionId() > 10));
        assertEquals(sourceList, unorderedList);
        assertTrue("remove elements", unorderedList.removeIf(section -> section.getSectionId() < 3));
        assertEquals(Lists.newArrayList(section4, section3, section5), unorderedList);
        assertEquals(3, unorderedList.size());
    }

}
