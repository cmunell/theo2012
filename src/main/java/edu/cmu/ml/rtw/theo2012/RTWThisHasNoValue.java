package edu.cmu.ml.rtw.theo2012;

/**
 * Used as the value for an assertion that the given query is known to have no value<p>
 *
 * This is analogous to the "NO_THEO_VALUE" string that was used in earlier incarnations of Theo for
 * the same purpose, with the upgrade that it is differentiable from values that are actually
 * strings.<p>
 *
 * Note that, because this class does not have any variable content to wrap, it is recommended to
 * use a singleton NONE member to conserve time and space.<p>
 *
 * While this has not yet found a use in NELL as of Fall 2012, we expect to begin using it in newer
 * code based on Theo2012.<p>
 */
public class RTWThisHasNoValue extends RTWValueBase implements RTWValue {
    /**
     * Singleton instance
     */
    public final static RTWThisHasNoValue NONE = new RTWThisHasNoValue();

    /**
     * Constructor
     */
    public RTWThisHasNoValue() {
        super();
    }

    /**
     * In keeping with other RTWValue toString() methods, this returns something both friendly to
     * humans and unambiguous to machines.
     */
    @Override public String toString() {
        return "$novalue";
    }

    /**
     * For lack of a better alternative, this is a synonym for {@link toString}.
     */
    @Override public String asString() {
        return toString();
    }

    @Override public String toPrettyString() {
        return "NO_THEO_VALUE";
    }

    public int hashCode() {
        return 7;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        return (obj instanceof RTWThisHasNoValue);
    }

    @Override public Object clone() {
        try {
            return (RTWThisHasNoValue)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }
    }
}
