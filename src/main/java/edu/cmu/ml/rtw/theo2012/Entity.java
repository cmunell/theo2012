package edu.cmu.ml.rtw.theo2012;

import java.util.Collection;

/**
 * Represents a Theo Entity<p>
 *
 * An Entity may be a {@link PrimitveEntity} (including a {@link Slot}), a {@link Query}, or a
 * {@link Belief}.  See the Theo whitepaper for further explanation.<p>
 * 
 * Among the convenience methods that Entity offers are some that accept values given as type Object
 * rather than as RTWValue, which can make life quite a bit easier for high-level code that less
 * typically works with values gotten from somebody else already of type RTWValue.  Therefore, we
 * must define a consistent way to interpret non-RTWValue values.  Boolean, Double, Integer, and
 * String are straightfoward; they become RTWBooleanValue, RTWDoubleValue, RTWIntegerValue, and
 * RTWStringValue, respectively.<p>
 *
 * bk:api Where's our recursive delete a la MATLAB's deleteQuery?<p>
 */
public interface Entity extends RTWValue {
    /**
     * Equality predicate inherited from {@link RTWVaule}<p>
     *
     * When considering equality of Entity objects, there are two parts of the Entity to consider.
     * Every Entity object is decomposable into some Theo expression such as might be written on a
     * whiteboard.  This is the primary determinant of equality; any two expressions that are
     * written the same way are said to be equal.  The "evaluation" of that expression, that is,
     * whether or not it corresponds to anything that might or might not be present in the KB, is
     * immaterial.<p>
     *
     * The other part of an Entity is the particular Theo class, and the particular instance of that
     * class, that that Entity came from.  This is likenable to a notion of which KB an Entity came
     * from.  To begin with, we will pursue the "easier" definition of equality that says that two
     * Entities from two different KBs are not equal.  The overwhelmingly common use case will
     * involve exactly one KB, however, so we may relatively safely revisit this down the road if we
     * should find a need to consider only the "whiteboard" form of an Entity.  But, for the current
     * definiton, what this means is that a given Entity implementation may categorically consider
     * all other Entity implementations to be not equal to itself.<p>
     *
     * FODO: reconsider in light of RTWPointerValue equality needs for the Store-based coordination.
     */
    public boolean equals(Object obj);

    /**
     * Hashing predicate inherited from {@link RTWValue}
     */
    public int hashCode();
    
    /**
     * This method returns an identifier which is unique to this Entity within the context of the
     * current instance of the current KB.  The identifier is not guaranteed to be unique across KBs
     * or across different loads of the same KB.  Entities which have no existence on the KB (for
     * example uninstantiated queries) are expected to return 0.  Two entities which share a
     * representation in the KB (for example mirror image beliefs) may share the same identifier.<p>
     * 
     * @return a unique identifier for the entity<p>
     */
    public long getId();

    /**
     * Inherited from {@link RTWValue}<p>
     *
     * As of 2015, things like MATLAB and HFT files share very particular formatting so as to be
     * able to marshall arbitrary composite entities in to and out of relatively human-friendly
     * strings that are very similar to the kind of notation we use on a whiteboard or in papers.
     * It seems to make an overwhelming amount of sense to always use this as a way to be standard
     * and reduce confusion, very much including the construction of logging and error messages.  A
     * standard toString implementation is now part of {@link Entity0Base} and Entity
     * implementations should default to using that.  Its behavior should be replicated carefully
     * when there are speed concerns that drive Entity implementations to want to use direct access
     * to their own internals.<p>
     */
    public String toString();

    /**
     * Obtain the {@link RTWLocation} form of this Entity.<p>
     *
     * Application-level code by and large should not need to use this.  This exists only
     * transitionally as we transition the NELL codebase to Theo2012, and it is expected to be
     * removed.<p>
     */
    public RTWLocation getRTWLocation();  

    /**
     * Returns whether or not there are any explicit beliefs in the KB for the given slot, or any
     * existent belief in the KB that can be constructed by appending additional subslots.<p>
     *
     * In other words this is equivalent to asking whether there is any explicitly stated belief for
     * the query constructed by appending the given slot to this Entity, or whether isEntity is true
     * for any subslot attached to that query.<p>
     *
     * bkdb: is this really a sensible name?  Might as well conduct a review of Parlance method names after trying to make a final decision on the RTWBag ones (i.e. after at least a week of Wedge work in December).  Although, we do need a somewhat unusual name for this since it's not the same as asking whether or not the slot has any values, so maybe this is appropriate afterall, and maybe all we really need is a more complete set of convenience methods (post-Java-8 of course)
     */
    public boolean entityExists(Slot slot);

