package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * This is an mutable {@link RTWListValue} implementation backed by a simple in-memory ArrayList
 * with no connection to any KB.<p>
 *
 * See {@link RTWImmutableListValue} for an immutable ArrayList-backed RTWListValue.<p>
 *
 * Use the {@link copy} method to transform a potentially immutable RTWListValue into a mutable one.
 * Using one of the constructors for this purpose will not have the intended effect.  The {@link
 * construct} method is also available for wrapping an existing ArrayList<RTWValue> object into an
 * RTWArrayListValue.<p>
 */
public class RTWArrayListValue extends RTWListValue implements RTWValue {
    /**
     * Singleton empty list for anyone to use, comparable to Collections.EMPTY_LIST
     */
    public final static RTWImmutableListValue EMPTY_LIST = new RTWImmutableListValue();

    /**
     * The in-memory list of RTWValues that we represent.<p>
     *
     * This is a List rather than an ArrayList so that our {@link RTWImmutableListValue} subclass
     * can use Collections.unmodifiableList as a quick immutability solution.<p>
     */
    protected List<RTWValue> val;

    /**
     * Copy constructor, basically; the guts of the copy method<p>
     *
     * This is protected in order to avoid accidental confusion with the behavior of the
     * "RTWValue..." constructor when given a single RTWValue as a parameter<p>
     */
    protected RTWArrayListValue(ArrayList<RTWValue> v) {
        super();
        val = v;
    }

    /**
     * Copy all values from the given RTWBag into a new RTWArrayListValue<p>
     *
     * Recall that an {@link RTWLocation} is a kind of {@link RTWBag}.<p>
     *
     * In general, it's not a good idea to use this constructor because the whole point of an RTWBag
     * is that it might contain too many elements to reasonably fit in memory.  This constructor us
     * meant for backward compatability with old code, and for odd circumstances where you can be
     * sure that you won't be facing a scalability problem and, for whatever reason, need a List
     * instead of an RTWBag.<p>
     */
    public RTWArrayListValue(RTWBag bag) {
        super();
        val = new ArrayList<RTWValue>(bag.getNumValues());
        for (RTWValue v : bag.iter())
            val.add(v);
    }

    /**
     * Construct an empty RTWArrayListValue
     */
    public RTWArrayListValue() {
        super();
        val = new ArrayList<RTWValue>();
    }
    
    /**
     * Construct en empty RTWArrayListValue with space allocated for the given number of elements
     */
    public RTWArrayListValue(int size) {
        super();
        val = new ArrayList<RTWValue>(size);
    }

    /**
     * Construct a new list containing the given list of elements<p>
     *
     * Note that if you give this a single list that you will *not* get a copy of that list but
     * rather a new list containing one element that is the list you provided.  Use the copy method
     * instead if you wish to make a copy of another list.<p>
     */
    public RTWArrayListValue(RTWValue... elements) {
        super();
        val = new ArrayList<RTWValue>(elements.length);
        Collections.addAll(val, elements);
    }

    /**
     * Construct a new list by appending the given list of values to the given list<p>
     *
     * If null is passed for the the list, then the result will be an RTWArrayListValue that
     * contains only the values in newElements.<p>
     */
    public static RTWArrayListValue appendAll(Collection<RTWValue> l, Collection<RTWValue> newElements) {
        ArrayList<RTWValue> tmp;
        if (l != null) {
            tmp = new ArrayList<RTWValue>(l.size() + newElements.size());
            tmp.addAll(l);
        } else {
            tmp = new ArrayList<RTWValue>(newElements.size());
        }
        tmp.addAll(newElements);
        return new RTWArrayListValue(tmp);
    }

    /**
     * Construct a new list by appending the given value to the given list<p>
     *
     * If null is passed for the the list, then the result will be an RTWArrayListValue of size
     * 1 that contains only the new element.<p>
     */
    public static RTWArrayListValue append(Collection<RTWValue> l, RTWValue newElement) {
        ArrayList<RTWValue> tmp;
        if (l != null) {
            tmp = new ArrayList<RTWValue>(l.size() + 1);
            tmp.addAll(l);
        } else {
            tmp = new ArrayList<RTWValue>(1);
        }
        tmp.add(newElement);
        return new RTWArrayListValue(tmp);
    }

    /**
     * Return a mutable RTWArrayListValue copy of the given collection of RTWValues.
     */
    public static RTWArrayListValue copy(Collection<RTWValue> l) {
        return new RTWArrayListValue(new ArrayList<RTWValue>(l));
    }

    /**
     * Return the given ArrayList<RTWValue> wrapped as an RTWArrayListValue object.<p>
     *
     * Whereas {@link copy} would make a copy of of whatever you pass it, this uses the given object
     * directly.  The resulting object becomes backed by exactly the ArrayList<RTWValue> given.
     * This may be useful for efficiency in some circumstances, although naturally it is potentially
     * dangerous because the caller will retain direct access to the internals of the returned
     * object.<p>
     */
    public static RTWArrayListValue construct(ArrayList<RTWValue> l) {
        return new RTWArrayListValue(l);
    }

    @Override public int hashCode() {
        return val.hashCode();
    }

    @Override public Object clone() {
        try {
            RTWArrayListValue clone = (RTWArrayListValue)super.clone();
            clone.val = new ArrayList<RTWValue>(val.size());
            for (RTWValue v : this)
                clone.add((RTWValue)v.clone());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }

    }

    @Override public boolean add(RTWValue element) {
        return val.add(element);
    }

    @Override public void add(int index, RTWValue element) {
        val.add(index, element);
    }

    @Override public boolean addAll(Collection<? extends RTWValue> c) {
        return val.addAll(c);
    }

    @Override public boolean addAll(int index, Collection<? extends RTWValue> c) {
        return val.addAll(index, c);
    }

    @Override public void clear() {
        val.clear();
    }

    // bkdb TODO:
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
        try {
            return val.get(index);
        } catch (Exception e) {
            throw new RuntimeException("get(" + index + ") on " + toString(), e);
        }
    }

    @Override public int indexOf(Object o) {
        return val.indexOf(o);
    }

    @Override public boolean isEmpty() {
        return val.isEmpty();
    }

    @Override public Iterator<RTWValue> iterator() {
        return val.iterator();
    }

    @Override public int lastIndexOf(Object o) { 
        return val.lastIndexOf(o); 
    } 
 
    @Override public ListIterator<RTWValue> listIterator() {
        return val.listIterator();
    }

    @Override public ListIterator<RTWValue> listIterator(int index) {
        return val.listIterator(index);
    }

    @Override public RTWValue remove(int index) {
        return val.remove(index);
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
        return val.set(index, element);
    }

    @Override public int size() {
        return val.size();
    }

    @Override public List<RTWValue> subList(int fromIndex, int toIndex) {
        return val.subList(fromIndex, toIndex);
    }

    @Override public Object[] toArray() {
        return val.toArray();
    }

    @Override public <T> T[] toArray(T[] a) {
        return val.toArray(a);
    }
}
