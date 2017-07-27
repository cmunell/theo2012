package edu.cmu.ml.rtw.theo2012;

public class RTWDoubleValue extends RTWValueBase implements RTWValue {
    private final double val;

    /**
     * Constructor
     */
    public RTWDoubleValue(double val) {
        super();
        this.val = val;
    }

    /**
     * Helper for Matlab, which cannot cast Java objects
     */
    public static RTWDoubleValue castFrom(RTWValue v) {
        return (RTWDoubleValue)v;
    }

    /**
     * This output is human-friendly, but also machine-friendly in that is distinguishable vs. an
     * RTWStringValue or RTWIntegerValue representing the same double.
     */
    @Override public String toString() {
        return "#" + String.valueOf(val);
    }

    /**
     * Human-friendly rendering of this double
     */
    @Override public String asString() {
        return String.format("%f", val);
    }

    /**
     * Convert to a primitive double value
     */
    @Override public double asDouble() {
        return val;
    }

    @Override public String toPrettyString() {
        return Double.toString(val);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(val);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    /**
     * This uses the semantics of java.lang.Double, which differ from double but allow use in hash
     * tables.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof RTWDoubleValue) return Double.compare(((RTWDoubleValue)obj).asDouble(), val) == 0;
        if (obj instanceof Double) return Double.compare((Double)obj, val) == 0;
        return false;
    }

    @Override
    public Object clone() {
        try {
            return (RTWDoubleValue)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }
    }
}
