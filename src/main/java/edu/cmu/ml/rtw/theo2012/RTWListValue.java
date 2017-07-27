package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Abstract parent for {@link RTWValue} instances that implement a list of RTWValues (properly an
 * extension of List<RTWValue).
 */
public abstract class RTWListValue extends RTWValueBase implements List<RTWValue>, RTWValue {
    /**
     * As usual, this returns something meant to be friendly to humans and unambiguous to machines<p>
     *
     * The one major benefit and failing of this method is that excessively long lists are
     * truncated.  This is a benefit to humans because excessively long lists are not nice to look
     * at, are not nice to terminal output or logs, and can be mean to heap limits.  The consequent
     * failing is that a truncated list cannot be completely recovered, but one ought better to
     * consider a specialized storage format for excessively long lists.<p>
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean comma = false;

        // 2013-01-09: Extraordinarily long lists can break StringBuilder here, and that can cause a
        // crash during the construction of error messagess, and that is highly obfuscatory.  I'm
        // not sure what a "good" solution here would be, so what we'll do is just cut things off
        // after some large number of elements and hope that doesn't cause dataloss.  toString isn't
        // really the best choice of a way to store an RTWListValue, but it will almost invariably
        // be used for that by somebody at some point.
        int maxelements = 10000;

        for (RTWValue v : this) {
            if (comma)  sb.append(", ");
            else comma = true;
            if (--maxelements <= 0) {
                sb.append("** LIST TRUNCATED (total of " + size() + " elements) **");
                break;
            }
            sb.append(v);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean comma = false;

        // 2013-01-09: Extraordinarily long lists can break StringBuilder here, and that can cause a
        // crash during the construction of error messagess, and that is highly obfuscatory.  I'm
        // not sure what a "good" solution here would be, so what we'll do is just cut things off
        // after some large number of elements and hope that doesn't cause dataloss.  toString isn't
        // really the best choice of a way to store an RTWListValue, but it will almost invariably
        // be used for that by somebody at some point.
        int maxelements = 10000;

        for (RTWValue v : this) {
            if (comma)  sb.append(", ");
            else comma = true;
            if (--maxelements <= 0) {
                sb.append("** LIST TRUNCATED (total of " + size() + " elements) **");
                break;
            }
            sb.append(v.toPrettyString());
        }
        sb.append("}");
        return sb.toString();
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

        final RTWListValue l = (RTWListValue)obj;
        if (size() != l.size()) return false;
        Iterator<RTWValue> us = iterator();
        Iterator<RTWValue> them = l.iterator();
        while (us.hasNext()) {
            if (!us.next().equals(them.next())) return false;
        }
        return true;
    }

    /**
     * Implementations may want to override this to do something more clever (or change this default
     * implementaion later).  Erm, they *must* override it, or else the underlying list will not get
     * cloned.  We can't do the cloning code here abstractly because it is only after this function
     * terminates that the subclass will get a chance to clone the underlying list.
     */
    @Override public Object clone() {
        try {
            RTWListValue clone = (RTWListValue)super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }

    }
}

