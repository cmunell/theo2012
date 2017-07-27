package edu.cmu.ml.rtw.theo2012;

/**
 * {@link RTWValue} implementation that contains an immutable Boolean<p>
 *
 * For speed and space, it is suggested to use the singleton TRUE and FALSE members.<p>
 */
public class RTWBooleanValue extends RTWValueBase implements RTWValue {
    /**
     * Singleton instance for true
     */
    public final static RTWBooleanValue TRUE = new RTWBooleanValue(true);

    /**
     * Singleton instance for false
     */
    public final static RTWBooleanValue FALSE = new RTWBooleanValue(false);

    private final boolean val;

    /**
     * Constructor
     */
    public RTWBooleanValue(boolean val) {
        super();
        this.val = val;
    }

    /**
     * Helper for Matlab, which cannot cast Java objects
     */
    public static RTWBooleanValue castFrom(RTWValue v) {
        return (RTWBooleanValue)v;
    }

    /**
     * This output is human-friendly, but also machine-friendly in that is distinguishable vs. an
     * RTWStringValue where the string is "true" or "false".
     */
    @Override public String toString() {
        if (val) return "$true";
        else return "$false";
    }

    @Override public String toPrettyString() {
        if (val) return "true";
        else return "false";
    }

    /**
     * Convert to a primitive boolean value
     */
    @Override public boolean asBoolean() {
        return val;
    }
    
    @Override
    public int hashCode() {
        if (val) return 2;
        else return 3;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof RTWBooleanValue) return ((RTWBooleanValue)obj).asBoolean() == val;
        if (obj instanceof Boolean) return ((Boolean)obj) == val;
        return false;
    }

    @Override
    public Object clone() {
        try {
            return (RTWBooleanValue)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }
    }
}
