package nz.cri.gns.NSHM.opensha.util;

import org.opensha.sha.faultSurface.FaultSection;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A List that enforces the consistency of FaultSections.
 * Operations that modify the list are not optimised.
 * Read operations are equivalent to ArrayList performance.
 */
public class FaultSectionList extends ArrayList<FaultSection> {

    private int maxId = -1;
    private final Set<Integer> ids;
    private  FaultSectionList parents;

    /**
     * Creates a new FaultSectionList.
     */
    public FaultSectionList() {
        this(null);
    }

    /**
     * Creates a new FaultSectionList.
     * @param parents a list of parent sections. If new sections have a parent, it will
     *                be checked whether the parent is in the parents list.
     */
    public FaultSectionList(FaultSectionList parents) {
        ids = new HashSet<>();
        this.parents = parents;
    }

    /**
     * Creates a new FaultSectionList and adds all elements.
     * @param elements the elements to be added.
     * @return the new FaultSectionList
     */
    public static FaultSectionList fromList(List<FaultSection> elements) {
        FaultSectionList result = new FaultSectionList();
        result.addAll(elements);
        return result;
    }

    /**
     * Adds a new parent section.
     * @param parent the parent section
     */
    public void addParent(FaultSection parent){
        if(parents == null){
            parents = new FaultSectionList();
        }
        parents.add(parent);
    }

    protected boolean containsId(int id) {
        return ids.contains(id);
    }

    /**
     * Gets an id that is not used by the sections in this list and that can be used to create any number of new
     * subsections starting at this id.
     * @return an id not yet used in the list
     */
    public int getSafeId() {
        return maxId + 1;
    }

    /**
     * Adds a new FaultSection and validates that its dip is reasonable, and that the section is consistent with
     * previously added sections.
     * @param section the section to be added.
     * @return true
     */
    public boolean add(FaultSection section) {
        if (0 <= section.getParentSectionId()
                && null != parents
                && !parents.containsId(section.getParentSectionId())) {
            System.err.println("Section " + section.getSectionId() + " has unknown parent " + section.getParentSectionId());
        }

        if (ids.contains(section.getSectionId())) {
            throw new IllegalArgumentException("Section " + section.getSectionId() + " \"" + section.getSectionName() + "\" reuses an id.");
        }

        if (section.getAveDip() <= 0) {
            throw new IllegalArgumentException("Section " + section.getSectionId() + " \"" + section.getSectionName() + "\" does not have a dip greater than 0.");
        }

        if (section.getAveDip() > 90) {
            throw new IllegalArgumentException("Section " + section.getSectionId() + " \"" + section.getSectionName() + "\" has a dip greater than 90.");
        }

        ids.add(section.getSectionId());
        maxId = Math.max(maxId, section.getSectionId());
        return super.add(section);
    }

    /**
     * Adds all sections from the sections collection.
     * Sections are validated as if added byt he add method.
     * @param sections a collection of sections.
     * @return true
     */
    public boolean addAll(Collection<? extends FaultSection> sections) {
        for (FaultSection section : sections) {
            add(section);
        }
        return true;
    }

    /**
     * Removes the ith FaultSection.
     * @param i the index of the FaultSection to be removed
     * @return the FaultSection that was removed.
     */
    public FaultSection remove(int i) {
        FaultSection removed = super.remove(i);
        ids.remove(removed.getSectionId());
        return removed;
    }

    /**
     * Removes o if it is in this FaultSectionList.
     * @param o the object to be removed.
     * @return whether an object has been removed from the list.
     */
    public boolean remove(Object o) {
        if (super.remove(o)) {
            ids.remove(((FaultSection) o).getSectionId());
            return true;
        } else {
            return false;
        }
    }

    private boolean batchRemove(Predicate<? super FaultSection> filter) {
        ListIterator<FaultSection> i = listIterator();
        boolean result = false;
        while (i.hasNext()) {
            FaultSection section = i.next();
            if (filter.test(section)) {
                i.remove();
                ids.remove(section.getSectionId());
                result = true;
            }
        }
        return result;
    }

    /**
     * Removes all FaultSections for which the filter returns true.
     * @param filter a predicate on a FaultSection
     * @return whether any element has been removed.
     */
    public boolean removeIf(Predicate<? super FaultSection> filter) {
        return batchRemove(filter);
    }

    /**
     * Removes all FaultSections that are both in this list and in c.
     * @param c the FaultSections to remove
     * @return whether any element has been removed.
     */
    public boolean removeAll(Collection<?> c) {
        return batchRemove(c::contains);
    }

    /**
     * Removes all FaultSections that are not in c.
     * @param c the FaultSections to retain
     * @return whether any element has been removed.
     */
    public boolean retainAll(Collection<?> c) {
        return batchRemove(section -> !c.contains(section));
    }

    public FaultSection set(int index, FaultSection element) {
        throw new RuntimeException("Not implemented");
    }

    public void add(int index, FaultSection element) {
        throw new RuntimeException("Not implemented");
    }

    public boolean addAll(int index, Collection<? extends FaultSection> c) {
        throw new RuntimeException("Not implemented");
    }

    public void replaceAll(UnaryOperator<FaultSection> operator) {
        throw new RuntimeException("Not implemented");
    }

    public void clear() {
        throw new RuntimeException("Not implemented");
    }
}