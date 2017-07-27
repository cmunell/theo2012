package edu.cmu.ml.rtw.theo2012;

/**
 * Used in RTWLocation to indicate a subslot attached to an element (namely, the one that this class
 * refers to) rather than to a slot.
 *
 * See RTWLocation's class-level comments for more information.
 *
 * Note that instances of this class are immutable so that RTWLocation instances, which may contain
 * instances of RTWElementRef, can gauarantee their own immutability.
 */
public class RTWElementRef {
    /**
     */
    protected final RTWValue val;

    public RTWElementRef(RTWValue val) {
        super();
        this.val = val;
    }

    public RTWValue getVal() {
        return val;
    }

    @Override public int hashCode() {
        return val.hashCode();
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof RTWElementRef)) return false;
        return val.equals(((RTWElementRef)obj).val);
    }
    
    @Override public String toString() {
        return "=" + val;
    }
}