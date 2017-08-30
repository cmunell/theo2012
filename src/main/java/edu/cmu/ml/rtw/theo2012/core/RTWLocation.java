package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

 /**
 * Interface that extends an {@link RTWBag} to include a location (specifically, a location in a KB)
 * of that bag of values.
 *
 *
 * HISTORICAL CONTEXT
 *
 * RTWLocation predates Theo2012, but is kind-of a proto-Theo2012 in that it includes ideas like
 * hanging KB slots on values.  There does exist a 1:1 mapping between the "KB location" captured by
 * an RTWLocation and a Theo2012 Entity expression.  In fact, the current still-in-development
 * implementation of Theo2012 still uses RTWLocation as part of its basis: every Theo2012 Entity can
 * return an equivalent RTWLocation, and a Theo2012 Entity object an be constructed from an
 * RTWLocation.  This interchangability may or may not persist, but it is valuable for the time
 * being as we live in a world that must simultaneously support old RTWLocaton-centric code and new
 * Theo2012-centric code.
 *
 *
 * HOW RTWLocation REPRESENTS A LOCATION IN THE KB
 *
 * The representation of a KB location in an RTWLocation is basically an Object[].  At NELL's
 * inception, this strictly took the form of an entity name followed by zero or more slot names
 * indicating subslots (also sometimes called a "slot list" or "slot address").  To generalize,
 * RTWLocation considered the first element (also known as a "top level entity" or "primitive
 * entity") to be treated as a slot as well; one cannot associate values with a primitive entity,
 * but that is a matter of the KB and not of the representation of a location in the KB.  Any
 * element of an RTWLocation's Object[] that is a String or an RTWStringValue indicates a "slot" for
 * the purposes of RTWLocation.
 *
 * We then extended RTWLocation to be able to capture what in Theo2012 is called a Belief by
 * allowing an element in its Object[] to alternatively be an {@link RTWElementRef}.  What we would
 * write in Theo2012 as (E S = V) becomes and Object[] containing [E, S, RTWElementRef(V)], which we
 * designate "<E, S, =V>" in the world of RTWLocation.
 *
 * With this extension, RTWLocation became able to represent subslots attached to values.  In
 * Theo2012 we would have ((E S1 = V) S2), and this would correspond to RTWLocation <E, S1, =V, S1>.
 * In Theo2012 we would have (((E S1 = V1) S2 = V2), and in RTWLocation <E, S1, =V1, S1, =V2>.  So
 * as long as the values are simple primitive RTWValues like RTWStringValue, RTWIntegerValue, etc.,
 * then RTWLocation provides a simple, fast, linear projection of the Theo2012 expression into an
 * Object[].
 *
 * Things get slightly more complicated if the values are themselves Entities.  In the world of
 * RTWLocation, values that are Entities are represented as RTWPointerValues.  An RTWPointerValue is
 * simply a wrapper around an RTWLocation (recall that every Entity can be expressed as an
 * RTWLocation and vise versa).  The RTWLocation form would still be something like <E, S, =V>, but
 * the "=V" would be an RTWElementRef containing an RTWPointerValue containin and RTWLocation
 * representing the Entity that is V.
 *
 *
 * FURTHER COMMENTARIES ON THIS REPRESENTATION
 *
 * A better abstract representation of a KB location might skip the RTWPointerValue and
 * RTWElementRef and simply store an RTWLocation directly in the Object[], but what we have now is
 * the product of an evolutionary process, and this process makes more sense in the context of
 * {@link Store} being the best available abstraction, and in the context of uncertainty about
 * whether or not we were going to make RTWLocation itself a subclass of RTWValue in order to pander
 * to even older concerns that are no longer relevant.
 *
 * One advantage that RTWLocation has over Theo2012 is that its basic representation of Object[]
 * offers a way to manipulate Theo expressions with less overhead of object creation and garbage
 * collection than is possible with Theo2012 without some acrobatics.  It is not yet known how
 * important this is, although past experience suggests that it can be insidiously important in
 * certain cases.  This is something we can revisit after Theo2012 goes into more thorough and
 * well-understood use, and we come to a point of deciding the fate of older RTWLocation-centric
 * legacy code.
 *
 *
 * ATTACHMENT OF AN RTWLocation TO A Store
 *
 * Our historical context of KbManipulation is one in which there is a single static instance of
 * "the KB".  For obvious reasons, we wanted to move to dynamic KB objects and being able to access
 * multiple KBs simultaneously.  Because RTWLocation offers not only a KB location but access to the
 * content at that location (recall that RTWLocation is a subinterface of {@link RTWBag}), it then
 * becomes necessary for an RTWLocation object to know which KB it is referring to, and that meant
 * attaching it to a {@link Store} object because Store was the closest thing we had to a non-static
 * encapsulation of a KB.
 *
 * An RTWLocation becomes attached to a Store via that Store object's getLoc method.  The idea is
 * that each Store would create its own RTWLocation subclass and therein provide the guts of the
 * RTWBag methods for accessing KB content, which would naturally need to be done in a different way
 * for each different Store implementation.
 *
 * Legacy code, however, continued to construct RTWLocations and to access their associated values
 * simply through "new RTWLocation".  In order to cater to legacy code based around static acess to
 * a KB (and also to facilitate the simple common case where there is indeed simply "the KB"), the
 * default case of an RTWLocation not attached to any particular Store object results in forwarding
 * its RTWBag method calls to KbManipulation in order to access "the KB" that KbManipulation
 * continues to represent.  KbManipulation, in turn, would forward the access into the Store object
 * that it used internally to hold the KB.
 *
 * This system of attachment never matured into something well-developed (e.g. there is no getStore
 * method) because Theo2012 came along to subsume a lot of this stuff.  It may or may not continue
 * to exist in the future, but it's an important mechanism to bear in mind.
 * 
 *
 * OTHER USEFUL THINGS TO KNOW
 *
 * RTWLocation instances are immutable once constructed in order to help simplify things that are
 * multithreaded or that would otherwise step on eachother's toes.  As is the case for all {@link
 * RTWBag} subclasses, callers should not in general assume that the RTWValues furnished by an
 * RTWLocation are not immutable (most RTWValue implementations are immutable anyway).
 *
 * We do not provide a "put" method to compliment the "get" method because that winds up being
 * forced into being too low-level a "put" to be useful for application code.  This class is really
 * geared toward operations at the "store" level.  Even the "get" is too low-level for a real Theo
 * implementation.  So we can expect that we'll have higher-level Theo-level classes down the road
 * where the "get" works in a Theo fasion and where we can reconsider whether or not to have a "put"
 * there as well.
 *
 * An RTWLocation can end in an RTWElementRef and thereby specify an entire assertion ("Belief" in
 * Theo2011 terms).  While compact, these are often awkward for callers to construct and methods
 * being called don't necessarily need such a thing.  So we are informally going to try a convention
 * of setting up functions that need an assertion as input so that they take an RTWLocation
 * designating a slot and a sepearate RTWValue parameter specifying the element in question within
 * that slot.
 *
 * FODO 2012-12-05: For Wedge expeidency, we're having RTWLocation constructors accept any RTWValue
 * that is an Entity via the asString method (which will work for all primitive entities).  But the
 * plan is to then have Theo2012 implementations use some RTWLocation subclass that does not do this
 * so that we do not come to rely on asString or on some ill-conceived situation of confusion
 * between arbitrary Entities and individual RTWLocation elements.  At present, all parenthetical
 * Theo whiteboard expressions are represented through RTWElementRefs; if and only when we have a
 * distinct RTWLocation equivalent for use within Theo code for whiteboard expressions would we want
 * to allow the direct use of a composite Entity or RTWLocation as an element within an RTWLocation.
 *
 *
 * HOLDING PEN FOR DOCUMENTATION TAKEN OUT OF OTHER CLASSES WHERE IT DOESN'T BELONG
 *
 * There is a direct correspondence between this Entity class and {@link RTWLocation}.  In effect,
 * RTWLocation is a way to take a Theo expression representing an Entity and represent it as a list
 * of elements in a way that is suitable for an underlying KB storage mechanism to view the content
 * of the KB as a collection of (RTWLocation, set-of-values) pairs.  Entity, on the other hand, is
 * geared toward end-user use in application code that operates more abstractly in terms of Theo
 * concepts.<p>
 *
 * TODO: on the choice of using getValues and getBelief etc. per Sandbox.java after bouncing off
 * Tom.<p>

 */
