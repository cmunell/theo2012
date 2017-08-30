package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    /**
     * Convert an RTWValue to 2011-style UTF-8 plaintext
     *
     * Inverse of {@link fromUTF8}.
     *
     * This is meant to be a fast and machine friendly way to marshal an RTWValue into a byte[].
     * For machine-friendliness, the UTF-8 returned is gauaranteed to contain no newlines, tabs, or
     * nulls.  Spaces, backslashes, quotes, control codes, and super-ASCII characters or codepoints
     * or whatever you'd call them in this context may be present.
     *
     * It would be really nice if we didn't have to build a whole new byte[] for most of these just
     * to prepend the datatype indicator character, but I don't see that happening without quite a
     * bit of work.  Perhaps we would do better with a non-human-friendly format if that extra
     * margin of speed becomes attractive.
     */
    default public byte[] toUTF8() {
        try {
            final RTWValue v = this;
            if (v instanceof RTWStringValue) {

                // I'm not sure what the fastest way to do this is.  We can tweak this if it ever
                // shows up as a performance issue.  We might do something along the lines of what
                // is presented at
                // http://www.javacodegeeks.com/2010/11/java-best-practices-char-to-byte-and.html,
                // but I certainly don't want to write UTF-8 conversion routines at the moment
                // because it turns out to be a bit time-consuming.
                //
                // This first attempt optimizes heavily in favor of not having to escape anything.
                // We escape things with an 0x01 character, which ought to be a rare occurrence on
                // its own.  Probably could do a lot better by using charAt and assembling the
                // result piecemeal as needed.
                //
                String str = v.asString();
                int pos = 0;
                while (true) {
                    pos = str.indexOf(1, pos);
                    if (pos < 0) break;
                    str = str.substring(0, pos) + "\u0001\u0001" + str.substring(pos+1);
                    pos = pos + 2;
                }
                pos = 0;
                while (true) {
                    pos = str.indexOf(0, pos);
                    if (pos < 0) break;
                    str = str.substring(0, pos) + "\u0001\u0002" + str.substring(pos+1);
                    pos = pos + 2;
                }
                pos = 0;
                while (true) {
                    pos = str.indexOf('\t', pos);
                    if (pos < 0) break;
                    str = str.substring(0, pos) + "\u0001\u0003" + str.substring(pos+1);
                    pos = pos + 2;
                }
                pos = 0;
                while (true) {
                    pos = str.indexOf('\n', pos);
                    if (pos < 0) break;
                    str = str.substring(0, pos) + "\u0001\u0004" + str.substring(pos+1);
                    pos = pos + 2;
                }

                byte[] bytes = v.asString().getBytes("UTF-8");
                byte[] dst = new byte[bytes.length + 1];
                dst[0] = 's';
                System.arraycopy(bytes, 0, dst, 1, bytes.length);
                return dst;
            }
            else if (v instanceof RTWListValue) {
                final RTWListValue lv = (RTWListValue)v;

                // Pairs: bytes followed by their length rendered as the ASCII text of a decimal
                // number.  This could be expensive in memory for very long lists, but probably
                // not worth trying to do better because of all the other impact of such a long
                // list.
                final byte[][] elementList = new byte[lv.size()*2][];
                int totalLength = 0;
                Iterator<RTWValue> it = lv.iterator();
                for (int i = 0; i < lv.size(); i++) {
                    elementList[i*2+1] = it.next().toUTF8();
                    elementList[i*2] = Integer.toString(elementList[i*2+1].length).getBytes();
                    totalLength = totalLength + elementList[i*2].length + elementList[i*2+1].length;
                }
                    
                final byte[] dst = new byte[totalLength + 1];
                dst[0] = 'l';
                int dstOffset = 1;
                for (int i = 0; i < elementList.length; i++) {
                    final int l = elementList[i].length;
                    System.arraycopy(elementList[i], 0, dst, dstOffset, l);
                    dstOffset += l;
                }
                return dst;
            }
            else if (v instanceof RTWDoubleValue) {
                // Could be faster, but rendering doubles is also tricky.  More speed here and
                // parsing might better come from storing them binarily.
                byte[] bytes = Double.toString(v.asDouble()).getBytes();
                byte[] dst = new byte[bytes.length + 1];
                dst[0] = 'd';
                System.arraycopy(bytes, 0, dst, 1, bytes.length);
                return dst;
            }
            else if (v instanceof RTWIntegerValue) {
                byte[] bytes = Integer.toString(v.asInteger()).getBytes();
                byte[] dst = new byte[bytes.length + 1];
                dst[0] = 'i';
                System.arraycopy(bytes, 0, dst, 1, bytes.length);
                return dst;
            }
            else if (v instanceof RTWBooleanValue) {
                byte[] dst = new byte[2];
                dst[0] = 'b';
                if (v.asBoolean()) dst[1] = '1';
                else dst[1] = '0';
                return dst;
            }
            else if (v instanceof RTWThisHasNoValue) {
                byte[] dst = new byte[1];
                dst[0] = 'n';
                return dst;
            }
            // bkdb: 2012-11-01: converting this from a handler of RTWPointerValue to a handler of
            // Entity to circumvent problems surrounding the composition vs. subclassing issue that
            // we're trying to keep at bay for now.
            else if (v instanceof Entity) {
                // Operation here will parallel RTWListValue.  We'll reuse RTWStringValue to encode
                // slot names.  We'll wrap element references by prefixing an 'e' to the usual
                // marshalling of the enclosed RTWValue.
                final RTWLocation l = ((Entity)v).getRTWLocation();
                
                // See comments for RTWListValue
                final byte[][] elementList = new byte[l.size()*2][];
                int totalLength = 0;
                for (int i = 0; i < l.size(); i++) {
                    if (l.isSlot(i)) {
                        elementList[i*2+1] = new RTWStringValue(l.getAsSlot(i)).toUTF8();
                        elementList[i*2] = Integer.toString(elementList[i*2+1].length).getBytes();
                        totalLength = totalLength + elementList[i*2].length + elementList[i*2+1].length;
                    } else {
                        // Same as above, but leave 1 extra byte for the 'e' prefix
                        elementList[i*2+1] = l.getAsElement(i).getVal().toUTF8();
                        elementList[i*2] = Integer.toString(elementList[i*2+1].length).getBytes();
                        totalLength = totalLength + elementList[i*2].length + elementList[i*2+1].length + 1;
                    }
                }

                final byte[] dst = new byte[totalLength + 1];
                dst[0] = 'p';
                int dstOffset = 1;
                for (int i = 0; i < l.size(); i++) {
                    // First the length marker
                    int len = elementList[i*2].length;
                    System.arraycopy(elementList[i*2], 0, dst, dstOffset, len);
                    dstOffset += len;

                    // Prefix the actual content with an e to denote an RTWElementRef
                    if (!l.isSlot(i)) dst[dstOffset++] = 'e';

                    // Then the content, be it the element in the RTWElementRef or the
                    // RTWStringValue slot name.
                    len = elementList[i*2+1].length;
                    System.arraycopy(elementList[i*2+1], 0, dst, dstOffset, len);
                    dstOffset += len;
                }
                return dst;
            }
            else {
                throw new RuntimeException("Unrecognized RTWValue subclass " + v.getClass().getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("toUTF8() on " + toString(), e);
        }
    }

    /**
     * Convenience alternative {@link toUTF8} that returns a String rather than byte[]
     */
    default public String toUTF8String() {
        try {
            // This one is our best bet for now.  It seems to be reasonably fast despite being
            // geared toward human-friendliness.  The upshot is that it avoids using a few very
            // common delimiters, making it robust to the common case where people tend not to give
            // much thought to metacharacters / quoting / escaping
            return new String(toUTF8(), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("toUTF8String() on " + toString(), e);
        }
    }

    /**
     * Returns a new RTWValue instance given the output of {@link toUTF8}
     *
     * This will only return immutable RTWValue objects.
     */
    public static RTWValue fromUTF8(byte[] bytes) {
        return fromUTF8(bytes, 0, bytes.length);
    }
    
    /**
     * Returns a new RTWValue instance given the output of {@link toUTF8String}
     *
     * This will only return immutable RTWValue objects.
     */
    public static RTWValue fromUTF8(String s) {
        try {
            return fromUTF8(s.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("fromUTF8(\"" + s + "\")", e);
        }
    }

    /**
     * Returns a new RTWValue instance given the output of {@link toUTF8String}
     *
     * This will only return immutable RTWValue objects.
     *
     * This relies on the tooFar parameter to know how many bytes to consume; the length of a single
     * RTWValue is itself not encoded as part of that RTWValue.
     */
    public static RTWValue fromUTF8(byte[] bytes, int offset, final int tooFar) {
        int originalOffset = offset;
        try {
            if (bytes.length < tooFar)
                throw new RuntimeException("tooFar = " + tooFar + ", but there are only "
                        + bytes.length + " bytes");

            // Lists are tricky because we need to be able to detect when one value ends and the
            // next begins (including the case of recursive lists).  What we'll do is prefix
            // each list element with an ASCII decimal number indicating how many characters
            // belong to the next list element.  We could probably get more clever and do
            // something faster with less String splicing and churn, but this is how we'll
            // start.

            // Here we go in order of commonality
            if (bytes[offset] == 's') {
                offset++;
                String v = new String(bytes, offset, tooFar - offset);

                // Now we have to unescape things.  Again, this could probably be done better, and
                // this is optimized against ever having to unescape.
                int pos = 0;
                while (true) {
                    pos = v.indexOf(1, pos);
                    if (pos < 0) break;
                    char c = v.charAt(pos+1);
                    if (c == 1) {
                        v = v.substring(0, pos) + (char)1 + v.substring(pos+2);
                    } else if (c == 2) {
                        v = v.substring(0, pos) + (char)2 + v.substring(pos+2);
                    } else if (c == 3) {
                        v = v.substring(0, pos) + '\t' + v.substring(pos+2);
                    } else if (c == 4) {
                        v = v.substring(0, pos) + '\n' + v.substring(pos+2);
                    } else {
                        throw new RuntimeException("Unrecognized escape indicator '" + c + "'");
                    }

                    // Only skip one character because we replaced two characters with one
                    pos++;  
                }

                return new RTWStringValue(v);
            }
            else if (bytes[offset] == 'l') {
                final ArrayList<RTWValue> list = new ArrayList<RTWValue>();
                try {
                    offset++;
                    while (offset < tooFar) {
                            
                        // Get # bytes for the next RTWValue element
                        int nextLength = 0;
                        while (true) {
                            final byte b = bytes[offset];
                            if (b < '0' || b > '9') break;
                            nextLength = nextLength * 10 + (int)(b - '0');
                            offset++;
                        }
                        RTWValue nextVal = fromUTF8(bytes, offset, offset + nextLength);
                        if (nextVal == null) {
                            String element = new String(bytes, offset, nextLength);
                            throw new RuntimeException("Failed to parse \"" + element + "\"");
                        }
                        list.add(nextVal);
                        offset += nextLength;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("After having built " + list, e);
                }
                return RTWImmutableListValue.copy(list);
            }
            else if (bytes[offset] == 'p') {
                // This is similar to the case of lists
                List<Object> elements = new ArrayList<Object>();
                try {
                    offset++;
                    while (offset < tooFar) {
                        int nextLength = 0;
                        while (true) {
                            final byte b = bytes[offset];
                            if (b < '0' || b > '9') break;
                            nextLength = nextLength * 10 + (int)(b - '0');
                            offset++;
                        }
                        if (bytes[offset] == 'e') {
                            offset++;
                            RTWElementRef e = new RTWElementRef(fromUTF8(bytes, offset, offset + nextLength));
                            elements.add(e);
                        } else {
                            RTWValue v = fromUTF8(bytes, offset, offset + nextLength);
                            if (!(v instanceof RTWStringValue))
                                throw new RuntimeException("Slot name is non-String value " + v);
                            elements.add(((RTWStringValue)v).asString());
                        }
                        offset += nextLength;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("After having build " + elements, e);
                }

                // bkdb: Here we are using AbstractRTWLocation as a strictly abstract RTWLocation,
                // which is close enough for as long as Theo2012 is doing the same.
                return new RTWPointerValue(new AbstractRTWLocation(elements.toArray()));
            }
            else if (bytes[offset] == 'd') {
                offset++;
                final String v = new String(bytes, offset, tooFar - offset);
                return new RTWDoubleValue(Double.parseDouble(v));
            }
            else if (bytes[offset] == 'i') {
                offset++;
                // We can switch to StringUtility.parseInt later if this turns out to be any
                // kind of a hot spot.  We could do something similar for doubles (keeping them
                // human-readable) or perhaps just write out whichever IEEE binary format it is
                // (thus avoiding the need to parse doubles, which can be a little bit tricky to
                // get exacly right).
                final String v = new String(bytes, offset, tooFar - offset);
                return new RTWIntegerValue(Integer.parseInt(v));
            }
            else if (bytes[offset] == 'b') {
                // FODO: Should we not use singleton instances of RTWBooleanValue here?
                if (bytes[offset+1] == '1') return RTWBooleanValue.TRUE;
                else if (bytes[offset+1] == '0') return RTWBooleanValue.FALSE;
                else
                    throw new RuntimeException("Unrecognized boolean value " + bytes[offset+1]);
            }
            else if (bytes[offset] == 'n') {
                return RTWThisHasNoValue.NONE;
            }
            else {
                throw new RuntimeException("Unrecognized type character '" + (char)bytes[offset] + "'");
            }
        } catch (Exception e) {
            int len = tooFar - originalOffset;
            if (len > bytes.length - originalOffset) len = bytes.length - originalOffset;
            String v = new String(bytes, originalOffset, len);
            throw new RuntimeException("fromUTF8(\"" + v + "\")", e);
        }
    }
}

