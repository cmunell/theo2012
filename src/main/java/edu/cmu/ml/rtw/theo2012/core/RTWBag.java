package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An RTWBag encapsulates the notion of a "bag" of RTWValues, where "bag" is roughly equivalent to
 * "set"<p>
 *
 * In particular, an RTWBag represents the content of a slot in the KB, or, equivalently, the answer
 * to a {@link Query} (which is itself a subclass of RTWBag).<p>
 *
 * Nominally, we define "bag" to mean the same thing as "set".  But because we might sometimes want
 * to relax that definition, for instance to tolerate some (presumably marginal) amount of
 * duplication while iterating through all of the values in an RTWBag for efficiency purposes, we
 * are careful not to give the impression that an RTWBag will always strictly act like a set.  This
 * allows for potential futures where we would be willing to settle for a sufficient proxy of of the
 * values actually in a KB slot due to tractability concerns, considering that the true set of
 * values might require on the fly inference, access past a network partition, a stochastic
 * definition of what the slot contains, or whatever else.<p>
 *
 * For common use and in all current implementation, all RTWBags are in fact sets.<p>
 *
 * Note that the RTWValues that an RTWBag provides access to will often be immutable (as most
 * RTWValue subclasses are).  This may be done as a rule to simplify implementation of KB code.<p>
 *
 * {@link RTWBagBase} exists as a base class that takes care of a lot of boilerplate.<p>
 */
public interface RTWBag {
    @Override public int hashCode();

    /**
     * Because an RTWBag object is conceptually equivalent to a pointer to a bag of values, we say
     * that two RTWBag objects are equal and only equal when they are the same object, with no
     * regard the value(s), if any, they contain.  Deep equality can be added separately as
     * appropriate.<p>
     *
     * Note that a Theo {@link Query} object effectively gives a "name" to an RTWBag, where that
     * name is some Theo expression designating the location in the KB where some set of values may
     * be found.  Indeed, it implements the RTWBag interface, meaning that it is an RTWBag.  By
     * keeping our definition of equals so narrow, Query is then given the opportunity to extend the
     * meaning of equals by defining two Query objects designating the same location in the same KB
     * to equal, even if they are not both the same object.<p>
     */
    @Override public boolean equals(Object obj);

    /**
     * Returns a human-friendly string describing what is in this bag (but not its actual contents)<p>
     *
     * This does not render the values in this bag.  Use {@link dump} for that instead.  This is
     * meant to pose no threat of great size or slowness, geared toward casual use in things like
     * logs and error messages.<p>
     */
    @Override public String toString();

    /**
     * Return the number of values in this bag
     */
    public int getNumValues();

    /**
     * Whether or not this bag has no values.
     */
    public boolean isEmpty();

    /**
     * Dereferences this bag and returns a String containing the output of the toString operation on
     * each of the values in this bag.<p>
     *
     * Note that this is inherently non-scalable.  It is meant for controlled situations and
     * debugging purposes.<p>
     */
    public String valueDump();

    /**
     * Returns true if there is exactly 1 value in this bag.
     */
    public boolean has1Value();

    /**
     * Returns true if this bag has exactly 1 value and that value is an {@link RTWStringValue}
     */
    public boolean has1String();

    /**
     * Returns true if this bag has exactly 1 value and that value is an {@link RTWIntegerValue}
     */
    public boolean has1Integer();

    /**
     * Returns true if this bag has exactly 1 value and that value is an {@link RTWDoubleValue}
     */
    public boolean has1Double();

    /**
     * Returns true if this bag has exactly 1 value and that value is an {@link RTWBooleanValue}
     */
    public boolean has1Boolean();

    /**
     * Returns true if this bag has exactly 1 value and that value is an {@link Entity}
     */
    public boolean has1Entity();

    // bkdb TODO: It's been decided that the typed iterations should return primative types, but
    // we've put that off until // bk:entityref even though the excuse for doing so didn't look as
    // solid as it once did because it seems we'll have to make an IterableIterator subclass for
    // each type, and I'm getting short on time.
    //
    // bkisiel 2017-03-30: Actually nothing in the NELL codebase uses anything other than entity (or
    // sometimes desires primitiveentity).  Maybe sometime String.  So I'd say leave this for some
    // future consideration or until there's some desire for change.  I suppose it would be more
    // consistent with e.g. inot1* to change.

