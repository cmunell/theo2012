package edu.cmu.ml.rtw.theo2012;

/**
 * Abstract base class that provides some conveniences for typical {@link RTWValue} implementations<p>
 *
 * This also serves as the basis for implementing Comparable<RTWValue>.  I'm not sure how easily
 * RTWValue subclasses could augment compareTo on their own, but, ideally, we won't really need to
 * get fancier than the implementation here.<p>
 */
public abstract class RTWValueBase implements RTWValue {

    @Override public abstract int hashCode();

    @Override public abstract boolean equals(Object obj);

    /**
     * Default implementation throws an exeption complaining about nonconvertibility
     */
    @Override public String asString() {
        throw new RuntimeException(this.getClass().getName() + " cannot be converted to a String");
    }

    /**
     * Default implementation throws an exeption complaining about nonconvertibility
     */
    @Override public int asInteger() {
        throw new RuntimeException(this.getClass().getName() + " cannot be converted to an Integer");
    }

    /**
     * Default implementation throws an exeption complaining about nonconvertibility
     */
    @Override public double asDouble() {
        throw new RuntimeException(this.getClass().getName() + " cannot be converted to a Double");
    }

    /**
     * Default implementation throws an exeption complaining about nonconvertibility
     */
    @Override public boolean asBoolean() {
        throw new RuntimeException(this.getClass().getName() + " cannot be converted to a Boolean");
    }

    @Override public Object clone() throws CloneNotSupportedException { 
        RTWValue clone = (RTWValue)super.clone(); 
        return clone;
    }

