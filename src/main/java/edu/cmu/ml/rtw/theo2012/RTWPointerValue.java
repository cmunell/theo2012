package edu.cmu.ml.rtw.theo2012;

import java.util.Collection;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

/**
 * An RTWValue that holds an RTWLocation, making it effectively a "pointer" to some location in the
 * KB
 *
 * One thing an RTWPointerValue does is eliminate the ambiguity in the historical use of
 * RTWStringValue to store the name of an entity as a value in some slot.  By having a separate
 * datatype, we can now distiguish between those slot values that name entities in the KB and those
 * that are arbitrary strings that might or might not have any relation to other KB content.  Not
 * only does this eliminate ambiguity and namespace collision, but also allows for constraining the
 * range of a slot.
 *
 * RTWPointerValue goes a step further by being able to hold not only the name of a top-level /
 * primitive entity but any RTWLocation.  Thus, we now have a way to refer to a contextualized
 * entity, or to a slot in some entity, to a particular value in some slot, a subslot attached to a
 * particular value in some slot, etc.
 *
 * Note that the RTWLocation held in RTWPointerValue instances might not be associated with any
 * particular KB, or might be associated with a KB other than the one you are working with.
 *
 * bkdb 2012-08-27: This is boldly being made into what will become the "hypothetical" Entity class,
 * and is expected to be renamed as such during some final RTWPointerValue cleanup phase.  Copy
 * reasoning over from TheoStore by way of verifying that all the deliberations there can be
 * dispatched and/or deleted.
 *
 * 2013-02-14: We might want to not make this an Entity afterall, now that we have Theo layers
 * wrapping and unwrapping more meticulously.  But I'm saying this without reviewing any of the
 * above because the whole thing is an issue for another day.  It does mean that RTWBag would lack a
 * specific iterator for hitting RTWPointerValues, but maybe that's a sufficiently oddball case that
 * we could ignore it (or give RTWBag a templatized type-returning itertor (which we just about do
 * already considering how SimpleBag implements the type-secific iterators in terms of it)).
 * Presumably, the only far-reaching effect would be that StoreInverselessTheo1 would have to have a
 * beefed up wrapEntity that would check for and wrap an RTWPointerValue.
 *
 * 2014-09: Review of notes and use suggests that the matter of hypothetical Entity class has a
 * serious lack of vision.  Also, this is a poor excuse for a good Parlance style hypothetical
 * soltution.  This looks broadly like a hack.  Furthermore, after the wrapEntity/unwrapEntity fix
 * of early 2013, an instance of this is no longer an acceptable input.  Because it's so unclear
 * what the final fix is here, since we probably can't force everything to be created through a
 * particular KB (analogous to the similarly-unsolved problem of RTWLocation/Store attachment), I'm
 * just going to create through a particular KB as much as possible in the hopes that any true needs
 * for hypothetical will come up after a better, newer vision takes place.
 *
 * Sidenote: I just remembered that at one point we were thinking in terms of having the likes of
 * unwrapEntity automatically take an arbitrary Parlance class and remake it via that KB in
 * question.  What I don't recall is whether we expressly decided against that level of automation,
 * shied away from it for now because of its debugging vlaue, or shied away from it for now in an
 * effort to get the Wedge up and running.
 *
 *
 * HOLDING PEN FOR DOCUMENTATION TAKEN FROM ELSEWHERE
 *
 * {@link Entity}: More interesting is the case of RTWPointerValue.  Using these ought to be as easy as
 * using plain strings, since many Theo beliefs will have the form of referring to another Theo
 * Entity as the value in some slot.  We cannot use String for this purpose because that must more
 * sanely become an RTWStringValue primitive value.  So we accept any Entity object to mean an
 * RTWPointerValue to that Entity.<p>
 *
 * Because our standing rule for storing an RTWPointerValue in a KB is that the thing it points to
 * must exist, we can imagine wanting to create another kind of RTWValue that stores a
 * representation of a Theo entity opaqueley without providing the indexing facilities that
 * RTWPointerValue does.  We would then be faced with a question of how to differentiate between
 * RTWPointerValue and this new RTWValue, and the current thinking is that we would simply have to
 * suffer creating instances explicitly, or perhaps offering an additional set of addValue methods
 * with a diferent interpretation of values that are of type Entity.<p>
 */
public class RTWPointerValue extends Entity1Base implements Entity {
    /**
     * Log sweet log
     */
    private final static Logger log = LogFactory.getLogger();

    /**
     * Developer mode to control stuff like ugly outputs helpful to developers
     */
    protected final boolean developerMode = false;

    /**
     * The location to which we point
     */
    protected final RTWLocation location;

    ////////////////////////////////////////////////////////////////////////////
    // Stuff to implement RTWValue (bkdb FODO: all of which should be absorbd into the Entity side
    // of things down the road
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor
     *
     * It's easy to imagine having other constructors, such as taking the String name of a top-level
     * entity, or mirroring all of RTWLocatin's constructors.  But we might not really want to use a
     * lot of that stuff in practice, so I'm erring on the side of not cluttering everything up with
     * fancy stuff that ultimately just gets in the way.
     */
    public RTWPointerValue(RTWLocation val) {
        super();
        if (val == null)
            throw new RuntimeException("Cannot construct RTWPointerValue from null");

        // Need a way of detaching it from any Store, which is important for our .equals to work
        // right.  RTWPointerValue captures the notion of a location in the abstract, not in
        // relation to any particular Store.  We would also not want anybody to dereference this
        // Location with the expectation that it was attached to any particular Store.
        //
        // bkdb: Might want to add a way to skip construction here if the RTWLocation is already not
        // attached.  Might want to consider an RTWLocation implementation that does not allow
        // itself to be dereferenced; the current RTWLocation class is no abstract and falls through
        // to KbManipulation for backward compatability when it is not attached to a Store.
        location = val;
    }

