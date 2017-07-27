package edu.cmu.ml.rtw.theo2012;

/**
 * Base type for all values storable in a Theo slot<p>
 *
 * Most RTWValue implementations are immutable, like with Java's native String object.  That
 * assumption makes life easier most of the time.  The main inconvenience comes from {@link
 * RTWListValue}, in which case it's nice to be able to build or otherwise adjust a list piecemeal.
 * This makes {@link RTWArrayListValue} a conspicuous example of a mutable RTWValue implementation.
 * This is usually not a significant concern to application level code, as Theo implementations take
 * care to maintain only immutable KB content, meaning that even in an application that opens a KB
 * in read-write mode, modifying values read from the KB does not modify the KB.  It is sufficient
 * to never assume that an arbitrary RTWValue is mutable.<p>
 *
 * RTWValue features methods like asString, asDouble, asList etc. that will attempt to convert the
 * value into the requested type.  These are meant to be used when you know or expect that the value
 * is the given type, and will throw an exception when it is not convetable.  They are really only
 * meant as ways to recover a normal Java object from an RTWValue of known type.  (Type inspection
 * is limited to instanceof at present, although this may change in the future.  So far it seems in
 * typical use the type inspection and conversion methods of {@link Querry}, which represents the
 * set of values in a given KB slot.)<p>
 *
 * Note that asString and toString are not the same, and the value returned by toString is likely to
 * be different from asString, even when the value is itself a String.  toString is geared toward
 * human-friendly use in logging and error messages, and is guaranteed to succeed.<p>
 *
 * {@link RTWValueBase} is provided as an abstract base class for RTWValue implementations.
 */
public interface RTWValue extends Cloneable, Comparable<RTWValue> {

    @Override public int hashCode();

    /**
     * The usual equality predicate<p>
     * 
     * This does not do autoconversions so as to say that the RTWIntegerValue 1 is equal to the
     * RTWStringValue "1".  But a subclass of RTWIntegerValue that also represents 1 should come
     * back as being equal to an RTWIntegerValue or other RTWIntegerValue subclass that represents
     * 1.<p>
     *
     * This will, however, treat primitive java objects as being equal to a corresponding RTWValue
     * objects.  So, for instance the integer 1 will be equal to the RTWIntegerValue 1, and the
     * string "1" will be equal to the RTWStringValue "1".<p>
     */
    @Override public boolean equals(Object obj);

    // These as* methods also do not (should not) do autoconversions like the equals method, but
    // asString does some conversions to maintain backward compatability.  Don't write new code that
    // uses asString to be type-agnostic.  Try to use RTWLocation's scalar-returning and type
    // inspection methods instead.  We may need to add type inspection methods to RTWValue as well,
    // since that would be generally preferable to folks using instanceof.
    //
    // bk:api: probably elaborate the above
    //
    // Also note that invoking as* with the wrong type will in general result in an exception,
    // although there are a few cases where a conversion could take place, like invoking asDouble on
    // an RTWIntegerValue.

    /**
     * Return Java String if this RTWValue is a String, or throw an exception.
     */
    public String asString();

    /**
     * Return Java int if this RTWValue is an integer, or throw an exception.
     */
    public int asInteger();

    /**
     * Return Java double if this RTWValue is an integer or double, or throw an exception.
     */
    public double asDouble();

    /**
     * Return Java boolean if this RTWValue is a boolean, or throw an exception.
     */
    public boolean asBoolean();

    // bkdb: I'm holding of on asList only because I want to see if we can get away with only asSet down the road.  // bk:sets

    /**
     * Return a potentially ambiguous but human-friendly string representation of this RTWValue
     *
     * The usual toString implementation renders an RTWValue in a format that is reasonably
     * human-friendly, but using a particular notation that keeps it machine-friendly by
     * unambiguously indicating its type.  So, for instance a an RTWStringValue will always be in
     * double-quotes, but no other type of RTWValue will be.  In other cases, the notation may be
     * more confusing, for instance that an RTWIntegerValue will always be prefixed by a percent
     * sign.
     *
     * This method returns a maximally human-friendly string instead.  So, for instance, the
     * RTWStringValue "true", an RTWBooleanValue of true, and a PrimitiveEntity named "true" will
     * all simply be rendered as the String "true".
     */
    public String toPrettyString();

    public Object clone() throws CloneNotSupportedException;
}
