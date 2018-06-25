package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * This is to what {@link RTWSetListValue} what {@link RTWImmutableListValue} is to {@link
 * RTWArrayListValue}.
 *
 * NOTE: True (deep) immutability is only gauranteed when this object contains only immutable
 * RTWValues.  Caveat scriptor.<p>
 *
 * Use the {@link copy} methods to transform a potentially immutable RTWListValue into a mutable
 * one.  Using one of the constructors for this purpose will not have the intended effect.<p>
 */
public class RTWImmutableSetListValue extends RTWSetListValue implements RTWValue {
    /**
     * Copy constructor, basically; the guts of the copy method<p>
     *
     * This is protected in order to avoid accidental confusion with the behavior of the
     * "RTWValue..." constructor when given a single RTWValue as a parameter<p>
     */
    protected RTWImmutableSetListValue(Set<RTWValue> v) {
        super();
        val = Collections.unmodifiableSet(v);
    }

    /**
     * Copy all values from the given RTWBag into a new RTWImmutableSetListValue<p>
     *
     * Recall that an {@link RTWLocation} is a kind of {@link RTWBag}.<p>
     *
     * In general, it's not a good idea to use this constructor because the whole point of an RTWBag
     * is that it might contain too many elements to reasonably fit in memory.  This constructor us
     * meant for backward compatability with old code, and for odd circumstances where you can be
     * sure that you won't be facing a scalability problem and, for whatever reason, need a List
     * instead of an RTWBag.<p>
     */
    public RTWImmutableSetListValue(RTWBag bag) {
        super();
        HashSet<RTWValue> tmp = new HashSet<RTWValue>(bag.getNumValues());
        for (RTWValue v : bag.iter())
            tmp.add(v);
        val = Collections.unmodifiableSet(tmp);
    }

    /**
     * Construct a new list containing the given list of elements<p>
     *
     * Note that if you give this a single list that you will *not* get a copy of that list but
     * rather a new list containing one element that is the list you provided.  Use the copy method
     * instead if you wish to make a copy of another list.<p>
     */
    public RTWImmutableSetListValue(RTWValue... elements) {
        super();
        HashSet<RTWValue> tmp = new HashSet<RTWValue>(elements.length);
        Collections.addAll(tmp, elements);
        val = Collections.unmodifiableSet(tmp);
    }

    /**
     * Construct a new list by appending the given value to the given list<p>
     *
     * If null is passed for the the list, then the result will be an RTWImmutableSetListValue of size
     * 1 that contains only the new element.<p>
     */
    public static RTWImmutableSetListValue append(Collection<RTWValue> l, RTWValue newElement) {
        HashSet<RTWValue> tmp;
        if (l != null) {
            tmp = new HashSet<RTWValue>(l.size() + 1);
            tmp.addAll(l);
        } else {
            tmp = new HashSet<RTWValue>(1);
        }
        tmp.add(newElement);
        return new RTWImmutableSetListValue(tmp);
    }

    /**
     * Return a mutable RTWSetListValue copy of the given collection of RTWValues.
     */
    public static RTWImmutableSetListValue copy(Collection<RTWValue> c) {
        HashSet<RTWValue> l = new HashSet<RTWValue>(c);
        return copy(l);
    }

    /**
     * Return a mutable RTWSetListValue copy of the given  list of RTWValues.
     */
    public static RTWImmutableSetListValue copy(Set<RTWValue> s) {
        return new RTWImmutableSetListValue(Collections.unmodifiableSet(s));
    }

    @Override public Object clone() {
        throw new RuntimeException("Do we really need this?"); // FODO
    }
}