    @Override public String toString() {
        // bkdb: This winds up in Entity0Base.toString, which then tries to invoke things like
        // toPrimitiveEntity.  This works in the case of e.g. StoreInverselessTheo1.MyEntity, but
        // leads to an exception when things like StringListSuperStore use RTWPointers directly and
        // want to log.  We'll have to get a better fix for this when RTWPointerValue finds its
        // ultimate fate.  But, for now, bounce to RTWLocation's toString if we happen to detect
        // that this is actually an RTWPointerValue.
        if (this.getClass() == RTWPointerValue.class) return location.toString();

        String s = super.toString();
        if (developerMode) s = s + "-RTWPV";
        return s;
    }

    @Override public int hashCode() {
        return location.hashCode();
    }

    // bkdb: will we have to invalidate equality against derived types on grounds of different KB?
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof RTWPointerValue)) return false;
        RTWPointerValue o = (RTWPointerValue)obj;
        return location.equals(o.location);
    }

    @Override public Object clone() {
        try {
            RTWPointerValue p = (RTWPointerValue)super.clone();
            if (true) throw new RuntimeException("bkdb: RTWLocation needs to clone itself because location is final");
            //p.destination = destination;
            return p;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("This exception should not have been thrown", e);
        }
    }

    // bkdb: FODO: we can ideally get rid of this, yes?
    public RTWLocation getDestination() {
        return location;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Stuff to implement Entity
    ////////////////////////////////////////////////////////////////////////////

    /**
     * {@link Entity0Base} uses this to implement the fancier Entity methods
     */
    @Override protected Slot toSlot(String name) {
        // bkdb: or will it just be a free-for-all as with is* and to* below?
        throw new RuntimeException("This Entity does not correspond to any KB");
    }

    /**
     * {@link Entity0Base} needs this to ensure that RTWValues we return that are Entities are
     * RTWPointerValues
     *
     * They certainly ought to be.  There's some kind of internal design error otherwise.  Things
     * are different for higher layers of Theo.  That's why we don't filter our return values
     * through Entity0Base.wrapEntity and the like (TODO: perhaps we should cf. other comments in
     * this class?).
     */
    @Override protected Entity wrapEntity(Entity entity) {
        try {
            if (!(entity instanceof RTWPointerValue))
                throw new RuntimeException("Internal Error: non-RTWPointerValue Entity "
                        + entity.getClass().getName());
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("wrapEntity(" + entity + ")", e);
        }
    }

    @Override public RTWLocation getRTWLocation() {
        return location;
    }

    @Override public boolean entityExists(Slot slot) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public boolean entityExists() {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public Collection<Slot> getSlots() {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public Query getQuery(Slot slot) {
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override public int getNumValues(Slot slot) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public RTWBag getReferringValues(Slot slot) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public int getNumReferringValues(Slot slot) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public Collection<Slot> getReferringSlots() {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public Belief getBelief(Slot slot, RTWValue value) {
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override public boolean addValue(Slot slot, RTWValue value) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public boolean deleteValue(Slot slot, RTWValue value) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override public boolean isQuery() {
        // If this becomes a speed issue, then we could look into having a rule that we never
        // construct an Entity when it is actually a Query.
        if (!location.endsInSlot()) return false;
        if (location.size() <= 1) return false;

        // FODO: So, we need a way to determine contextualization outside of a KB if whiteboard
        // expressions are to be valid, or is there going to be an alternate set of rules that we
        // play by in this case?  bk:context
        return true;
    }        

    @Override public Query toQuery() {
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override public boolean isBelief() {
        // If it doesn't end in a slot, then it ends in an entity reference.  It would therefore
        // be a Belief (assuming well-formedness, i.e. that the last element is preceded by at
        // least a primitive entity and a slot.)
        return !location.endsInSlot();
    }

    @Override public Belief toBelief() {
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override public boolean isPrimitiveEntity() {
        return (location.size() == 1);
    }

    @Override public PrimitiveEntity toPrimitiveEntity() {
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override public boolean isSlot() {
        // FODO: So, we need a way to determine contextualization outside of a KB if whiteboard
        // expressions are to be valid, or is there going to be an alternate set of rules that we
        // play by in this case?  bk:context
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override public Slot toSlot() {
        throw new RuntimeException("bkdb: guess we need a whole compliment, of course");
    }

    @Override
    public Belief addValueAndGetBelief(Slot slot, RTWValue value) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override
    public Iterable<Belief> getBeliefs(Slot slot) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }

    @Override
    public Iterable<Belief> getReferringBeliefs(Slot slot) {
        throw new RuntimeException("This Entity does not belong to any KB");
    }
    
    // We override this because a whole bunch of pre-T2012 code uses this to get the String form of
    // a primitive entity, and the default implementation in Entity0Base will fail because
    // RTWPointerValue doesn't implement a MyPrimitiveEntity class or any such thing becuase it's
    // not clear what the eventual future of RTWPointerValue will be.  So this is a quick workaround
    // we hope to clean up later.
    @Override public String asString() {
        if (isPrimitiveEntity()) return location.getPrimitiveEntity();
        throw new RuntimeException("This backward-comatability method only works for primitive entities.  This entity is "
                + toString());
    }
}