    /**
     * Convenience version of isEntity that takes the slot as a String
     */
    public boolean entityExists(String slot);

    /**
     * Same as the other isEntity, but poses the question about this entity itself rather than of a
     * slot thereof<p>
     *
     * Notably, this version of isEntity is necessary in order to ask if a primitive entity exists.<p>
     *
     * For Theo0 Entities only: If your Theo0 implementation never constructs Entity objects that
     * don't correspond to existent structures in the KB, then this will trivially return true.<p>
     */
    public boolean entityExists();

    /**
     * Returns the slots attached to this entity in which there exists at least one belief asserted
     * in the KB, or for which there is some subslot or subsubslot etc. in which there is at least
     * one asserted belief.<p>
     *
     * Returning Collection (rather than, say, Iterable) does not preclude an implementation that
     * seeks to avoid building an actual collection of all Slots in RAM.  But, in general, we expect
     * the number of slots to be reasonable to hold in RAM, and so this method is not a relative
     * priority in terms of space efficiency.<p>
     */
    public Collection<Slot> getSlots();

    /**
     * Get access to the set of beliefs in the given slot of this entity via the Query object
     * returned.<p>
     *
     * In Theospeak, if this object is entity E and the given slot is S, then the Query returned
     * will be (E S), and will grant you access to all values V such that the KB asserts belief ((E
     * S) = V).<p>
     *
     * For Theo0 Entities only: This may return null if the resulting Query does not map to any
     * existent structure in the KB.<p>
     *
     * FODO: If we find constructing the Query object to be objectionably inefficient, we can add a
     * getBag method to allow lower-level access to the bag of values.  There were thoughts earlier
     * that we might want to add elaborations here of the sort that RTWBag has, but let's wait and
     * see on that.  It'd be nice to not have an explosive number of methods everywhere.<p>
     */
    public Query getQuery(Slot slot);

    /**
     * Convenience version of getQuery that takes the slot as a String
     */
    public Query getQuery(String slot);
    
    /**
     * Equivalent to getQuery(slot).getBeliefs().
     */
    public Iterable<Belief> getBeliefs(Slot slot);
    
    /**
     * Equivalent to getQuery(slot).getBeliefs(). Convenience version.
     */
    public Iterable<Belief> getBeliefs(String slot);
    
    /**
     * Same as get, but returns the number of beliefs present in the given slot rather than the
     * beliefs themselves, which is generally much faster<p>
     *
     * Testing this against being nonzero makes for an alternative definition of isEntity that might
     * be preferable in some circumstances.  isEntity may return true even when the number of
     * beliefs is zero.<p>
     */
    public int getNumValues(Slot slot);

    /**
     * Convenience version of getNumValues that takes the slot as a String
     */
    public int getNumValues(String slot);

    /**
     * Return set of values referring to this entity through the given slot<p>
     *
     * Consider every belief in the KB "(E' S) = E" where E is this entity.  This will return the
     * set of all E' where S is the given slot.  In other words, this could be thought of as being
     * the inverse of getQuery.<p>
     *
     * This returns an {@link RTWBag} rather than a {@link Query} because the set of values returned
     * might not have a specifiable corresponding location in the KB.  We might change to return
     * some "Theo" class along the lines Entity or Query here in the future, but it doesn't seem
     * well-justified at this point because there is no particular concept in Theo corresponding to
     * this, and there is nothing beyond what RTWBag offers that we want to offer at this time.
     * And, afterall, RTWBag, being a superinterface of Query, is our standard way to access a set
     * of values.<p>
     *
     * {@link RTWBagBase.empty} may be of interest when there is a need to return an empty RTWBag.<p>
     */
    public RTWBag getReferringValues(Slot slot);

    /**
     * Convenience version of getReferringValues that takes the slot as a String<p>
     *
     * {@link RTWBagBase.empty} may be of interest when there is a need to return an empty RTWBag.
     */
    public RTWBag getReferringValues(String slot);
    
    /**
     * Same as getReferringValues, but returns the number of values that would be returned, which is
     * generally much fatser.
     */
    public int getNumReferringValues(Slot slot);

    /**
     * Convenience version of getNumReferringValues that takes the slot as a String
     */
    public int getNumReferringValues(String slot);