    /**
     * This returns a negative value if o1 is less than o2, zero if they are considered to be equal,
     * and a positive value if o1 is greater than o2, thereby defining an ordering over all values
     * of all standard types of RTWValue.<p>
     *
     * We face the challenge of comparing dissimilar types, and of lists in the form of both
     * RTWListValues and the RTWLocations representation of Entity objects, and of the nesting
     * thereof.  So, foremost, we define a global ordering among the possible RTWValue types so that
     * we needn't consider the question of how to choose an ordering based on the content of two
     * RTWValues of dissimilar type.  (And, considering the preponderance of lists and of nesting,
     * this will hopefully save us computational time as well.)  For simplicity, we will use a
     * global ordering that follows the lexographical ordering of the names of the different
     * RTWValue types.  Going from least to greatest, this is:<p>
     *
     * <ul>
     * <li>RTWThisHasNoValue
     * <li>RTWBooleanValue
     * <li>RTWIntegerValue
     * <li>RTWDoubleValue
     * <li>RTWListValue
     * <li>Entity
     * <li>RTWStringValue
     * </ul>
     * <p>
     * 
     * For boolean, integer, double, and string values, the ordering is easy.  (For boolean, we say
     * that true comes after false.)<p>
     *
     * For lists, both RTWListValue, and the RTWLocation representation an Entity object, we say
     * that a longer list comes after a shorter list.  In the case of equal-length lists, we proceed
     * to do an element-by-element comparison, treating the earlier elements like digits of greater
     * significant value than the later.  That way, we only need to iterate through the lists to the
     * extent that they are exactly equal.  Encountering an RTWListValue or Entity necessarily means
     * recursion.<p>
     *
     * We could apply this ordering to RTWValue globally by making RTWValue subtypes implement
     * Comparable, but I'm not sure that this particular ordering is one that we'd want to reuse
     * globally.  It might also be preferable to have this kind of cross-type comparison contained
     * in a single method rather than spread across many source files.<p>
     */
    @Override public int compareTo(RTWValue o2) throws NullPointerException {
        RTWValue o1 = this;
        try {
            if (o2 == null) throw new NullPointerException();

            // I don't know if this is the most implementationally compact way to go, but it should
            // be good enough.

            if (o1 instanceof RTWStringValue) {
                // Trivially greater than anything other than another string
                if (o2 instanceof RTWStringValue) {
                    String s1 = ((RTWStringValue)o1).asString();
                    String s2 = ((RTWStringValue)o2).asString();
                    return s1.compareTo(s2);
                }
                return 1;
            }

            if (o1 instanceof RTWDoubleValue) {
                // Trivially greater than only these three
                if (o2 instanceof RTWBooleanValue
                        || o2 instanceof RTWIntegerValue
                        || o2 instanceof RTWThisHasNoValue) return 1;

                if (o2 instanceof RTWDoubleValue) {
                    double d1 = ((RTWDoubleValue)o1).asDouble();
                    double d2 = ((RTWDoubleValue)o2).asDouble();
                    return Double.compare(d1, d2);
                }

                // Trivially less than everything else
                return -1;
            }

            if (o1 instanceof RTWListValue) {
                // o1 is trivially greater than these types
                if (o2 instanceof RTWThisHasNoValue || o2 instanceof RTWBooleanValue
                        || o2 instanceof RTWIntegerValue || o2 instanceof RTWDoubleValue) return 1;

                // Recursion if both objects are lists
                if (o2 instanceof RTWListValue) {
                    RTWListValue l1 = (RTWListValue)o1;
                    RTWListValue l2 = (RTWListValue)o2;
                    if (l1.size() > l2.size()) return 1;
                    if (l1.size() < l2.size()) return -1;
                    for (int i = 0; i < l1.size(); i++) {
                        int c = l1.get(i).compareTo(l2.get(i));
                        if (c != 0) return c;
                    }
                    return 0;   // All elements equal
                }

                // Trivially less than everything else
                return -1;
            }

            if (o1 instanceof RTWIntegerValue) {
                // o1 is greater than any boolean or no value
                if (o2 instanceof RTWThisHasNoValue || o2 instanceof RTWBooleanValue) return 1;

                // o1 is conditionally greater than another integer value
                if (o2 instanceof RTWIntegerValue) {
                    int i1 = ((RTWIntegerValue)o1).asInteger();
                    int i2 = ((RTWIntegerValue)o2).asInteger();
                    return i1 - i2;
                }

                // o1 is less than all other types
                return -1;
            }

            if (o1 instanceof RTWBooleanValue) {
                // o1 is greater than no value
                if (o2 instanceof RTWThisHasNoValue) return 1;

                // o1 is less than everything other than a boolean value
                if (o2 instanceof RTWBooleanValue) {
                    boolean b1 = ((RTWBooleanValue)o1).asBoolean();
                    boolean b2 = ((RTWBooleanValue)o2).asBoolean();
                    if (b1 == b2) return 0;
                    if (b1 == true && b2 == false) return 1;
                    return -1;
                }
                return -1;
            }

            if (o1 instanceof RTWThisHasNoValue) {
                // Less than anything other than another no value
                if (o2 instanceof RTWThisHasNoValue) return 0;
                return -1;
            }

            // If o1 was none of those types, then it should be an Entity.  Turn it into an
            // RTWLocation for fast and easy treatment as a list.
            RTWLocation l1;
            if (o1 instanceof Entity) l1 = ((Entity)o1).getRTWLocation();
            else
                throw new RuntimeException("Unrecognized type for this " + o1.getClass().getName());
            
            // Trivially less than string values
            if (o2 instanceof RTWStringValue) return -1;

            // Trivially greater unless o2 is also an Entity
            RTWLocation l2;
            if (o2 instanceof Entity) l2 = ((Entity)o2).getRTWLocation();
            else
                return 1;

            if (l1.size() > l2.size()) return 1;
            if (l1.size() < l2.size()) return -1;

            // We'll say that a slot comes before an RTWElementRef
            for (int i = 0; i < l1.size(); i++) {
                if (l1.isSlot(i)) {
                    if (l2.isSlot(i)) {
                        int c = l1.getAsSlot(i).compareTo(l2.getAsSlot(i));
                        if (c != 0) return c;
                    } else {
                        return -1;
                    }
                } else {
                    if (l2.isSlot(i)) {
                        return 1;
                    } else {
                        RTWValue v1 = l1.getAsElement(i).getVal();
                        RTWValue v2 = l2.getAsElement(i).getVal();
                        int c = v1.compareTo(v2);
                        if (c != 0) return c;
                    }
                }
            }
            return 0;  // Everything was equal
        } catch (Exception e) {
            throw new RuntimeException("compareTo(" + o2 + ") against " + o1, e);
        }
    }
}
