package edu.cmu.ml.rtw.theo2012;

import java.util.Map;
import java.util.List;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

/**
 * Extends {@link StringListSTore} with extra hidden indexing to make it a {@link SuperStore}.
 *
 * Extra indexing will come into play through additional slots that start with a space character.
 * Say we have the following written to the store:
 *
 * 1990
 *   Bob
 *     livesIn = <Tokyo>
 *       =<Tokyo>
 *         accordingTo = <Mary>
 *
 * Here, 1990 is a context, and we have the assertion that, within this context, Bob lives in Tokyo,
 * and the fact that Bob lives in Tokyo is according to Mary.  Here, The Tokyo and Mary values are
 * of type RTWPointerValue.  What this class will do, then, is to create the following additional
 * entries.  Note: we will use an underscore prefix here for clarity, but the actual prefix used
 * will be a space character.
 *
 * Tokyo
 *   _P
 *     livesIn = <1990, Bob>
 *
 * Mary
 *   _P
 *     accordingTo = <1990, Bob, livesIn, =<Tokyo>>
 *
 * Thusly, the set of RTWPointerValue elements in <Mary, _P, accordingTo> is exactly that set of
 * RTWPointerValue elements that need to be returned in response invoking getPointer(<Mary>,
 * accordingTo).  Here we can efficiently filter the pointers by slot because they are partitioned
 * by the name of the slot containing the referent.
 *
 * The number of values in the subslots of _P will be of greater dimension than the slots those
 * pointers point to because they're not recursively nested in the way that contexts and subslots
 * are.  It's not clear that there would be an advantage to that extra indexing, and packing
 * everything into a single slot (potentially) reduces the number of Store fetches.
 *
 * In the case of RTWPointerValues to composite entities, our layout extends in a relatively natural
 * fashion.  Here, we consider an assertion of the form (Belief Slot) = (Belief), and another of the
 * form (Belief Slot) = (Query).  Note that we must store some value in our Query (Japan, onFire),
 * or in some subslot beneath that Query in order to allow an RTWPointerValue to be stored there.
 * In this example, we satisfy the requirement by stating the belief that our Query is easilySolved.
 *
 * 1990
 *   Bob
 *     livesIn = <Tokyo>
 *       =<Tokyo>
 *         causes = <Gojira, attacks, =<Japan>>
 *
 * Gojira
 *   attacks = <Japan>
 *     =<Japan>
 *       raisesQuestion = <Japan, onFire>
 *
 * Japan
 *   onFire
 *     easilySolved = true
 *
 * Then, in addition to the extra indexing for the Tokyo entity per our earlier example, we would
 * wind up with a Gojira and Japan entities that looks like so:
 *
 * Gojira
 *   attacks = <Japan>
 *     =<Japan>
 *       raisesQuestion = <Japan, onFire>
 *       _P
 *         causes = <1990, Bob, livesIn, =<Tokyo>>
 *
 * Japan
 *   onFire
 *     easilySolved = true
 *     _P
 *       raisesQuestion = <Gojira, attacks, =<Japan>>
 *   _P
 *     attacks = <Gojira>
 *     
 * Note that we do not need to add additional indexing for RTWPointerValues that are stored in our
 * hidden _P subslot.  This is because those RTWPointerValues will implicitly be deleted in the
 * course of deleting the things to which they refer.  For instance, deleting the Japan entity will
 * entail the deletion of the <Gojira, attacks, =<Japan>> value, and that, in turn, will entail the
 * deletion of the raisesQuestion subslot attached to that value, and that, in turn, will entail the
 * deletion of the <Japan, onFire, _P, raisesQuestion> slot.  Thus, we did not need any additional
 * indexing to ensure the deletion of the <Gojira, attacks, =<Japan>> value in that slot.
 * 
 * TODO: might should filter gets, adds, and deletes.
 *
 * Originally, I wanted to make this a generic {@link SuperStore} implementation that would add
 * extra hidden slots to any {@link Store} implementation.  One of the things we'd have to do in
 * that case is worry about how to coordinate on metacharacters or whatever else in order to achieve
 * hidden slots.  But what broke the camel's back was the need to trap all of the "primitive"
 * deletes of individual values from the complex recursive delete, and probably similarly for other
 * recursive operations like the copy and the rename.  That goes too far outside the needs of our
 * short-term goals, and I think it might anyway better be tackled during a later revision when we
 * have Theo2012 up, running, better-understood, and more experience with Stores other than
 * StringListStore
 */
