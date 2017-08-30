package edu.cmu.ml.rtw.theo2012.core;

/**
 * The lowest layer of Theo, in fact a "sub-Theo", intended to be the most expedient API to use in
 * order to write new KB storage implementations for which there is not already some other better
 * fit, like {@link Store} or {@link StoreMap}.  See {@link Theo1} for he lowest "real" layer of Theo.
 * <p>
 * This, along with interfaces like {@link Entity} and {@link Query} provide basic Theo constructs
 * along with the notion of "a KB" and operations on it.  Various implementations will have
 * different gaurantees and requirements depending on what constraints they introduce.
 * <p>
 *
 * DESIGN COMMENTARY FOR PEOPLE WHO WANT TO IMPLEMENT THEO0<br>
 * --------------------------------------------------------<p>
 *
 * As a rule, these lower layers of Theo derive more convenience from having a smaller number of
 * methods than from having many "convenience" methods that do the same thing with different
 * configurations of parameters.  Application code isn't going to be using these lower layers
 * anyway, and so adding "convenience" methods is left to some application-code-oriented higher
 * layer.
 * <p>
 * In particular, we're starting out by using RTWLocation as sort-of a least-common-denominator
 * parameter type.  That's about as lightweight as we can go without having separate methods that
 * takes arrays, sequences, etc. of Strings or Objects, and we might wind up having to do that for
 * speed purposes, but that remains to be seen.  This results in an API that might a look a little
 * self-defeating, but things will look different at higher application-facing layers.
 * <p>
 * One of the key design principles here, in following with {@link Store} and {@link RTWLocation} is
 * that we represent the set of beliefs in a given slot through a {@link Query} entity.  More
 * generally, we simply return an {@link Entity} object as a proxy for any "thing" in the KB, just as
 * an RTWLocation can be used to name any kind of "thing" in a Store.  This gives us the same kind
 * of smaller and simpler number of methods that we get from separating the various ways to
 * construct an RTWLocation from the various ways to access the values in it.  This is also
 * important for performance in that a particualr implementation's Entity or Query objects may
 * contain results of computations (perhaps inlcuding expensive KB fetches) that happened during
 * construction so that they need not be replicated on each subsequent access.  For instance, by
 * operating through an Entity object, we potentially can access all of its slots without
 * re-validating, re-resolving, or re-fetching that entity's place in the KB.
 * <p>
 * We omit methods like open, checkpoint, and flush from this interface, at least for now, because we
 * don't yet know how we'll want to generalize these.
 * <p>
 * Theo0 implementations (and objects they create, like Entitys and Queries) are thread-safe only
 * when in read-only mode.  Theo0 implementations are not required to be thread-safe in read-write
 * mode (and indeed this is often desirable to not be thread-safe in order to obtain greater speed
 * in both modes).
 * <p>
 *
 * COMMENTS ON THE USE OF COMPOSITION VS. SUBCLASSING<br>
 * --------------------------------------------------<p>
 *
 * This interface is the same as {@link Theo1}, except that the existence of Theo0 objects (Entity,
 * Query, etc.) is assumed to be equivalent to the existence of corresponding structures in the
 * underlying database used to store the Theo KB.  So, whereas a Theo1 implementation must be able
 * to create and manipulate Theo objects that are "hypothetical", for instance a Query that is not
 * the basis for any known Belief, or some compound Entity formed from slots, entities, beliefs, and
 * whatever else that don't exist, a Theo0 implementation may simply disallow the creation of such
 * objects.  To facilitate this, a Theo0 implementation (and its Entity implementation and
 * subclasses thereof) may elect to return null from methods when there is no return value that
 * would correspond to something that actually exists in the underlying DB.  This produces a target
 * API that is simpler and more direct (vs. Theo1) for writing new KB storage formats.
 * <p>
 * To restate the rule: null must never be returned in place of an Entity (or subclass thereof) that
 * does actually correspond to something that exists in the KB.  Beyond that returning null is
 * optional, but not required.
 * <p>
 * A Theo implementation may be confronted with "illegal" constructions, particularly in read-write
 * mode, from simple things like attempts to create Slot objects with String names that don't name
 * existent primitive entities that descend from the "slot" entity, to composite Entity objects made
 * from other objects that are illegal, out of date or no long existent due to KB modifications, or
 * are Entity objects from some other KB or even other Theo implementation.  And then there is the
 * possibility that the KB itself contains some kind of constraint violation, which may be entirely
 * legitimate on the grounds that there may exist constraints that are not reasonable to enforce on
 * the fly.
 * <p>
 * Ultimately, we are only gauranteed a good and correct KB following a KB "fsck" operation, and so
 * Theo implementations are technically free to be maximally sloppy in the face of questionable
 * inputs, and do anything at all that they want to the KB as a consequence.  But the guideline is
 * that a Theo implementation should not allow a KB write to occur via an Entity that is not
 * well-formed.  So a Theo implementation might allow the user to create and manipulate an Entity
 * that uses an invalid slot or that uses another Entity from a foreign KB as a value, and, as long
 * as an exception is thrown no later than the point at which that Theo implementation attempts to
 * perform a KB modification based on that Entity, then that is in keeping with the guideline.  It
 * is suggested that the same guideline be used for reads, i.e. throw an exception before things get
 * to the point of having to hit the underlying database.  A Theo implementation is welcome to
 * detect these kinds of illegal operations at any time prior to this, but it is not required to do
 * so.  Two of the intended effects of these guidelines are to allow error detection to be
 * implementationally compact, and to allow error detection to err on the side of speed.
 * <p>
 * Another future direction would involve a more monolithic implementation where a single class
 * would cover multiple layers.  In an extreme, we might have a compact and efficient implementation
 * of an inversing Theo store that is directly tied in to the underlying database.  This could cut
 * down significantly on the number of objects constantly being created and destroyed, and could
 * speed up some operations that currently wind up being duplicated in our current setup of having
 * many different layers.  The expectation is that starting out with the layered approach will keep
 * things easier in terms of design and implementation because we are, to a significant extent,
 * defining what Theo is as we are writing these.  We also can expect to have a thorough
 * understanding of what overhead is really important only after getting something functionally
 * correct up and running.
 * <p>
 *
 * COMMENTS ON THE INCLUSION OF LIBRARY METHODS IN THESE INTERFACES<br>
 * ----------------------------------------------------------------<p>
 *
 * Without question, client code will want library functions that consist of more than single
 * primitive operations.  Some will be relatively common operations and others will be increasingly
 * esoteric, and there is likely to be some grey area of library functions that are in practice
 * shared seldom but which have no better place to go.  Generally, I see three options for where in
 * the source code to put these.
 * <p>
 * 1) We could put them in one or more wholly separate classes, e.g a TheoUtility.java.  In this
 * case, we would have to pass in a Theo instance as a parameter, or require that a TheoUtility
 * instance be constructed given a Theo instance to use.  Either way, invoking the method would
 * require invokation upon an instantiated TheoUtility object or prepending the class name in order
 * to invoke static methods.  That's not terrible, but the extra keystrokes really make it better
 * suited for rather high-level methods that are called relatively seldom, whereas we can expect to
 * want to provide a lot of smaller lower-level methods that are used relatively oftne.
 * <p>
 * 2) We could create a new class that composes a Theo instance.  There is a point at which this and
 * #1 could take basically the same form, but the key attribute here is that all of the Theo0
 * interface would be present in this new outer layer.  We might even subclass the interface.  The
 * downside here is that we wind up having to do a lot of tiresome composing to implement it,
 * although the end user wouldn't have to be bothered by that, and we can hope that the compiler
 * would inline everything for speed as needed.
 * <p>
 * 3) We could simply dump these extra library functions right into the Theo0 interface
 * specification, and then provide an abstract base calass that implements them all in terms of a
 * small set of most-primitve operations.  One downside is that we no longer have a sleek, elegant,
 * and relatively very static Theo0 interface.  Changes ought not to perturb Theo0 implementations
 * because corresponding changes would be made to the abstract base, but it would require that
 * library additions that don't have somewhere else to live would entail modification of low level
 * Theo code that library-level developers might not be familiar with or have easy dominion over.
 * Also, lack of support for multiple inheritance means that Theo0 implementations that need to
 * inherit from elsewhere would have to wind up doing some tedious compositional thing, but at least
 * that shouldn't be worse than #2 if and when it does ever happen.  But there are advantages to
 * this approach vs. #1 and #2, such as that each Theo0 implementation retains the ability to
 * selectively override the default implementations of library functions, which opens up potentially
 * significant opportunities to speed up complex operations.
 * <p>
 * Option #3 is a bit unfamiliar to me, so I'm going to go with that for now, lending it the
 * additional benefit of being a learning experience.  If, as Theo2012 takes shape, we find that we
 * ought to break things up into something more like #2, that shouldn't be too big a burden, and
 * code sitting on top of this all ideally wouldn't have to be bothered about it -- we have
 * TheoFactory, afterall.  See {@link Theo0Base}
 * <p>
 * Theo0 implementations can reasonably expect that some higher layer of Theo will be using lazy
 * fetching, and so Theo0 implementations need not complicate themselves with that.  Further
 * guidelines on caching and performance don't yet exist because we're still figuring them out.
 */
