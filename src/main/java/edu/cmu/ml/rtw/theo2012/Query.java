package edu.cmu.ml.rtw.theo2012;

import java.util.Collection;

/**
 * An {@link Entity} that represents a Theo Query in particular<p>
 *
 * A Theo Query has the form (E S), where E is any Entity (including composite entities) and S is a
 * slot.  A Query object functions as a proxy for the set of values in that slot, or, in Theospeak,
 * the set of Beliefs answering that Query.  In this sense, it serves the same purpose as {@link
 * RTWBag}, and, indeed, Query implements RTWBag in order to provide iterators, type-checking, and
 * other conversion methods that allow for easy and scalable access to that set of values.<p>
 *
 * Query also builds on Entity's API by offering variants of some of Entity's methods, like {@link
 * addValue} and {@link deleteValue}, that do not accept a slot parameter.  One can expect it to be
 * more efficient to obtain a Query object and then invoke these methods if there are many
 * operations to perform on a given Query.<p>
 *
 * Note that Theo queries themselves can have subslots appended to them, and therefore that all of
 * Entity's slot-accepting methods still apply equally to Query objects.  For instance, if this
 * Query is (E S), then invoking getQuery(S') for some slot S' would return the Query ((E S) S').<p>
 */
public interface Query extends Entity, RTWBag {

    // bk:api: as with some other RTWValue-accepting methods, should we allow for more "autoboxing"
    // type stuff like accepting .contains("somestring")?
    //
    // One yes vote, provided the auto-unboxing effectively provided by RTWBag is sufficient to make
    // RTWValue almost invisible.  This might entail ensuring that it's easy to get a primtive
    // directly out of an RTWValue in some way consistent with the syntax offered by RTWBag.

    /**
     * Return a collection of all Beliefs associated with this Query.  This includes only beliefs of
     * the form (Q = V), not beliefs of the form ((Q S2) = V).  To get those, form a new Query using
     * this Query and the S2 slot.
     */
    public Iterable<Belief> getBeliefs();
	
    /**
     * Return the belief (Q V) where Q is this Query and V is the given value<p>
     *
     * For Theo0 Queries only: This may return null if the resulting Belief does not map to any
     * existent structure in the KB.<p>
     */
    public Belief getBelief(RTWValue value);
    
    /**
     * Convenience version of getBelief that accepts a value as an Object per the rules in the
     * class-level comments of {@link Entity}
     */
    public Belief getBelief(Object value);

    /**
     * Same as Entity's addValue, but adds the value directly to the slot represented by this Query
     */
    public boolean addValue(RTWValue value);

    /**
     * Convenience version of addValue that accepts a value as an Object per the rules in the
     * class-level comments of {@link Entity}
     */
    public boolean addValue(Object value);

    /**
     * Convenience method that does what addValue does, and then returns what getBelief does
     */
    public Belief addValueAndGetBelief(RTWValue value);

    /**
     * Convenience method that does what addValue does, and then returns what getBelief does
     */
    public Belief addValueAndGetBelief(Object value);

    /**
     * Same as Entity's deleteValue, but deletes the value directly from the slot represented by
     * this Query.  This is equivalent to performing entity.deleteEntity on the belief returned
     * by quere.getBelief(value).
     */
    public boolean deleteValue(RTWValue value);

    /**
     * Convenience version of deleteValue that accepts a value as an Object per the rules in the
     * class-level comments of {@link Entity}
     */
    public boolean deleteValue(Object value);

    /**
     * Macro-delete operation that invokes deleteValue for all values in a given slot<p>
     *
     * Returns whether or not anything was deleted.<p>
     */
    public boolean deleteAllValues();

    /**
     * Considering this Query as "(E S)", return the "E" part.
     */
    public Entity getQueryEntity();

    /**
     * Considering this Query as "(E S)", return the "S" part.
     */
    public Slot getQuerySlot();   // FODO: getQuerySlotName etc?
}