public class StringListSuperStore<SLSM extends StringListStoreMap> extends StringListStore<SLSM> implements SuperStore { 
    private final static Logger log = LogFactory.getLogger();

    /**
     * The name of our " pointers" slot
     *
     * We may as well follow StringListStore's trend of using single-character indicators for its metadata
     * slots.  It certainly saves space.
     *
     * Note: we use RTWLocation.append rather than RTWLocation.sublot with this because
     * RTWLocation.subslot forces slot names to all-lowercase.  // bk:lowercase
     */
    protected final String pointerSlotName = " P";
    protected final RTWStringValue pointerSlotNameValue = new RTWStringValue(pointerSlotName);

    /**
     * Subclass of RTWBag that we use to return from getPointers
     *
     * Technically, we could just return a plain RTWLocation to our hidden pointers slot, but then
     * the outside world would gain undue access to the particulars of our hidden pointers slots and
     * their structure, and could go so far as to write in or around them.  So this just hides a
     * {@link TCHRTWLocation} by placing it in an {@link RTWBag} impelementation that only forwards
     * the bag-related methods to TCHRTWLocation.
     *
     * This way, too, we can make our toString return something more informative.  And have some
     * custom informataive exceptions for attempts to use this as other than a bag of
     * RTWPointerValues.
     */
    protected class PointersBag extends RTWBagBase implements RTWBag {
        /**
         * The hidden pointers slot that we represent.
         *
         * Never null.
         */
        protected final RTWLocation loc;

        public PointersBag(RTWLocation pointersSlot) {
            loc = pointersSlot;
        }

        @Override public int getNumValues() {
            return loc.getNumValues();
        }

        @Override public String valueDump() {
            return loc.valueDump();
        }

        @Override public int hashCode() {
            return loc.hashCode();
        }

        @Override public boolean equals(Object obj) {
            return loc.equals(obj);
        }

        @Override public String toString() {
            // This is more computationally heavy than having our toString value set on
            // construction, but we expect that the common case is that our toString never gets
            // called, and so we save the time and space of doing that upfront by paying the bigger
            // price here instead.

            String slot = loc.lastAsSlot();
            String referent = loc.parent().parent().toString();
            return "<Pointers to " + referent + " through the \"" + slot + "\" slot>";
        }

        @Override public boolean isEmpty() {
            return loc.isEmpty();
        }

        @Override public boolean has1Value() {
            return loc.has1Value();
        }

        @Override public boolean has1String() {
            // The result of getPointers is always only RTWPointerValues
            return false;
        }

        @Override public boolean has1Integer() {
            // The result of getPointers is always only RTWPointerValues
            return false;
        }

        @Override public boolean has1Double() {
            // The result of getPointers is always only RTWPointerValues
            return false;
        }

        @Override public boolean has1Boolean() {
            // The result of getPointers is always only RTWPointerValues
            return false;
        }

        @Override public boolean has1Entity() {
            return loc.has1Entity();
        }
        
        @Override public Iterable<RTWValue> iter() {
            return loc.iter();
        }

        @Override public Iterable<RTWBooleanValue> booleanIter() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Iterable<RTWDoubleValue> doubleIter() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Iterable<RTWIntegerValue> integerIter() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Iterable<RTWStringValue> stringIter() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Iterable<Entity> entityIter() {
            return loc.entityIter();
        }

        @Override public RTWValue into1Value() {
            return loc.into1Value();
        }

        @Override public String into1String() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Integer into1Integer() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Double into1Double() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Boolean into1Boolean() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Entity into1Entity() {
            return loc.into1Entity();
        }

        @Override public RTWValue need1Value() {
            return loc.need1Value();
        }

        @Override public boolean need1Boolean() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public Entity need1Entity() {
            return loc.need1Entity();
        }

        @Override public double need1Double() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public int need1Integer() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public String need1String() {
            throw new RuntimeException("The result of getPointers is always only RTWPointerValues");
        }

        @Override public boolean containsValue(RTWValue v) {
            return loc.containsValue(v);
        }
    }