public interface Theo0 {
    ////////////////////////////////////////////////////////////////////////////
    // Basic things that need to be implemented
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns whether or not the KB being accessed through this Theo instance is open
     */
    public boolean isOpen();

    /**
     * Returns whether or not the already-open KB being accessed through this Theo instance is open
     * read-only
     */
    public boolean isReadOnly();

    /**
     * Alter whether or not the already-open KB being accessed through this Theo instance is open
     * read-only<p>
     *
     * One would reasonably expect read-only mode to be faster.<p>
     */
    public void setReadOnly(boolean makeReadOnly);

    /**
     * Close this Theo instance.<p>
     *
     * For the time being, closure is permanent; reopening requires creating a new Theo instance
     * through {@link TheoFactory}.<p>
     */
    public void close();

    /**
     * Convenience version of get that takes a single String denoting a primitive entity<p>
     *
     * This may throw an exception if the given String does not correspond to an existent primitive
     * entity.  But, per the requirements outline in the class-level comments, no such error
     * checking need be done here, the guideline being that it needn't happen until the point of
     * using the resulting Slot object in the course of a read or write to the KB.<p>
     *
     * FODO: is there a category of these that should be moved out to a higher layer afterall?  For
     * instance, is there really justification for accepting java primitives here for speed<p>
     * purposes?
     *
     * bkdb: rename?<p>
     */
    public PrimitiveEntity get(String primitiveEntity);

