package edu.cmu.ml.rtw.theo2012.core;

/**
 * {@link RTWValue} implementation that contains an immutable String
 */
public class RTWStringValue extends RTWValueBase implements CharSequence, RTWValue {
    private final String val;

    /**
     * String
     */
    public RTWStringValue(String val) {
        super();
        if (val == null)
            throw new RuntimeException("Cannot construct RTWStringValue from null");
        this.val = val;
    }

    /**
     * This will return a quoted and escaped version of the string.<p>
     *
     * The level of quoting is meant to be useful for logging and error messages.  As such, \n, \r,
     * and " will be the only escaped things as of this writing, so that things get kept on one
     * line.<p>
     *
     * Notably, the current jurry-rigged system for getting a KB into our web-based KB browser
     * relies on tabs not being escaped in order for common things like tab-containing provenance to
     * show up right.  Making that assumption here is obviously not a very good solution, but it's
     * the easy one until such time as we want to stop and put in the time to start overhauling that
     * relic.<p>
     *
     * As a result of all of this, such a rendition can be easily machine-interpreted back into
     * exactly the same RTWStringValue object, while also being largely human-friendly.
     * Additionally, because it is always quoted with double-quotes, it is easily differentiated
     * from all other current kinds of RTWValues, none of which will ever begin with a double quote
     * character, meaning that it "30" the string is differentiable from 30 the integer, and that
     * "true" the string is differentiable from the boolean value of the same name.<p>
     */
    @Override public String toString() {
        if (val == null)
            throw new RuntimeException("Internal Error: Should not have an RTWStringValue that is null");
        final StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            if (c == '\\' || c =='"') {
                sb.append('\\');
                sb.append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if ( c== '\r') {
                sb.append("\\r");
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Convert to a native String object<p>
     *
     * Note that this performs no molestation as {@link toString} does.<p>
     */
    @Override public String asString() {
        return val;
    }

    @Override public String toPrettyString() {
        return val;
    }

    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((val == null) ? 0 : val.hashCode());
        return result;
    }

    /**
     * Note especially that this equals works correctly against the Java String class
     */
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof RTWStringValue) return val.equals(((RTWStringValue)obj).val);
        if (obj instanceof String) return val.equals((String)obj);
        return false;
    }

    @Override public Object clone() {
        try {
            return (RTWStringValue)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }
    }

    @Override public char charAt(int index) {
        return val.charAt(index);
    }

    @Override public int length() {
        return val.length();
    }
    
    @Override public CharSequence subSequence(int start, int end) {
        return val.subSequence(start, end);
    }
}