    /**
     * Given the destination of an RTWPointerValue, verifies that it refers to something existent
     * per the rules for storing RTWPointerValues in SuperStores, including a recursive verification
     * of any RTWPointerValues present within the destination.
     *
     * If the given destination does not constitue a valid RTWPointerValue, an exception will be
     * thrown.
     *
     * FODO: I wonder if this sort of thing is going to wind up being a real performance drag.  I
     * suppose the Parlance classes would provide a facility for marking validated RTWPointerValues,
     * but the whole dance of maintaining that in read/write mode, where it validation is important,
     * is as yet unclear.  Or maybe this is just the sort of thing we'll opt to (conditionally) turn
     * off on the assumption that the calling code is sufficiently well vetted, and leave any
     * consequent corruption to a future fsck kind of operation
     */
    protected void validateRTWPointerValue(RTWLocation destination) {
        try {
            // We'll do the recursive part first.  This could result in slower failures, but
            // failures are not our common case.  Doing the recursive part first ensures that we get
            // an error message that clearly indicates that some nested thing is the root of the
            // problem.  Were we do do the existence check of our given destination first, the
            // underlying Store implementation might come up with some error of its own that doesn't
            // fully explain the nature of our larger situation here.

            // As a minor optimization, we'll assume that the
            // first element is not an RTWElementRef, because that would be an illegitimate
            // construction.  If it does happen to be one, the illegitimacy is sure to be caught
            // elsewhere at some point (e.g. in Store).
            for (int i = 1; i < destination.size(); i++) {
                if (!destination.isSlot(i)) {
                    RTWValue v = destination.getAsValue(i);
                    if (v instanceof RTWPointerValue)
                        validateRTWPointerValue(((RTWPointerValue)v).getDestination());
                }
            }

            // Now check that the destination exists
            if (destination.endsInSlot()) {
                if (super.getSubslots(destination) == null)
                    throw new RuntimeException("Error: Referent "
                            + destination + " of given RTWPointerValue does not exist");
            } else {
                // bkdb: can we do better about knowing whether or not the location is already attached?  Or will this in general be enough of a no-op that we can ignore the issue?  I suppose the thing to be concerned about is wanting to ensure that we wind up attaching an RTWLocation at most once in the entire course of things.

                // bkisiel 2013-02-13: we used to unconditionally invoke isEmpty here, but I'm
                // trying to find out if anybody else needs/wants to use isEmpty on a location not
                // ending in a slot, so I've changed that to throw an exception in SLSRTWLocation.
                // And so we need to check contains ourselves when the location doesnt end in a
                // slot.
                if (destination.endsInSlot()) {
                    if (super.getLoc(destination).isEmpty())
                        throw new RuntimeException("Error: Referent "
                                + destination + " of given RTWPointerValue does not exist");
                } else {
                    if (!destination.parent().containsValue(destination.lastAsValue()))
                        throw new RuntimeException("Error: Referent "
                                + destination + " of given RTWPointerValue does not exist");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("validateRTWPointerValue(" + destination + ")", e);
        }
    }
    
    @Override protected void deleteValue(RTWLocation location, RTWValue value) {
        try {
            // log.debug("bkdb: SLSS deleteValue(" + location + ", " + value + ")");

            // For our delete code, we'll use the running example of deleting <Japan> value from the
            // <Gojira, attacks> slot.

            // In our example KB, there are RTWPointerValues that point to the entire assertion
            // <Gojira attacks =<Japan>>, not just to <Japan>.  These RTWPointer values need to be
            // deleted as well, since deleting the <Japan> value deletes the assertion.  Any such
            // beasts will be conveniently tracked in slots beneath the <Gojira, attacks, =<Japan>,
            // _P> slot.  We take care of these first so that this _P subslot is gone before
            // StringListStore deletes the <Japan> element.  Put another way, deleting <Japan>
            // conceptually entails deleting all subslots attached to it, so naturally we should not
            // concieve to be able to process this _P slot after <Japan> has been deleted.

            // Just to give each kind of thing its own name, we'll call things stored under this _P
            // slot "assertion pointers" here.
            RTWLocation wholeLocation = getLoc(location.element(value)); //bkdb: the dance of location attachment
            RTWLocation assertionPointersSlot = wholeLocation.append(pointerSlotName);
            
            // In our example, the "subslot" variable will be causes, and p will be the value in
            // there, <1990, Bob, livesIn, =<Tokyo>>.  That tells us that there is a slot <1990,
            // Bob, livesIn, =<Tokyo>, causes> slot that has an RTWPointerValue <Gojira attacks
            // =<Japan>> in it, and we have to delete that because we're going to go on to delete
            // <Gojira attacks =<Japan>>.
            //
            // When we we recurse into this deleteValue to do that, it will delete the corresponding
            // value p from v.  That means two things: 1. we do not ourselves have to delete
            // anything under assertionPointersSlot; 2. we're going to be looping over things that
            // get deleted, so use use an iterative refetch-and-delete-next-element approach to keep
            // the iterators from getting tripped up.
            RTWPointerValue wholeBelief = new RTWPointerValue(wholeLocation);  // FODO: requires deep equality with RTWPointerValue
            while (true) {
                RTWListValue subslots = super.getSubslots(assertionPointersSlot);
                // log.debug("bkdb: subslots of " + assertionPointersSlot + " are " + subslots);
                if (subslots == null) break;
                String subslot = subslots.get(0).asString();
                RTWLocation s = assertionPointersSlot.subslot(subslot);
                RTWPointerValue p = (RTWPointerValue)s.iter().iterator().next();
                
                // When we delete this value, we might get a signalDeleteSlot from StringListStore.
                // The one to do with the stuff in our hidden pointer slots will be ignored.  The
                // ones to do with references to <Gojira, attacks, =<Japan>> could well go on to
                // cause an avalance of deletes from pointers to that pointer.  One more reason for
                // using refetch-and-delete-next-element everywhere.
                log.debug("bkdb: recurse to delete " + wholeBelief + " from "  + p.getDestination().subslot(subslot));
                deleteValue(p.getDestination().subslot(subslot), wholeBelief);
            }

            // OK.  When assertionPointerSlot has no more subslots, that means that there are no
            // more RTWPointerValues out there pointing to the belief we're about to delete by way
            // of deleting the value.
            //
            // Now, if this is the only value in the slot, then we also have to worry about
            // RTWPointerValues pointing to that slot.  But we don't have to worry about that here.
            // We trap events of that nature in signalDeleteSlot below.
            //
            // Similarly, it is possible that, if our delete causes that slot to be deleted, that
            // the entity having that slot gets deleted as a consequence.  In that case, we'd have
            // to delete all the RTWPointerValues pointing to or otherwise involving that entity.
            // That, too, is handled in signalDeleteSlot.

            // Finally, we handle the case that the value we're about to delete is itself an
            // RTWPointerValue, in which case we have to undo what the original call to add did by
            // adding a hidden backpointer.  In our running example, our value is <Japan>, and the
            // hidden backpointer we have to pre-delete is <Japan, _P, attacks>.  When we delete
            // that value, StringListStore will invoke signalDeleteSlot a couple of times, and those
            // will be no-ops.
            if (value instanceof RTWPointerValue) {
                RTWPointerValue p = (RTWPointerValue)value;
                RTWLocation destination = p.getDestination();

                String slot = location.lastAsSlot();
                RTWLocation pointerSlot = destination.append(pointerSlotName).subslot(slot);

                // Note that deleteValue requires that we do an existence check.  Note also that we
                // know location must have a parent because top-level entities cannot be used as
                // slots.
                RTWValue pointerValue = new RTWPointerValue(location.parent());
                log.debug("bkdb: now we will delete hidden pointer " + pointerValue + " from " + pointerSlot);
                if (!super.getLoc(pointerSlot).containsValue(pointerValue))  //bkdb: the dance of location attachment
                    throw new RuntimeException("Missing hidden backpointer " + pointerValue
                            + " in " + pointerSlot + ".  Existent backpointers are: "
                            + super.getLoc(pointerSlot).valueDump());
                super.deleteValue(pointerSlot, new RTWPointerValue(location.parent()));
            }

            // And finally we can perform the actual delete we came here to perform.  No existence
            // check here becuase we ourselves require that our caller did it.
            super.deleteValue(location, value);
        } catch (Exception e) {
            throw new RuntimeException("deleteValue(" + location + ", " + value + ")", e);
        }
    }

    @Override protected void signalDeleteSlot(RTWLocation location) {
        try {
            // log.debug("bkdb:signalDeleteSlot for " + location);

            // Read the comments inside deleteValue first.

            // Basically, what we have to do is check for and delete any RTWPointerValue values that
            // are currently pointing at the slot that just got deleted.  And then, because
            // top-level entities are not themselves slots for the purposes of this method, we also
            // check to see if the top-level entity has consequently been deleted.

            // This is pretty straightforward.  Using the Gojira example in our class-level
            // comments, if the <Japan, onFire, easilySolved> slot were to be deleted, then we'd
            // simply look under the <Japan, onFire, easilySolved, _P> to find all the
            // RTWPointerValues to delete.  Similarly, if the top-level entity Japan were deleted,
            // then we'd look under <Japan, _P>.  We would not, in this second case, also have to
            // worry about <Japan, onFire, easilySolved, _P> because the recursive nature of
            // StringListStore.delete would have already specifically deleted all of the other
            // content in the Japan entity.
            //
            // As with the similar case in our deleteEntity, we do not have to worry about deleting
            // the values under the hidden pointers slots because they will be automatically delted
            // when we delete the actual RTWPointerValues that they point back to.  And we need to
            // use the same kind of refetch-and-delete-next-element approach because we're iterating
            // over what's being deleted (and for saftey in the face of wild recursive deletes)

            // OK, so first look for hidden pointers slot under the given slot.  This is basically
            // cut and paste from deleteValue, right down to the variable naming, except that
            // comments are omitted for brevity.
            RTWLocation assertionPointersSlot = location.append(pointerSlotName);
            RTWPointerValue wholeBelief = new RTWPointerValue(location);
            while (true) {
                RTWListValue subslots = super.getSubslots(assertionPointersSlot);
                if (subslots == null) break;
                String subslot = subslots.get(0).asString();
                RTWLocation s = assertionPointersSlot.subslot(subslot);
                RTWPointerValue p = (RTWPointerValue)s.iter().iterator().next();
                log.debug("bkdb: recurse to delete " + wholeBelief + " from "  + p.getDestination().subslot(subslot));
                deleteValue(p.getDestination().subslot(subslot), wholeBelief);
            }

            // And now check to see if this is a slot of a top-level entity, and, if so, if that
            // top-level entity has been deleted.
            if (location.size() == 2) {
                RTWLocation entity = location.parent();
                if (getSubslots(entity) == null) {
                    log.debug("bkdb: Detected delete of " + entity);
                    assertionPointersSlot = entity.append(pointerSlotName);
                    wholeBelief = new RTWPointerValue(entity);
                    while (true) {
                        RTWListValue subslots = super.getSubslots(assertionPointersSlot);
                        if (subslots == null) break;
                        String subslot = subslots.get(0).asString();
                        RTWLocation s = getLoc(assertionPointersSlot.subslot(subslot));  // location store attachment
                        RTWPointerValue p = (RTWPointerValue)s.iter().iterator().next();
                        log.debug("bkdb: recurse to delete " + wholeBelief + " from "  + p.getDestination().subslot(subslot));
                        deleteValue(p.getDestination().subslot(subslot), wholeBelief);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("signalDeleteSlot(" + location + ")", e);
        }
    }

    /**
     * Construct a new StringListSuperStore
     */
    public StringListSuperStore(SLSM slsm) {
        super(slsm);
    }

    @Override public void open(String filename, boolean openInReadOnlyMode) {
        // bkdb TODO: the existence of this is enough to need to standardize IDing within the Store
        // so that others know that this is a StringListSuperStore and that StringListStore should
        // not open it except readonly, and that we know it wasn't a different SuperStore wrapper
        // that made it, etc.  Of course, this will have to somehow authorize StringListStore to use it
        // etc.  Let's come back to this around the same time as indicating constraints.  (And maybe
        // this could be a kind of constraint, and our fsck would find/fix pointer errors.)
        //
        // If "magic bytes" to identify things is a legit approach, then we could have a set of
        // "magic subslots" in a canonical location to encapsulate places to put additional
        // information relevant to each "thing"'s magic.
        super.open(filename, openInReadOnlyMode);
    }

    @Override public RTWListValue getSubslots(RTWLocation location) {
        // Here we have to remove the appearence of our hidden subslot from everyone else's view,
        // including StringListStore's, as described below.  It could perhaps be faster to write an
        // RTWListValue subclass that would pretend to not be holding a pointer subslot, but we'll
        // start with something implementationally easier: substitute an RTWListValue with the
        // offending element spliced out.

        // Why it's OK to hide our subslot from StringListStore:
        //
        // When doing a subslot delete where the subslot is attached to a value, our getSubslots
        // implementation will hide from StringListStore the fact that there may be a hidden
        // pointers subslot, but our overridden deleteValue implementation will remove that subslot
        // before it falls through to StringListStore's deleteValue to actually delete the value, so
        // no more subslots on that value will exist by the time that value is deleteted.
        // Therefore, it's OK to hide the pointers subslot on this case.
        //
        // When doing a subslot delete, it's similarly safe to filter StringListStore's view of
        // getSubslots because we'll pre-delete any pointers subslot in our overridden
        // implementation of deleteSlot
        //
        // cullEmptyEntries also need not see the pointers slot because that alone is no reason to
        // stop anything from being deleted; the pointers slot is only needed to delete references
        // to things that already have other reasons to be deleted.
        //
        // When doing copySubslotsRecursively, we do want to hide the pointers slot because we'll be
        // auto-adding to it when copySubslotsRecursively invokes our overridden add.
        //
        // renameValues should not see our hidden pointers subslot, either, by transitivity via the
        // methods it uses to do adds and deletes.

        RTWListValue orig = super.getSubslots(location);
        if (orig == null) return orig;

        int index = orig.indexOf(pointerSlotNameValue);
        if (index < 0) return orig;

        // Special case if the only truley existent subslot is our hidden pointers subslot.
        if (orig.size() == 1) return null;

        RTWListValue substitute = new RTWArrayListValue(orig.size()-1);
        for (int i = 0; i < orig.size(); i++) {
            if (i == index) continue;
            substitute.add(orig.get(i));
        }
        return substitute;
    }

    @Override public boolean add(RTWLocation location, RTWValue value) {
        try {
            boolean wasNew = super.add(location, value);
            
            // Here we need to add a hidden pointer back to the value we added if it wasn't there
            // already
            if (wasNew && value instanceof RTWPointerValue) {
                RTWPointerValue p = (RTWPointerValue) value;
                RTWLocation destination = p.getDestination();

                // Make sure the destination exists etc.
                validateRTWPointerValue(destination);
                
                // Now come up with the hidden pointers slot and add this location as to indicate
                // there there is some value.  location necessarily ends in a slot because that's
                // the only kind of a place we can add a value to.
                String slot = location.lastAsSlot();
                RTWLocation pointerSlot = destination.append(pointerSlotName).subslot(slot);

                // Note that we know location must have a parent because top-level entities
                // cannot be used as slots.
                super.add(pointerSlot, new RTWPointerValue(location.parent()));
            }

            return wasNew;
        } catch (Exception e) {
            throw new RuntimeException("add(" + location + ", " + value + ")", e);
        }
    }

    // Don't need to override copySubslotsRecursively because it's only going to call add, which
    // we've already overridden to take care of maintaining our pointers slots.

    // Don't need to override renameValues because it's only going to call add, delete,
    // copySubslotsRecursively, etc., which will do all the necessary bookeeping with our pointers
    // slots.
    //
    // Note that renameValues is not responsible for changing values in slots to point to the new
    // entities.  That happens in RTWRenameCommand.java.  That whole system will have to be retooled
    // when we switch to using RTWPointerValues as slot values, but, in terms of staying
    // backward-comaptable to renaming Strings, our reasonings here are legitimate and sufficient.

    @Override public RTWBag getPointers(RTWLocation referent, String slot) {
        try {
            // Conveniently, our hidden pointers slot is exactly the bag of value we want to return.
            // Also conveniently, if an illegitimate referent is given, then StringListStore will
            // either hand us back a null or throw an exception about it
            return new PointersBag(super.getLoc(referent.append(pointerSlotName).subslot(slot)));
        } catch (Exception e) {
            throw new RuntimeException("getPointer(" + referent + ", \"" + slot + "\")", e);
        }
    }

    
    @Override public RTWListValue getPointingSlots(RTWLocation referent) {
        try {
            // Similarly convenient
            return super.getSubslots(referent.append(pointerSlotName));
        } catch (Exception e) {
            throw new RuntimeException("getPointingSlots(" + referent + ")", e);
        }
    }

    /**
     * Testing fixture
     *
     * bkdb TODO: move things like this elsewhere or at least remove dependencies on things like KbMLocation
     */
    public static void main(String args[]) throws Exception {
        try {
            String filename = args[0];
            StringListSuperStore store = new StringListSuperStore(new MapDBStoreMap());
            store.open(filename, false);

            String cmd = args[1];
            String locstr = args[2];
            RTWLocation loc = new AbstractRTWLocation();
            for (String s : locstr.split(" ")) {
                if (s.charAt(0) == '@') loc = loc.element(new RTWPointerValue(store.getLoc(s.substring(1))));
                else loc = loc.subslot(s);
            }
            System.out.println("For location " + loc);

            if (cmd.equals("get")) {
                RTWLocation l = store.getLoc(loc);
                System.out.println(l + ": " + l.valueDump());
            } else if (cmd.equals("getss")) {
                System.out.println(store.getSubslots(loc));
            } else if (cmd.equals("add")) {
                String val = args[3];
                RTWValue value;
                if (val.charAt(0) == '@')
                    value = new RTWPointerValue(store.getLoc(val.substring(1)));
                else
                    value = new RTWStringValue(val);
                store.add(loc, value);
                RTWLocation l = store.getLoc(loc);
                System.out.println(l + ": " + l.valueDump());
            } else if (cmd.equals("delete")) {
                boolean recursive = args[3].equals("r");
                store.delete(loc, true, recursive);
                if (!loc.endsInSlot()) loc = loc.parent();
                RTWLocation l = store.getLoc(loc);
                System.out.println(l + ": " + l.valueDump());
            } else if (cmd.equals("rename")) {
                throw new RuntimeException("nah, test this with an RTWRenameCommand via KBfromTextfile");
            } else if (cmd.equals("getpointers")) {
                String slot = args[3];
                RTWBag pointers = store.getPointers(loc, slot);
                System.out.println(pointers + ": " + pointers.valueDump());
            } else if (cmd.equals("gojira")) {
                // Set up the Gojira example from the class-level comments (less the 1990 context,
                // just to simplify slightly).  We also add generalizations to avoid a chicken-egg
                // problem when both the arg1 and arg2 of the relation don't yet exist.
                store.add(store.getLoc("everything", "dummyslot"), new RTWStringValue("dummyvalue"));
                RTWValue everything = new RTWPointerValue(store.getLoc("everything"));
                RTWValue bob = new RTWPointerValue(store.getLoc("bob"));
                RTWValue tokyo = new RTWPointerValue(store.getLoc("tokyo"));
                RTWValue gojira = new RTWPointerValue(store.getLoc("gojira"));
                RTWValue japan = new RTWPointerValue(store.getLoc("japan"));
                store.add(store.getLoc("bob", "generalizations"), everything);
                store.add(store.getLoc("tokyo", "generalizations"), everything);
                store.add(store.getLoc("gojira", "generalizations"), everything);
                store.add(store.getLoc("japan", "generalizations"), everything);

                store.add(store.getLoc("bob", "livesin"), tokyo);

                store.add(store.getLoc("gojira", "attacks"), japan);

                RTWValue theAttack = new RTWPointerValue(store.getLoc("gojira", "attacks").element(japan));
                store.add(store.getLoc("bob", "livesin").element(tokyo).subslot("causes"), theAttack);

                store.add(store.getLoc("japan", "onfire", "easilysolved"), new RTWStringValue("true"));

                RTWValue onFire = new RTWPointerValue(store.getLoc("japan", "onfire"));
                store.add(store.getLoc("gojira", "attacks").element(japan).subslot("raisesquestion"), onFire);
            } else {
                System.out.println("Unrecognized command");
            }

            store.close();
        } catch (Exception e) {
            log.fatal("Uncaught exception", e);
            System.exit(2);
        }
    }
}