    /**
     * Return an iterable over the set of RTWValues in this bag<p>
     *
     * Note that this is not named "iterator" beacuse we offer many different iterators with
     * different semantics, and it's important for the user to be aware of this and to chose one
     * whose semantics matches his needs and/or assumptions.  That and because it returns an
     * Iterable to make it easy to use in for loops and such.<p>
     *
     * bkdb: It would be more consistent to call this "valueIter" but it is used so frequently that
     * having shorthand is worth it.  FODO: RTWLocation-era code is typically
     * KbM.getValue(...).iter(), which would better be KbM.getValueIter(...).  We could sed all that
     * from iter to valueIter if it turns out that Theo2012-era code would benefit more from such a
     * construct than having this named "iter") // bk:api<p>
     */
    public Iterable<RTWValue> iter();

    /**
     * Return an iterable over the set of values in this bag, all of which must be booleans<p>
     *
     * If a value of the wrong type is encountered during iteration, an exception will be thrown.<p>
     */
    public Iterable<RTWBooleanValue> booleanIter();

    /**
     * Return an iterable over the set of values in this bag, all of which must be doubles.<p>
     *
     * If a value of the wrong type is encountered during iteration, an exception will be thrown.<p>
     */
    public Iterable<RTWDoubleValue> doubleIter();

    /**
     * Return an iterable over the set of values in this bag, all of which must be integers.<p>
     *
     * If a value of the wrong type is encountered during iteration, an exception will be thrown.<p>
     */
    public Iterable<RTWIntegerValue> integerIter();

    /**
     * Return an iterable over the set of values in this bag, all of which must be strings.<p>
     *
     * If a value of the wrong type is encountered during iteration, an exception will be thrown.<p>
     */
    public Iterable<RTWStringValue> stringIter();

    /**
     * Return an iterable over the set of values in this bag, all of which must be Entities.<p>
     *
     * If a value of the wrong type is encountered during iteration, an exception will be thrown.<p>
     */
    public Iterable<Entity> entityIter();

    /**
     * Return the single RTWValue found in this bag, or null if there are no values<p>
     *
     * If multiple values are found an exception will be thrown.<p>
     */
    public RTWValue into1Value();

    /**
     * Return the single String found in this bag, or null if there are no values<p>
     *
     * If the value is of the wrong type or multiple values are found, an exception will be thrown.<p>
     */
    public String into1String();

    /**
     * Return the single Integer found in this bag, or null if there are no values<p>
     *
     * If the value is of the wrong type or multiple values are found, an exception will be thrown.<p>
     */
    public Integer into1Integer();

    /**
     * Return the single Double found in this bag, or null if there are no values<p>
     *
     * If the value is of the wrong type or multiple values are found, an exception will be thrown.<p>
     */
    public Double into1Double();

    /**
     * Return the single Boolean found in this bag, or null if there are no values<p>
     *
     * If the value is of the wrong type or multiple values are found, an exception will be thrown.<p>
     */
    public Boolean into1Boolean();

    /**
     * Return the single Entity found in this bag, or null if there are no values<p>
     *
     * If the value is of the wrong type or multiple values are found, an exception will be thrown.<p>
     */
    public Entity into1Entity();

    /**
     * Return the RTWValue in this bag, throwing an exception if there is no value or if there is more
     * than one value.
     */
    public RTWValue need1Value();

    /**
     * Return the boolean in this bag, throwing an exception if there is no value or if it is not a
     * scalar or otherwise not of the requested type.
     */
    public boolean need1Boolean();

    /**
     * Return the double in this bag, throwing an exception if there is no value or if it is not a
     * scalar or otherwise not of the requested type.
     */
    public double need1Double();

    /**
     * Return the int in this bag, throwing an exception if there is no value or if it is not a
     * scalar or otherwise not of the requested type.
     */
    public int need1Integer();

    /**
     * Return the String in this bag, throwing an exception if there is no value or if it is not a
     * scalar or otherwise not of the requested type.
     */
    public String need1String();

    /**
     * Return the Entity in this bag, throwing an exception if there is no value or if it is not a
     * scalar or otherwise not of the requested type.
     */
    public Entity need1Entity();

    /**
     * Return whether or not the given value is present in this bag<p>
     *
     * bk:api Seeing how commonly used this method is, I'm starting to wonder if its worth the
     * uniformity to not just use contains.  Was there a reason we didn't use contains?<p>
     */
    public boolean containsValue(RTWValue v);
}