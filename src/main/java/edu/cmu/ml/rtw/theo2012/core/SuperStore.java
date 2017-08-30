package edu.cmu.ml.rtw.theo2012.core;

/**
 * This is a {@link Store} that supports efficient "(*, S, V)" queries
 *
 * The get method of Store could be called a "(E, S, *)" query in that it returns all values V
 * present in some slot S of entity E (where a single RTWLocation object is used to convey both S
 * and E).  But there is no way to efficiently execute a "(*, S, V)" query with a Store in order to
 * get all entities E such that E has a slot S containing value V.  With a SuperStore, this is
 * efficiently possible.
 *
 * In the semantics of a SuperStore, this additional query facility applies only to values that are
 * RTWPointerValues.  So this is equivalent to travelling back from something to which zero or more
 * RTWPointerValues point, and generating the set of RTWPointerValues that point there.
 *
 * In the simplest case, a SuperStore is concerned with assertions of the the form (E1 S) = <E2>,
 * where values are RTWPointerValues that point to primitive entities.  But a SuperStore must also
 * be concerned with more subtle cases like (E1 S) = <E2, S, V>, wherein the RTWPointerValue points
 * to a composite entity, like a query, belief, or arbitrary nesting thereof.  Here, a SuperStore
 * would have to maintain indexing to support queries where the V of the query is a composite
 * entity.
 *
 * We have a rule here that may help to simplify implementation: any RTWPointerValue stored as a
 * value must point to something that actually exists.  Therefore, if <E2> is stored as a value,
 * then the entity E2 must first exist according to isEntity, meaning that E2 must have at least one
 * slot (or series of subslots) in which is stored a value.  A SuperStore should consider a
 * violation of this to be an error condition, and should, accordingly, ensure that all
 * RTWPointerValues pointing to E2 be deleted if E2 itself is deleted.
 *
 * This also extends to RTWPointerValues that point to composite entities.  SuperStores may assume
 * and require that, for isntance, (E2 S) = V already be asserted in order to allow calling code to
 * store an RTWPointerValue pointing to it.  In the case of an RTWPointerValue pointing to a query
 * (i.e. to an RTWLocation ending in a slot), the same isEntity requirement as for RTWPointerValues
 * to primitive entities applies.  And, accordingly, a SuperStore should delete such
 * RTWPointerValues when delete operations cause these requirements to no longer be met.
 *
 * Note that an RWTPointerValue might point to a location that itself contains an RTWPointerValue.
 * All the same rules apply to these nested things.  Conveniently, because all of the nested
 * RTWPointerValues must correspond to things that are individually stated elsewhere in the KB, much
 * of the work of checking and enforcing the rules for nested things is entailed automatically.
 */
public interface SuperStore extends Store {
    /**
     * Return the set of RTWPointerValues that point to the given referent, constrained by the slot
     * used to do the referring.
     *
     * The RTWLocations in the RTWPointerValues that are returned will not end in the slot being
     * requested; that is, they will in fact be only the * of the (*, S, V).
     *
     * For example, say you had this in your KB:
     *
     * 1990
     *   Bob
     *     livesIn = <Tokyo>
     *       =Tokyo
     *         accordingTo = <Mary>
     *
     * Then getPointers(<Mary>, accordingTo) would yield a set containing the RTWPointerValue <1990,
     * Bob, livesIn, =Tokyo>.
     *
     * getPointers(<Tokyo>, livesIn) would yield a set containing the RTWPointerValue <1990, Bob>.
     */
    public RTWBag getPointers(RTWLocation referent, String slot);

    /**
     * Return the list of slots through which there exist RTWPoitners that point to the given referent
     *
     * In other words, this is the list of slots for which getPointers would return a non-empty RTWBag.
     *
     * This returns an RTWListValue of RTWStringValues in keeping with Store.getSubslots.
     * Similarly, it will return null instead of an empty list.
     */
    public RTWListValue getPointingSlots(RTWLocation referent);

    // TODO: I guess we should augment the documentation for Store's methods, e.g. that add needs to
    // require that all RTWPointerValues (including those nested within other RTWPointerValues)
    // refer to existent things.  Can a subclass of an implementation @Override its parent's methods
    // to provide anchors for the additional documentation?
}

