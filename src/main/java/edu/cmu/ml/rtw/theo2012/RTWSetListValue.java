package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * A mutable RTWListValue implementation that secretly uses a HashSet internally to store the
 * values<p>
 *
 * This is a bit of Q&D skullduggery mainly meant to allow the in-memory-only use of sets to store
 * slot values in {@link StringListStoreMap} implementations.  FODO: rewrite/refactor
 * StringListStoreMap to use something more general than RTWListValue.<p>
 *
 * This will throw exceptions any time it is treated as something other than a set, making it
 * entirely unsuitable for any kind of general use.  As an especial violation of listness, get(0)
 * will work when and only when there is exactly one value in the set; this works as a fast way to
 * get that value without having to go through an Iterator object or something like that.<p>
 *
 * Use the {@link copy} methods to transform a potentially immutable RTWListValue into a mutable
 * one.  Using one of the constructors for this purpose will not have the intended effect.<p>
 */
public class RTWSetListValue extends RTWListValue implements RTWValue {
    /**
     * The in-memory set of RTWValues that we represent.<p>
     *
     * This is a Set rather than a HashSet so that our {@link RTWImmutableListValue} subclass can
     * use Collections.unmodifiableSet as a quick immutability solution.<p>
     */
    protected Set<RTWValue> val;

    /**
     * Copy constructor, basically; the guts of the copy method<p>
     *
     * This is protected in order to avoid accidental confusion with the behavior of the
     * "RTWValue..." constructor when given a single RTWValue as a parameter<p>
     */
    protected RTWSetListValue(Set<RTWValue> v) {
        super();
        val = v;
    }

    /**
     * Construct an empty RTWSetListValue
     */
    public RTWSetListValue() {
        super();
        val = new HashSet<RTWValue>();
    }
    
    /**
     * Construct en empty RTWSetListValue with space allocated for the given number of elements
     */
    public RTWSetListValue(int size) {
        super();
        val = new HashSet<RTWValue>(size);
    }

    /**
     * Construct a new list containing the given list of elements<p>
     *
     * Note that if you give this a single list that you will *not* get a copy of that list but
     * rather a new list containing one element that is the list you provided.  Use the copy method
     * instead if you wish to make a copy of another list.<p>
     */
    public RTWSetListValue(RTWValue... elements) {
        super();
        val = new HashSet<RTWValue>(elements.length);
        Collections.addAll(val, elements);
    }

    /**
     * TODO: we need this as a transitional step because, with the advent of TCHStore, we are now in  // bk:sets
     * a world where all slots are collections, and we need existing code that uses asString to get
     * nrOfValues=1 values (e.g. predicate metadata) to continue working until it gets renovated,
     * and I susepct that it would be deleterious to performance to go the route of having getValue
     * have to check nrOfValues and autoconvert.
     */
    @Override public String asString() {
        if (true)
            throw new RuntimeException("I'm hoping we don't need this implementation anymore");
        if (size() != 1)
            throw new RuntimeException("asString may only be used on lists of size 1; this list is " + toString());
        return get(0).asString();
    }

    /**
     * Subclasses are encouraged to override this method if they have more efficient ways to do
     * this.
     */
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || !(obj instanceof RTWListValue))
            return false;
        if (obj instanceof RTWSetListValue)
            return val.equals(((RTWSetListValue)obj).val);

        throw new RuntimeException("Under what conditions do we need equality?");  // FODO
    }

    /**
     * Construct a new list by appending the given value to the given list<p>
     *
     * If null is passed for the the list, then the result will be an RTWSetListValue of size
     * 1 that contains only the new element.<p>
     */
    public static RTWSetListValue append(Collection<RTWValue> l, RTWValue newElement) {
        HashSet<RTWValue> tmp;
        if (l != null) {
            tmp = new HashSet<RTWValue>(l.size() + 1);
            tmp.addAll(l);
        } else {
            tmp = new HashSet<RTWValue>(1);
        }
        tmp.add(newElement);
        return new RTWSetListValue(tmp);
    }

    /**
     * Return a mutable RTWSetListValue copy of the given collection of RTWValues.
     */
    public static RTWSetListValue copy(Collection<RTWValue> l) {
        return new RTWSetListValue(new HashSet<RTWValue>(l));
    }

    /**
     * Return a mutable RTWSetListValue copy of the given set of RTWValues.
     */
    public static RTWSetListValue copy(Set<RTWValue> l) {
        return new RTWSetListValue(l);
    }

    @Override public Object clone() {
        throw new RuntimeException("Do we really need this?"); // FODO
    }

    @Override public int hashCode() {
        return val.hashCode();
    }

    @Override public boolean add(RTWValue element) {
        return val.add(element);
    }

    @Override public void add(int index, RTWValue element) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public boolean addAll(Collection<? extends RTWValue> c) {
        return val.addAll(c);
    }

    @Override public boolean addAll(int index, Collection<? extends RTWValue> c) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public void clear() {
        val.clear();
    }

    // TODO:
    //
    // Do we need / want to support autoconversion of, say, a String to an RTWStringValue, an int to
    // an RTWIntegerValue etc.?  I'm going to say no unless we accumulate votes to the contrary
    // based on places where not having this capability overcomplicates calling code.
    //
    // We'd have to support add(Object), and indexOf(Object), and suchlike, we'd want to be able to
    // create an RTWValue from and Object and everything else, all fo which we've so far had
    // commented out and marked for deletion anyway.
    //
    @Override public boolean contains(Object o) {
        return val.contains(o);
    }

    @Override public boolean containsAll(Collection<?> c) {
        return val.containsAll(c);
    }

    @Override public RTWValue get(int index) {
        // This is a dirty hack as explained in the class-level comments
        if (index == 0) return val.iterator().next();
        throw new RuntimeException("This is not actually a list");
    }

    @Override public int indexOf(Object o) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public boolean isEmpty() {
        return val.isEmpty();
    }

    @Override public Iterator<RTWValue> iterator() {
        return val.iterator();
    }

    @Override public int lastIndexOf(Object o) { 
        throw new RuntimeException("This is not actually a list");
    } 
 
    @Override public ListIterator<RTWValue> listIterator() {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public ListIterator<RTWValue> listIterator(int index) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public RTWValue remove(int index) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public boolean remove(Object o) {
        return val.remove(o);
    }

    @Override public boolean removeAll(Collection<?> c) {
        return val.removeAll(c);
    }

    @Override public boolean retainAll(Collection<?> c) {
        return val.retainAll(c);
    }

    @Override public RTWValue set(int index, RTWValue element) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public int size() {
        return val.size();
    }

    @Override public List<RTWValue> subList(int fromIndex, int toIndex) {
        throw new RuntimeException("This is not actually a list");
    }

    @Override public Object[] toArray() {
        return val.toArray();
    }

    @Override public <T> T[] toArray(T[] a) {
        return val.toArray(a);
    }
}