public interface RTWLocation extends RTWBag {
    /**
     * Construct a new location based on this location
     *
     * This will return a new RTWLocation instance that is a copy of this one with the given list
     * appended to it.
     *
     * Semantics for interpreting the arguments are the same as for the similar constructor.  This
     * is not especially fast, and the subslot and element methods are to be preferred where
     * applicable.
     */
    public RTWLocation append(Object... list);

    /**
     * Construct a new location based on this one by appending a subslot to it
     *
     * This will return a new RTWLocation instance that is a copy of this one with the given slot
     * name appended to it.
     */
    public RTWLocation subslot(String slot);

    /**
     * Construct a new location based on this one by appending an RTWElementRef to it
     *
     * This will return a new RTWLocation instance that is a copy of this one with the given slot
     * name appended to it.  Thus, this RTWLocation must end in a slot name (meaning also that it
     * must not be zero-length).
     */
    public RTWLocation element(RTWElementRef e);

    /**
     * Alternative to the other element function that constructs an RTWElementRef from the RTWValue
     * given.
     */
    public RTWLocation element(RTWValue v);

    /**
     * Construct a new location by backing up one step
     *
     * This will return a new RTWLocation instance that is missing the last value.
     *
     * Use siblingSlot() if that's what you're after; that will avoid constructing an intermediate
     * RTWLocation object.
     */
    public RTWLocation parent();