    /**
     * Convenience version of get that takes a single String denoting a slot entity<p>
     *
     * This may throw an exception if the given String does not correspond to an existent primitive
     * entity that is a slot.  But, per the requirements outline in the class-level comments, no
     * such error checking need be done here, the guideline being that it needn't happen until the
     * point of using the resulting Slot object in the course of a read or write to the KB.<p>
     *
     * bkdb: commentary on why this is not in Theo0Base if remains not there<p>
     */
    public Slot getSlot(String slotName);

    /**
     * Creates and returns a new primitive entity of the given name that generalizes to the given
     * entity.<p>
     *
     * If an entity by this name already exists, then this reduces to adding the given
     * generalization.  That, then, reduces to a no-op if the entity already has that
     * generalization.<p>
     *
     * If the given generalization is not a primitive entity or is otherwise unsuitable for being
     * used as a generalization, an exception will be thrown.<p>
     */
    public PrimitiveEntity createPrimitiveEntity(String name, Entity generalization);


    ////////////////////////////////////////////////////////////////////////////
    // Things implemented by Theo0Base.java
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns an Entity object corresponding to the given location, through which the caller may
     * interact with the KB.<p>
     *
     * Application-level code should avoid anything involving RTWLocation.  We expect to ultimately
     * remove everything to do with RTWLocation.<p>
     *
     * This may return null if the given RTWLocation does not correspond to something that exists in
     * the underlying database.<p>
     *
     * Most of the methods available from Entity take a slot name as a parameter, making it
     * unnecessary to obtain an intermediate Query object corresponding to (Entity, slot).  We may
     * decide to add a getQuery and/or getBelief method to Theo0 if it turns out that this would be
     * useful for brevity or speed, although perhaps that would best be done in some superficial
     * subclass or composition or higher layer of Theo so as to keep the various lower-level KB
     * classes simpler.<p>
     *
     * We may also decide that this ought to return a Query or other Entity subclass in the case
     * that the given location can be identified as something more specific.<p>
     *
     * bkdb TODO: Tom votes for getEntity here.  Let's see what other constructors we wind up with
     * in the course of writing Wedge; cf. Deliberations & RTWLocation's constructors.<p>
     */
    public Entity get(RTWLocation location);

    /**
     * Returns an Entity object from this KB matching the given Entity, which may be from any (or
     * no) KB.
     *
     * All terms and conditions of the other get method apply.
     *
     * TODO: rename to getEntity if the other get changes.
     */
    //bkdb: get rid of this unless OM winds up needing it: who uses this public Entity get(Entity entity);

    /**
     * Returns the named primitive entity if it already exists in the KB; otherwise returns null.<p>
     */
    public PrimitiveEntity getPrimitiveEntity(String primNameString);
    
    /**
     * Creates and returns a new slot entity, optionally creating and setting up an inverse slave
     * for it.<p>
     *
     * The generalization parameter may be left null as shorthand to indicate that this slot should
     * simply generalize to the "slot" entity.<p>
     *
     * The inverse parameter may be left null if a slave inverse is not desired.  The
     * generalizations of the slave inverse will be set to the inverses of the generalizations of
     * the master (or the generalizations themselves, for those that do not have inverses).<p>
     *
     * For implementational simpilcity, the master and slave entities will be deleted first if they
     * already exist.<p>
     *
     * FODO: not strictly necessary to require Theo0s to provide inversing, but not going to
     * refactor for that until after Wedge at least.<p>
     */
    public Slot createSlot(String master, Slot generalization, String inverseSlave);

    /**
     * Rercursively deletes the entire entity<p>
     *
     * Returns true iff something was deleted<p>
     */
    public boolean deleteEntity(Entity entity);

    /**
     * Write a GraphViz 'dot' format file representing the database.  If title is
     * non-null, it appears as a label for the graph.  If startEntity is non-null,
     * only nodes which can be reached via paths other than 'specialization' from
     * the given node will be included in the graph.<p>
     *
     * bkdb FODO: this should almost certainly move out ot a utilities class of some sort<p>
     */
    public void writeDot( String fileName, String title, Entity startEntity);

    /**
     * Undoes RTWValue.toString() to recover a Java Entity object from a (correctly formated)
     * string<p>
     *
     * This is the real guts of src/matlab/Theo/code/Theo2012/string2java.m.  It's also what we use
     * for HFT files.<p>
     *
     * {@link Theo0Base} contains the standard implementation for this, and this should in general
     * not be overridden.<p>
     */
    public RTWValue valueFromString(String str);

}