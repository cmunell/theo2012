package edu.cmu.ml.rtw.theo2012.core;

/**
 * {@link RTWValue} implementation that contains an immutable Integer
 */
public class RTWIntegerValue extends RTWValueBase implements RTWValue {
    private final int val;

    /**
     * Constructor
     */
    public RTWIntegerValue(int val) {
        super();
        this.val = val;
    }

    /**
     * This output is human-friendly, but also machine-friendly in that is distinguishable vs. an
     * RTWStringValue or RTWDoubleValue representing the same integer.
     */
    @Override public String toString() {
        return "%" + String.valueOf(val);
    }

    /**
     * Human-friendly rendering of this integer
     */
    @Override public String asString() {
        // bkisiel 2012-05-29: switching to toString for a substantial speedup.  It appears that
        // everything should come out the same, but I'm making a note of this because any
        // differences will cause trouble for StringListStore.getNamePartitionHash.  return
        //
        // String.format("%d", val);
        return Integer.toString(val);
    }

    /**
     * Convert to a primitive int value
     */
    @Override public int asInteger() {
        return val;
    }

    /**
     * Convert to a primitive double value
     */
    @Override public double asDouble() {
        return (double)val;
    }

    /**
     * Convert to a primitive boolean value<p>
     *
     * 0 is taken to be false.  1 is taken to be true.  All other values result in an exception.<p>
     */
    @Override public boolean asBoolean() {
        if (val == 0) return false;
        if (val == 1) return true;
        throw new RuntimeException("Only integers 0 and 1 may be converted to boolean values, not " + toString());
    }

    @Override public String toPrettyString() {
        return Integer.toString(val);
    }
    
    /**
     * Helper for Matlab, which cannot cast Java objects
     */
    public static RTWIntegerValue castFrom(RTWValue v) {
        return (RTWIntegerValue)v;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + val;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof RTWIntegerValue) return ((RTWIntegerValue)obj).asInteger() == val;
        if (obj instanceof Integer) return ((Integer)obj).equals(val);
        return false;
    }

    @Override
    public Object clone() {
        try {
            return (RTWIntegerValue)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }
    }
}