    /**
     * Construct a new location by taking the first N elements of this one
     */
    public RTWLocation firstN(int n);

    /**
     * Construct a new location by changing the last value in the address from one slot to another.
     *
     * This will return a new RTWLocation instance.  An exception will be thrown if this address
     * doesn't end in a slot name.
     */
    public RTWLocation siblingSlot(String slot);

    /**
     * Return the last value in the address, throwing an exception if it is not a subslot
     *
     * Returns null if this is a zero-length location
     */
    public String lastAsSlot();

    /**
     * Return the last value in the address, throwing an exception if it is not an element reference
     *
     * Returns null if this is a zero-length location
     */
    public RTWElementRef lastAsElement();

    /**
     * Return the value referred to by the last value in the address, throwing an exception if the
     * last value is not an element reference.
     *
     * Returns null if this is a zero-length location.
     *
     * In other words, this is the same as lastAsElement().getVal()
     */
    public RTWValue lastAsValue();

    /**
     * Returns whether or not the last value in the address is a slot name (the alternative being an
     * element reference)
     *
     * NOTE: The first element (i.e. "primitive entity" in Theo-speak) is considered by RTWLocation
     * to be a slot because the (location, value) pairs view of things presented by the Store class
     * has no reason of its own to consider it differently.
     *
     * Because the fate of RTWLocation is at this point uncertain, there is no expectation about
     * whether or not the interpretation of the primitive entity as being a slot for the purposes of
     * RTWLocation will change to better mirror Theo.
     */
    public boolean endsInSlot();

    /**
     * Returns the primitive entity (aka "top-level entity") of this location
     * 
     * Returns null if this is a zero-length location
     */
    public String getPrimitiveEntity();   // bk: entityref I guess we'll have to change this to RTWValue at some point so that it can return an entity ref

    /**
     * Return the number of elements in this address
     *
     * Note that this is different from RTWBag's getNumValues, which returns the number of values
     * store in the location designated by this address.
     */
    public int size();

    /**
     * Return whether or not element i of this address is a slot name
     *
     * If it is not, then it is an element reference
     *
     * bkdb: can/should we make it an error condition to have i=0 and/or to invoke endsInSlot on a
     * zero-length RTWLocation?
     */
    public boolean isSlot(int i);

    /**
     * Return element i of this address, which must be a slot name
     *
     * An exception will be thrown if i is out of range or if element i is not a slot name (e.g. if
     * it is an element reference)
     */
    public String getAsSlot(int i);

    /**
     * Return element i of this address, which must be an element reference
     *
     * An exception will be thrown if i is out of range or if element i is not an elment reference
     * (e.g. if it is a slot name)
     */
    public RTWElementRef getAsElement(int i);

    /**
     * Return element i of this address, which must be an element reference, as the value is refers
     * to.
     *
     * An exception will be thrown if i is out of range or if element i is not an element reference
     * (e.g. if it is a slot name)
     *
     * In other words, this is the same as getAsElementt(i).getVal()
     */
    public RTWValue getAsValue(int i);

    /**
     * Hash code based strictly on the elements of the RTWLocation (and not dependent on the Store
     * to which it is attached)
     *
     * Note the definition of {@link equals} and the required relationship between hashCode and
     * equals.
     */
    @Override public int hashCode();

    /**
     * Returns whether or not this location is the same as the given location; does NOT dereference
     * locations.
     *
     * Location attatched to different Stores (where we can think of being attached to no store as
     * being attached to a Store of value null) are considered to be different.  This makes us fully
     * consistent with {@link RTWBag.equals}.
     *
     * bkdb: indeed, we can't really do the Store-sameness check without a getStore, which we've
     * delayed adding for now (and we can delay because there won't be any "real" multistore action
     * going on for a while yet).
     */
    @Override public boolean equals(Object obj);

    /**
     * Renders the this location as a human-friendly string geared for logging and error messages
     *
     * This does not render the value referred to by this location.  Use {@link dump} for that
     * instead.  This is consistent with {@link RTWBag.toString}.
     */
    @Override public String toString();
}