    /**
     * Get the slots for which getNumReverringValues would return a non-zero value
     */
    public Collection<Slot> getReferringSlots();
    
    /**
     * Return the collection of Belief instances associated with the given slot.  The slot
     * is expected to be one of those returned by getReferringSlots.<p>
     *
     * This is an alternate to {@link getReferringValues}. Consider every belief in the KB "(E' S) =
     * E" where E is this entity.  This will return an Iterable over those beliefs.<p>
     */ 
    public Iterable<Belief> getReferringBeliefs(Slot slot);
    
    /**
     * Return the collection of Belief instances associated with the given slot.  The slot
     * is expected to be one of those returned by getReferringSlots.  Convenience version.
     */     
    public Iterable<Belief> getReferringBeliefs(String slot);

    /**
     * Convenience method equivalent to getQuery(slot).getBelief(value)<p>
     *
     * For Theo0 Entities only: This may return null if the resulting Belief does not map to any
     * existent structure in the KB.<p>
     */
    public Belief getBelief(Slot slot, RTWValue value);

    /**
     * Convenience version of getBelief that accepts a value as an Object per the rules in the
     * class-level comments, and also accepts either a Slot or a String to designate the slot.
     */
    public Belief getBelief(Object slot, Object value);

    /**
     * Adds the given value to the given slot attached to this Entity.<p>
     *
     * In other words, asserts the belief "(E slot) = value" where E is this Entity.<p>
     *
     * Returns true if and only if the given value was not already present in the given location.
     * If this returns false, then the add was effectivley a no-op.<p>
     */
    public boolean addValue(Slot slot, RTWValue value);

    /**
     * Convenience version of addValue that accepts a value as an Object per the rules in the
     * class-level comments, and also accepts either a Slot or a String to designate the slot.
     */
    public boolean addValue(Object slot, Object value);

    /**
     * Convenience method that does what addValue does, and then returns what getBelief does<p>
     *
     * bkdb: revisit the question of whether to offer abbreviated MATLAB-style method names,
     * e.g. "avagb" for this.<p>
     */
    public Belief addValueAndGetBelief(Slot slot, RTWValue value);

    /**
     * Convenience method that does what addValue does, and then returns what getBelief does<p>
     *
     * bkdb: revisit the question of whether to offer abbreviated MATLAB-style method names,
     * e.g. "avagb" for this.<p>
     */
    public Belief addValueAndGetBelief(Object slot, Object value);
    
    /**
     * Deletes the given value from the given subslot of this Entity, if it exists<p>
     *
     * This will entail recursive deletion of all subslots on the given value, if any exist.  All
     * beliefs elsewhere in the KB referencing this value or any content of any subslot will also be
     * deleted.<p>
     *
     * Returns true if and only if something was deleted.  If this returns false, then the delete
     * was effectively a no-op because the thing being deleted wasn't there to begin with.<p>
     */
    public boolean deleteValue(Slot slot, RTWValue value);

    /**
     * Convenience version of deleteValue that accepts a value as an Object per the rules in the
     * class-level comments, and also accepts either a Slot or a String to designate the slot.
     */
    public boolean deleteValue(Object slot, Object value);

    /**
     * Macro-delete operation that invokes deleteValue for all values in a given slot<p>
     *
     * Returns whether or not anything was deleted.<p>
     */
    public boolean deleteAllValues(Slot slot);

    /**
     * Convenience version of deleteAllValues that takes the slot as a String
     */
    public boolean deleteAllValues(String slot);

    /**
     * Returns whether or not this Entity is a Query
     */
    public boolean isQuery();

    /**
     * Returns this Entity as a Query, or throws an exception if this is not a Query
     */
    public Query toQuery();

    /**
     * Returns whether or not this Entity is a Belief
     */
    public boolean isBelief();

    /**
     * Returns this Entity as a Belief, or throws an exception if this is not a Belief
     */
    public Belief toBelief();

    /**
     * Returns whether or not this Entity is a PrimitiveEntity
     */
    public boolean isPrimitiveEntity();

    /**
     * Returns this Entity as a PrimtiveEntity, or throws an exception if this is not a
     * PrimitiveEntity
     */
    public PrimitiveEntity toPrimitiveEntity();

    /**
     * Returns whether or not this Entity is a Slot
     */
    public boolean isSlot();

    /**
     * Returns this Entity as a Slot, or throws an exception if this is not a Slot
     */
    public Slot toSlot();
}
