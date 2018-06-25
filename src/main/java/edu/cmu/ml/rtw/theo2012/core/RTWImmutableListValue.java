package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Immutable version of {@link RTWArrayListValue}.<p>
 *
 * NOTE: True (deep) immutability is only gauranteed when this object contains only immutable
 * RTWValues.  Caveat scriptor.<p>
 *
 * Use the {@link copy} methods to transform a potentially immutable RTWListValue into a mutable
 * one.  Using one of the constructors for this purpose will not have the intended effect.<p>
 */
public class RTWImmutableListValue extends RTWArrayListValue implements RTWValue {
    /**
     * Copy constructor, basically; the guts of the copy method<p>
     *
     * This is protected in order to avoid accidental confusion with the behavior of the
     * "RTWValue..." constructor when given a single RTWValue as a parameter<p>
     */
    protected RTWImmutableListValue(List<RTWValue> v) {
        super();
        val = Collections.unmodifiableList(v);
    }

    /**
     * Copy all values from the given RTWBag into a new RTWImmutableListValue<p>
     *
     * Recall that an {@link RTWLocation} is a kind of {@link RTWBag}.<p>
     *
     * In general, it's not a good idea to use this constructor because the whole point of an RTWBag
     * is that it might contain too many elements to reasonably fit in memory.  This constructor us
     * meant for backward compatability with old code, and for odd circumstances where you can be
     * sure that you won't be facing a scalability problem and, for whatever reason, need a List
     * instead of an RTWBag.<p>
     */
    public RTWImmutableListValue(RTWBag bag) {
        super();
        ArrayList<RTWValue> tmp = new ArrayList<RTWValue>(bag.getNumValues());
        for (RTWValue v : bag.iter())
            tmp.add(v);
        val = Collections.unmodifiableList(tmp);
    }

    /**
     * Construct a new list containing the given list of elements<p>
     *
     * Note that if you give this a single list that you will *not* get a copy of that list but
     * rather a new list containing one element that is the list you provided.  Use the copy method
     * instead if you wish to make a copy of another list.<p>
     */
    public RTWImmutableListValue(RTWValue... elements) {
        super();
        ArrayList<RTWValue> tmp = new ArrayList<RTWValue>(elements.length);
        Collections.addAll(tmp, elements);
        val = Collections.unmodifiableList(tmp);
    }

    /**
     * Construct a new list by appending the given value to the given list<p>
     *
     * If null is passed for the the list, then the result will be an RTWImmutableListValue of size
     * 1 that contains only the new element.<p>
     */
    public static RTWImmutableListValue append(Collection<RTWValue> l, RTWValue newElement) {
        ArrayList<RTWValue> tmp;
        if (l != null) {
            tmp = new ArrayList<RTWValue>(l.size() + 1);
            tmp.addAll(l);
        } else {
            tmp = new ArrayList<RTWValue>(1);
        }
        tmp.add(newElement);
        return new RTWImmutableListValue(tmp);
    }

    /**
     * Return a mutable RTWArrayListValue copy of the given collection of RTWValues.
     */
    public static RTWImmutableListValue copy(Collection<RTWValue> c) {
        ArrayList<RTWValue> l = new ArrayList<RTWValue>(c);
        return copy(l);
    }

    /**
     * Return a mutable RTWArrayListValue copy of the given list of RTWValues.
     */
    public static RTWImmutableListValue copy(List<RTWValue> l) {
        return new RTWImmutableListValue(Collections.unmodifiableList(l));
    }

    @Override public Object clone() {
        // Our parent, RTWArrayListValue, will have cloned all of the elements for us already.
        RTWImmutableListValue clone = (RTWImmutableListValue)super.clone();
        clone.val = Collections.unmodifiableList(clone.val);
        return clone;
    }
}
