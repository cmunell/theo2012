package edu.cmu.ml.rtw.theo2012.core;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Pair;
import edu.cmu.ml.rtw.util.Properties;

/**
 * Uses a {@link SuperStore} to underpin a {@link Theo1} implementation that does not do any
 * automatic updating of a slot's inverse when modifying a slot's content.
 *
 * This is written with {@link PointerInversingTheo1} in mind.  A PointerInversingTheo1 object uses
 * a StoreInverselessTheo1 object to produce a Theo1 implementation that maintains a KB in such a
 * way that adding or removing values from a slot will add or remove values from its inverse as
 * appropriate.  See PointerInversingTheo1 for further details on this.  It is not expected that
 * general application code will ever use a StoreInverselessTheo1 object directly.  This class
 * exists only as a way to simplify our internal design.
 *
 * In the future, we may decide to create a separate interface for this "not-quite-fully-Theo1"
 * thing, e.g. InverselessTheo1.  It's not yet clear exactly how "smart" application code will be
 * able to efficiently bypass the slowdowns introduced in running the inversing mechanisms of
 * PointerInveringTheo1.
 *
 * This class is thread-safe in read-only mode.  This is because read operations mutate no state of
 * its own, and Store is also thread-safe in read-only mode.  This class is most assuredly not
 * thread-safe in read-write mode.
 *
 *
 * CONSTRAINTS AND BEHAVIORS ENFORCED BY THIS CLASS
 * ------------------------------------------------
 *
 * This class builds on Store to provide the "everything", "slot", and "context" entities, along
 * with the "generalizations" entity and slot to enforce a generalization hierarchy among primitive
 * entities.
 *
 * TODO: constraints enforced here.  This has to include the equivalence between generalizations and
 * existence.  SuperStore requires that entities referred to by RTWPointerValues exist according to
 * it, meaning that everything needs a slot.  This seems like something to revisit when we develop a
 * higher layer of Theo that rolls out all its usual machinery, so we'll just give it a
 * "storeinverselesstheo1_dummyslot" slot, which is at least unlikely to result in some kind of
 * naming collision.
 *
 * Resolved: non-primitive-entities are not considered to be legitimate slots or contexts.  The
 * won't be considered to be slots or contexts if they are found to generalize to the slot or
 * context entities.  TODO: shoot on sight or just fsck?
 *
 * Contexts are not fully implemented nor supported at this time.
 *
 * TODO: definition of a Theo slot.  Also, slots are Strings, not EntityRefs.  Making slots
 * EntityRefs would result in (*, R, *) indexing, which we do not want to require/enforce at this
 * juncture.  Hence, we are limited in on-the-fly constraint enforcement in this implementation.  We
 * could have an alternate implementation that requires efficient native (*, R, *) indexing and does
 * enforce its constraints on the fly.
 *
 * TODO: no, we're not doing automatic sparsification of generalizations.  NELL wants to use dense
 * ones.  But we are forwarding children to the parent in the case of primitive entity deletion in
 * order to maintain a valid generalizations hierarchy, and, notably, this could be a source of
 * densification.
 *
 *
 * INTERNAL DESIGN COMMENTS
 * ------------------------
 *
 * FODO: writing this for now in many layers for design ease and incremental simplicity.  Can
 * rewrite later more monolithically or whatever when we see how things run with everything in
 * place.
 *
 * FODO: might could have a Slot class as well in order to bypass validation, but that's easily a
 * round 2 or 3 issue.
 *
 * FODO: we're leaving these layers not totally safe in read-write mode becuase we'll want to be
 * able to play with that in situ when we can execute TCFs using these
 *
 * 2013-02: PointerInversingTheo1 and StoreInverselessTheo1 were written in such a way as to
 * tolerate being given Slot objects that are not their own.  It would be more correct for them to
 * require their own MySlot objects, but it's not worth the reimplementation costs at this point.
 */
public class StoreInverselessTheo1 extends Theo1Base implements Theo1 {
    /**
     * Implementation of a Theo Entity used by StoreInverselessTheo1
     *
     * TODO: I guess the class-level documentation should refer to this as well a the central discussion on constraint enforcement.
     *
     * In read-only mode, our assumption is that no Entity instance will be constructed unless all
     * slots and contexts that it uses are known to be slots and contexts.  TODO: read-write mode.
     *
     * We cannot outright ban the existence of an Entity instance that refers to something that
     * doesn't exist.  It's always legitimate to issue queries for which there are no beliefs, and
     * we would wind up in a chicken-egg situation when it comes to creating things that don't yet
     * exist.  We must, however, gaurantee that no primitive entity is written to before it
     * participates in the generalizations hierarchy.  Conveniently, this constraint need only be
     * considered during add and delete operations, and so we're not stuck validating this
     * everywhere.
     *
     * Recalling that {@link SuperStore} is responsible for ensuring that RTWPointerValues refer to
     * existent things and that RTWElementRefs used inside of RTWLocations refer to existent
     * elements, we thereforeneedn't be concerned with either of those things here.  There will
     * always be constraint violations that we won't discover until the execution path runs far
     * enough to bump into them.
     *
     * For now, we won't go as far as to verify that RTWElementRefs and RTWPointerValues don't use
     * non-slots or non-contexts as slots or contexts.  We may discover some subtle case, but I
     * think SuperStore ensuring that RTWPointerValues refer to existent things is sufficient by
     * transitivity to ensure that they don't break our slot, context, or generalizations
     * requirements.
     *
     * TODO: maybe we should make a separate read-write section here.  Trapping modifications.  Deleting slots and contexts.
     *
     * FODO: we may need an (optional) relaxation here to allow invalid things to be deleted.  Or
     * maybe it will be safe to relegate that activity to our fsck.
     */
    protected class MyEntity extends RTWPointerValue implements Entity {
        /**
         * {@link Entity0Base} uses this to implement the fancier Entity methods
         */
        @Override protected Slot toSlot(String name) {
            return getSlot(name);
        }

        /**
         * {@link Entity0Base} uses this to ensure that all Entity values that we return originate
         * from StoreInverslessTheo1
         *
         * We wouldn't want to return a staright RTWPointerValue from the Store on which we are
         * based because then the end user would be bypassing this layer of Theo and talking
         * directly to something that is too low-level.  It would also screw up things like equals.
         */
        @Override protected Entity wrapEntity(Entity entity) {
            try {
                /* bkdb: do we need these to ensure that e.g. toBelief only ever gets invoked on a MyBelief?
                if (entity instanceof MyBelief)
                    return (MyBelief)entity;
                if (entity instanceof MyQuery)
                    return (MyQuery)entity;
                if (entity instanceof MySlot)
                    return (MySlot)entity;
                if (entity instanceof MyPrimitiveEntity)
                    return (MyPrimitiveEntity)entity;
                */
                if (entity == null) return null;
                if (entity instanceof MyEntity)
                    return entity;
                if (entity instanceof RTWPointerValue)
                    return new MyEntity(((RTWPointerValue)entity).getDestination());
                throw new RuntimeException("Unexpected Entity class "
                        + entity.getClass().getName());
            } catch (Exception e) {
                throw new RuntimeException("wrapEntity(" + entity + ")", e);
            }
        }

        /**
         * Constructor
         *
         * It is assumed that the given RTWLocation is a valid one, and is attached to our store.
         */
        protected MyEntity(RTWLocation location) {
            super(store.getLoc(location));  // FODO: this goes away after removing automagic RTWLocation/Store attachment?
        }

        // FODO: We're sticking with RTWPointerValue's equals and hashCode in order to maintain the
        // KB-independent equality currently needed in order to pass our Entity objects directly
        //
        // bkdb: 2013-02 stuff: reconsider this when we get to the point of deciding the fate of
        // RTWLocation and RTWPointer.

        @Override public String toString() {
            String s = super.toString();
            if (developerMode) s = s + "-SIT1";
            return s;
        }

        @Override public boolean entityExists(Slot slot) {
            try {
                RTWLocation query = location.subslot(slot.getName());
                if (query.getNumValues() > 0) return true;
                return store.getSubslots(query) != null;
            } catch (Exception e) {
                throw new RuntimeException("entityExists(" + slot + ") on " + toString(), e);
            }
        }

        @Override public boolean entityExists() {
            if (location.endsInSlot()) {
                if (location.getNumValues() > 0) return true;
                return store.getSubslots(location) != null;
            } else {
                return location.parent().containsValue(location.lastAsValue());
            }
        }

        @Override public Collection<Slot> getSlots() {
            try {
                RTWListValue subslots = store.getSubslots(location);
                if (subslots == null) return Collections.EMPTY_LIST;
                Collection<Slot> collection = new ArrayList<Slot>(subslots.size());
                for (RTWValue v : subslots)
                    collection.add(getSlot(v.asString()));
                return collection;
            } catch (Exception e) {
                throw new RuntimeException("getSlots() on " + toString(), e);
            }
        }

        @Override public Query getQuery(Slot slot) {
            try {
                return new MyQuery(location.subslot(slot.getName()));
            } catch (Exception e) {
                throw new RuntimeException("getQuery(" + slot + ") on " + toString(), e);
            }
        }

        // bkdb: does EntityBase have a viable equals or what?  Also, it's attractive to think of
        // Slot as having a base equals that goes through getName in order to simplify equality
        // lookups, but 1) no multiple inheritence and 2) maybe we'll wind up violating that
        // simplicity at some higher Theo layer anyway.
        //
        // For now, we'll require equality only within a given KB.  This should be documented along
        // with the two RTWLocations (with iterator exception) thing. at the class level.
        //
        // FODO: per Store, we need equality with RTWPointerValue without regard to KBs
        //
        // Fall 2014: We've ruled that RTWBag has equality only at the object level, but that Theo
        // Parlance objects should have equality if they refer to the same location.  Not sure if
        // we've decided that attached KB also needs to be equal, so let's revisit all of this
        // coherently later.  RTWBag already verified to have correct equals impelementation
        // (i.e. inheriting it from Object).

        @Override public int getNumValues(Slot slot) {
            try {
                return location.subslot(slot.getName()).getNumValues();
            } catch (Exception e) {
                throw new RuntimeException("getNumValues(" + slot + ") on " + toString(), e);
            }
        }

        @Override public RTWBag getReferringValues(Slot slot) {
            try {
                return new ReferringValuesWrapper(store.getPointers(location, slot.getName()));
            } catch (Exception e) {
                throw new RuntimeException("getReferringValues(" + slot + ") on " + toString(), e);
            }
        }

        @Override public int getNumReferringValues(Slot slot) {
            try {
                return store.getPointers(location, slot.getName()).getNumValues();
            } catch (Exception e) {
                throw new RuntimeException("getNumReferringValues(" + slot + ") on " + toString(), e);
            }
        }
                        
        @Override public Collection<Slot> getReferringSlots() {
            RTWListValue subslots = store.getPointingSlots(location);
            if (subslots == null) return Collections.EMPTY_LIST;
            Collection<Slot> collection = new ArrayList<Slot>(subslots.size());
            for (RTWValue v : subslots)
                collection.add(getSlot(v.asString()));
            return collection;
        }

        @Override public Belief getBelief(Slot slot, RTWValue value) {
            try {
                return new MyBelief(location.subslot(slot.getName()).element(value));
            } catch (Exception e) {
                throw new RuntimeException("getBelief(" + slot + ", " + value + ") on " + toString(), e);
            }
        }

        @Override public boolean addValue(Slot slot, RTWValue value) {
            try {
                return StoreInverselessTheo1.this.addValue(location, slot.getName(), value);
            } catch (Exception e) {
                throw new RuntimeException("addValue(" + slot + ", " + value + ") on " + toString(), e);
            }
        }

        /**
         * SuperStore will take care of deleting all RTWPointerValues pointing to anything that we
         * delete here.
         *
         * Note that these automatic deletes within SuperStore will not affect our tracking of the
         * generalizations hierarchy (or, therefore, our complete sets of all slots and contexts)
         * because only primitive entities participate in this hierarchy, and primitive entities are
         * not subject to these automatic deletes.  Any more subtle dangers, such as to complex
         * structures that we are responsible for ensuring the existence of, can be left to being
         * caught by our fsck operation.
         *
         * Deleting the last remaining generalization from a primitive entity will result in an
         * exception if there are still any other belief employing that primitive entity.
         * Otherwise, deleting the last generalization is synonymous with deleting that primitive
         * entity.
         *
         * Deleting the "slot", "context", "everything", or any other entity that this layer of Theo
         * is responsible for enforcing the existence of will result in an error condition, which
         * may take the form of an exception or of a corrupt KB that produces undefined behavior.
         * The same goes for destructive things like making the "generalizations" entity no longer a
         * slot.  This is a class of illegal operations that we might not want to always check for
         * on the fly due to speed concerns, relying instead on the ability to validate and fix a
         * corrupt KB as a separate operation.
         *
         * Deleting a primitive entity to which some other primitive entity generalizes will result
         * in connecting the specializations to the last remaining generalization being deleted.
         * This ensures the integrity of the generalizations hierarhcy among primitive entities.
         * Note that this may result in redundant generalizations; there is no gaurantee that a
         * primitive entitiy won't have a generalization that is implied by some other
         * generalization through the transitivity of the generalizations slot.
         *
         * Deleting a generalization that causes an entity to no longer be a slot or a context will
         * not result in the automatic deletion of all uses of that entity as a slot or context, and
         * hence may result in a KB containing invalid content.  TODO: how this gets rectified.
         */
        @Override public boolean deleteValue(Slot slot, RTWValue value) {
            try {
                return StoreInverselessTheo1.this.deleteValue(location, slot.getName(), value);
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(\"" + slot + "\", " + value + ") on " + toString(), e);
            }
        }

        // FODO: can any of these is* reuse the superclass?
        @Override public boolean isQuery() {
            // If this becomes a speed issue, then we could look into having a rule that we never
            // construct an Entity when it is actually a Query.
            if (!location.endsInSlot()) return false;
            if (location.size() <= 1) return false;
            if (location.size() > 2 || !allContexts.contains(location.getAsSlot(1))) return true;
            return false;
        }
        
        @Override public Query toQuery() {
            // See note in isQuery regarding speed
            if (!isQuery()) throw new RuntimeException(location + " does not designate a Query");
            return new MyQuery(location);
        }

        @Override public boolean isBelief() {
            // If it doesn't end in a slot, then it ends in an entity reference.  It would therefore
            // be a Belief (assuming well-formedness, i.e. that the last element is preceded by at
            // least a primitive entity and a slot.)
            return !location.endsInSlot();
        }

        @Override public Belief toBelief() {
            if (!isBelief()) throw new RuntimeException(location + " does not designate a Belief");
            return new MyBelief(location);
        }

        @Override public boolean isPrimitiveEntity() {
            return (location.size() == 1);
        }

        @Override public PrimitiveEntity toPrimitiveEntity() {
            if (location.size() != 1)
                throw new RuntimeException(location + " does not designate a Primitive Entity");
            return StoreInverselessTheo1.this.get(location.getPrimitiveEntity());
        }

        @Override public boolean isSlot() {
            if (location.size() != 1) return false;  // bk:context
            if (!allSlots.contains(location.getPrimitiveEntity())) return false;
            return true;
        }

        @Override public Slot toSlot() {
            if (location.size() != 1)
                throw new RuntimeException(location + " does not designate a Slot");
            // This will do the slotness check
            return StoreInverselessTheo1.this.getSlot(location.getPrimitiveEntity());
        }
    }

    // FODO: will the rest of these My* then extend RTWPointerValues' future friends?
    /**
     * Implementation of a Theo PrimitiveEntity object used by StoreInverselessTheo1
     *
     * In read-only mode, the existence of one of these objects implies that it has been vetted as
     * an existent and legitimate primitive entity.
     */
    protected class MyPrimitiveEntity extends MyEntity implements PrimitiveEntity {
        // FODO: arguably, MyPrimitiveEntity needn't and shouldn't be RTWLocation-centric but
        // instead carry a String or something -- in the future we'd want it to as easily carry an
        // int etc.  We're doing this for now so that we can derive from MyEntity.

        /**
         * Constructor
         *
         * As with MyEntity, it is assumed that the given location is a valid primitive entity
         */
        protected MyPrimitiveEntity(RTWLocation location) {
            super(location);
        }

        @Override public boolean isPrimitiveEntity() {
            return true;
        }

        @Override public PrimitiveEntity toPrimitiveEntity() {
            return this;
        }

        /**
         * Convenience method for our implementation that gets the String slot name being used
         */
        @Override public String getName() {
            return getRTWLocation().getPrimitiveEntity();
        }

        // FODO: need this so that RTWValue.asString() will work for the needs of Store.  Sounding
        // like another reason to remove the RTWPointerValue N4J parity rigamarole.
        @Override public String asString() {
            return getName();
        }
    }

    /**
     * Implementation of a Theo Slot object used by StoreInverselessTheo1
     *
     * In read-only mode, the existence of one of these objects implies that it has been vetted as
     * an existent and legitimate slot
     */
    protected class MySlot extends MyPrimitiveEntity implements Slot {
        /**
         * Constructor
         *
         * As with MyEntity, it is assumed that the given location is a valid slot
         *
         * By convention, to simplify the code flow, we construct MySlot objects through
         * StoreInverselessTheo1.getSlot.
         */
        protected MySlot(RTWLocation location) {
            super(location);
        }

        @Override public boolean isSlot() {
            return true;
        }

        @Override public Slot toSlot() {
            return this;
        }
    }

    /**
     * Implementation of a Theo Query used by StoreInverselessTheo1
     *
     * Here, the RTWLocation wrapped will end in a valid slot.  It is our assumption that, in
     * read-only mode, all MyQuery instances are valid because they would not have been
     * created otherwise, and therefore need no further validation (e.g. that all slots used exist
     * and are slots).
     */
    protected class MyQuery extends MyEntity implements Query {
        /**
         * Constructor
         *
         * It is assumed that the given RTWLocation is a valid one
         */
        protected MyQuery(RTWLocation location) {
            super(location);
        }

        @Override public Belief getBelief(RTWValue value) {
            return new MyBelief(location.element(value));
        }

        @Override public boolean addValue(RTWValue value) {
            try {
                // There are opportunities here and in addValue to go faster and to precompue.  Not
                // yet sure if this is a valuable place to focus on.
                return StoreInverselessTheo1.this.addValue(location.parent(), location.lastAsSlot(), value);
            } catch (Exception e) {
                throw new RuntimeException("addValue(" + value + ") on " + toString(), e);
            }
        }

        @Override public boolean deleteValue(RTWValue value) {
            try {
                // There are opportunities here and in deleteValue to go faster and to precompue.
                // Not yet sure if this is a valuable place to focus on.
                return StoreInverselessTheo1.this.deleteValue(location.parent(), location.lastAsSlot(), value);
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(" + value + ") on " + toString(), e);
            }
        }            

        @Override public Entity getQueryEntity() {
            return get(location.parent());
        }
        
        @Override public Slot getQuerySlot() {
            return StoreInverselessTheo1.this.getSlot(location.lastAsSlot());
        }

        ////////////////////////////////////////////////////////////////////////
        // Boilerplate that would go into a QueryBase abstract parent except that Java is a jerk and
        // does not allow multiple inheritence
        ////////////////////////////////////////////////////////////////////////

        @Override public Belief getBelief(Object value) {
            try {
                return getBelief(toRTWValue(value));
            } catch (Exception e) {
                throw new RuntimeException("getBelief(" + value + ") on " + toString(), e);
            }
        }

        @Override public boolean addValue(Object value) {
            try {
                return addValue(toRTWValue(value));
            } catch (Exception e) {
                throw new RuntimeException("addValue(" + value + ") on " + toString(), e);
            }
        }

        @Override public Belief addValueAndGetBelief(Object value) {
            try {
                // Do a single conversion up front rather than be lazy
                RTWValue v = toRTWValue(value);
                addValue(v);
                return getBelief(v);
            } catch (Exception e) {
                throw new RuntimeException("addValueAndGetBelief(" + value + ") on " + toString(), e);
            }
        }

        @Override public boolean deleteValue(Object value) {
            try {
                return deleteValue(toRTWValue(value));
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(" + value + ") on " + toString(), e);
            }
        }

        @Override public boolean deleteAllValues() {
            try {
                // Use iterative fetch-next-and-delete approach so that we don't invite the risks of
                // iterating over the things we're modifying.  Deleting a value from a Query object does
                // not invalidate that Query object, so we don't need to reconstruct it.
                int numDeleted = 0;
                boolean deletedSomething;
                do {
                    deletedSomething = false;
                    for (RTWValue v : iter()) {
                        deleteValue(v);
                        deletedSomething = true;
                        numDeleted++;
                        break;
                    }
                } while (deletedSomething);
                return numDeleted > 0;
            } catch (Exception e) {
                throw new RuntimeException("deleteAllValues()", e);
            }
        }

        @Override public boolean isQuery() {
            return true;
        }

        @Override public Query toQuery() {
            return this;
        }

        ////////////////////////////////////////////////////////////////////////
        // For the most part, forward RTWBag methods to our member variable "location".  Stupid jerk
        // Java and its no multiple inhertence.
        //
        // We do have to filter some of the return values through wrapEntity, though, so make sure
        // that we return MyEntity instances and not RTWPointers straight out of the Store.
        ////////////////////////////////////////////////////////////////////////

        @Override public int getNumValues() {
            return location.getNumValues();
        }

        @Override public String valueDump() {
            // This ensures that the entities get wrapped before dumped
            return SimpleBag.valueDump(this);
        }

        @Override public boolean isEmpty() {
            return location.isEmpty();
        }

        @Override public boolean has1Value() {
            return location.has1Value();
        }

        @Override public boolean has1String() {
            return location.has1String();
        }

        @Override public boolean has1Integer() {
            return location.has1Integer();
        }

        @Override public boolean has1Double() {
            return location.has1Double();
        }

        @Override public boolean has1Boolean() {
            return location.has1Boolean();
        }

        @Override public boolean has1Entity() {
            return location.has1Entity();
        }

        @Override public Iterable<RTWValue> iter() {
            return wrapEntities(location.iter());
        }

        @Override public Iterable<RTWBooleanValue> booleanIter() {
            return location.booleanIter();
        }

        @Override public Iterable<RTWDoubleValue> doubleIter() {
            return location.doubleIter();
        }

        @Override public Iterable<RTWIntegerValue> integerIter() {
            return location.integerIter();
        }

        @Override public Iterable<RTWStringValue> stringIter() {
            return location.stringIter();
        }

        @Override public Iterable<Entity> entityIter() {
            return wrapEntities2(location.entityIter());
        }

        @Override public RTWValue into1Value() {
            return wrapEntity(location.into1Value());
        }

        @Override public String into1String() {
            return location.into1String();
        }
       
        @Override public Integer into1Integer() {
            return location.into1Integer();
        }

        @Override public Double into1Double() {
            return location.into1Double();
        }

        @Override public Boolean into1Boolean() {
            return location.into1Boolean();
        }

        @Override public Entity into1Entity() {
            return wrapEntity(location.into1Entity());
        }

        @Override public RTWValue need1Value() {
            return wrapEntity(location.need1Value());
        }

        @Override public boolean need1Boolean() {
            return location.need1Boolean();
        }

        @Override public double need1Double() {
            return location.need1Double();
        }

        @Override public int need1Integer() {
            return location.need1Integer();
        }

        @Override public String need1String() {
            return location.need1String();
        }

        @Override public Entity need1Entity() {
            return wrapEntity(location.need1Entity());
        }

        @Override public boolean containsValue(RTWValue v) {
            return location.containsValue(v);
        }

        @Override
        public Belief addValueAndGetBelief(RTWValue value) {
            addValue(value);
            return getBelief(value);
        }

        @Override
        public Iterable<Belief> getBeliefs() {
            throw new RuntimeException("Not Implemented");  //bkdb
        }
    }

    /**
     * Implementation of a Theo Belief object used by StoreInverselessTheo1
     *
     * It's not yet known whether and how Belief objects will come up in the normal course of
     * things, so it's too early to debate whether or not we might be better off storing this as a
     * (Query, Value) pair rather than as an RTWLocation.
     */
    protected class MyBelief extends MyEntity implements Belief {
        protected MyBelief(RTWLocation location) {
            super(location);
        }

        @Override public Query getBeliefQuery() {
            return new MyQuery(location.parent());
        }

        @Override public RTWValue getBeliefValue() {
            return wrapEntity(location.lastAsValue());
        }

        @Override public Entity getQueryEntity() {
            // We can speed this up later if it turns out to be a common code path
            return get(location.parent().parent());
        }

        @Override public Slot getQuerySlot() {
            // We can speed this up later if it turns out to be a common code path
            return StoreInverselessTheo1.this.getSlot(location.parent().lastAsSlot());
        }

        @Override public boolean isBelief() {
            return true;
        }

        @Override public Belief toBelief() {
            return this;
        }
        
        @Override public boolean hasMirrorImage() {
            throw new RuntimeException("Not implemented");  //bkdb
        }
        
        @Override public Belief getMirrorImage() {
            throw new RuntimeException("Not implemented");  // bkdb
        }
    }

    /**
     * Log sweet log
     */
    private final static Logger log = LogFactory.getLogger();

    /**
     * Developer mode to control stuff like ugly outputs helpful to developers
     */
    protected final boolean developerMode;

    /**
     * SuperStore that we sit atop
     */
    protected final SuperStore store;

    /**
     * Complete set of entities that generalize directly or indirectly to slot
     */
    protected final HashSet<String> allSlots = new HashSet<String>();

    /**
     * Complete set of entities that generalize directly or indirectly to context
     */
    protected final HashSet<String> allContexts = new HashSet<String>();

    /**
     * This is the constraint-checking guts of get.
     *
     * Return true if this location qualifies as a Query and false otherwise.
     *
     * This might not need to be factored out, but that's the way it grew.
     *
     * FODO: or would we want to be (optionally) tolerant of non-slotness etc. during reads?
     */
    protected boolean validateEntity(RTWLocation location) {
        try {
            if (location.size() == 0)
                throw new RuntimeException("Zero-length location");

            // The first element can be a primitive entity or a context, so anything goes as long as
            // its not an RTWElementRef.
            if (!location.isSlot(0))
                throw new RuntimeException("Location cannot begin with an RTWElementRef");

            // As for the rest, they must not be contexts.  Furthermore, those elements that are not
            // RTWElementRefs must be slots (except for the very first one, which may be a nonslot
            // only if element 0 is a context).  In a valid KB, there will be no overlap between
            // slot and context, so we'll forego verifying that all slots are, additionally, not
            // contexts.
            //
            // We don't go to the extent of verifying that all RTWElementRefs refer to existent
            // elements.  That will get caught down in Store if/when the user actually tries to
            // dereference the location.  What we do here is sufficient to gaurantee something that
            // is structurally valid.
            //
            boolean prevWasElementRef = false;
            boolean startsWithContext = false;
            for (int i = 1; i < location.size(); i++) {
                if (location.isSlot(i)) {
                    String slot = location.getAsSlot(i);
                    if (!allSlots.contains(slot)) {
                        // This is allowable when i == 1 if the 0th element is a context
                        if (i == 1) {
                            String c = location.getPrimitiveEntity();
                            if (!allContexts.contains(c))
                                throw new RuntimeException("\"" + slot + "\" is not a known slot and \""
                                        + c + "\" is not a known context");
                                log.error(slot + " is not a known slot");
                                startsWithContext = true;
                        } else {
                            throw new RuntimeException("\"" + slot + "\" is not a known slot");
                        }
                    }
                    prevWasElementRef = false;
                } else {
                    // I suppose this is not strictly necessary because it would blow up somewhere
                    // inside Store, but it seems like a quick enough check to be harmless.
                    if (prevWasElementRef)
                        throw new RuntimeException("Location cannot have two RTWElementRefs in a row");
                    prevWasElementRef = true;
                }
            }

            // Not a query if it ends with an elementref.  Otherwise, we have to verify that it's
            // not a primitive entity or contextualized primitive entity.  Length > 2 gaurantees
            // this.  Length > 1 is a Query only if we didn't soak up that length with a prepended
            // context.
            if (prevWasElementRef) return false;
            if (location.size() > 2) return true;
            if (location.size() > 1 && !startsWithContext) return true;
            return false;
        } catch (Exception e) {
            throw new RuntimeException("validateEntity(" + location + ")", e);
        }
    }

    /**
     * Helper function for checkEssentials
     */
    protected boolean checkEssential(String entity, String slot, RTWValue value, boolean exactly, boolean autofix) {
        boolean ok = true;
        RTWLocation l = store.getLoc(entity, slot);
        int expectedNumValues = 1;
        if (!l.containsValue(value)) {
            if (autofix) {
                log.debug("Adding " + value + " to " + l);
                store.add(l, value);
            } else {
                log.error("KB lacks " + value + " in " + l);
                expectedNumValues = 0;
                ok = false;
            }
        }
        if (exactly && l.getNumValues() != expectedNumValues) {
            if (autofix) {
                log.warn("Deleting spurious values in " + l);
                boolean deletedSomething;
                do {
                    deletedSomething = false;
                    for (RTWValue v : l.iter()) {
                        if (v.equals(value)) continue;
                        log.debug("Deleting spurious value " + v + " from " + l);
                        store.delete(l.element(v), true, false);
                        deletedSomething = true;
                    }
                } while (deletedSomething);
            } else {
                ok = false;
                log.error("Spurious values detected in " + l);
            }
        }
        return ok;
    }

    /**
     * Verifies that the KB contains essential entries like the slot, generalizations, and
     * everything entities, and optionally creates / fixes those that are missing or improper.
     *
     * This is kind of our provisional fsck
     *
     * If autofix is set and we are in read/write mode, this will create missing things and delete
     * improper things.  Otherwise, this will only put errors in the log about problems that it
     * finds.
     *
     * Returns true if the KB is good to go and false if it is not.
     */
    protected boolean checkEssentials(boolean autofix) {
        // FODO: in the next design change, make it log that it's creating/fixing essential stuff
        // exactly once at the outset.
        if (isReadOnly() && autofix == true)
            throw new RuntimeException("Can't autofix in read-only mode");
        boolean ok = true;
        ok = ok && checkEssential("everything", "storeinverselesstheo1_dummyslot", new RTWStringValue("dummyvalue"), true, autofix);
        ok = ok && checkEssential("slot", "generalizations", new MyEntity(store.getLoc("everything")), true, autofix);
        ok = ok && checkEssential("context", "generalizations", new MyEntity(store.getLoc("everything")), true, autofix);
        ok = ok && checkEssential("generalizations", "generalizations", new MyEntity(store.getLoc("slot")), true, autofix);
        ok = ok && checkEssential("storeinverselesstheo1_dummyslot", "generalizations", new MyEntity(store.getLoc("slot")), true, autofix);
        return ok;
    }

    /**
     * Recursive guts of computeAllSlots.
     *
     * Adds all entities that generalize to the given entity to dst (but not the given entity
     * itself), recursing into each one.
     *
     * This assumes that dst has not already been populated.  It will not recurse into an entity
     * that is already present in dst.
     *
     * This will complain and reject recursion into anything that doesn't qualify as a primitive
     * entity
     */
    protected void computeAllSlotsRecurse(Set<String> dst, String entity, String name) {
        try {
            for (RTWValue v : store.getPointers(store.getLoc(entity), "generalizations").iter()) {  // bkdb: pointeriteration
                RTWLocation l = ((RTWPointerValue)v).getDestination();
                if (l.size() != 1) {
                    log.error("Ignoring invalid generalization from non-primitive entity " + l
                            + " to " + entity + " as part of the " + name + " hierarchy!");
                    continue;
                }
                String subEntity = l.getPrimitiveEntity();
                if (allSlots.add(subEntity))
                    computeAllSlotsRecurse(dst, subEntity, name);
            }
        } catch (Exception e) {
            throw new RuntimeException("recurse(<dst>, \"" + entity + "\", \"" + name + "\")",
                    e);
        }
    }

    /**
     * Repopulate allSlots and allContexts based on the content of the KB
     *
     * This recursively traverses the the "inverse" of the generalizations slots starting at the
     * given entity.  (Technically, there is no proper notion of an "inverse slot" at this low layer
     * of Theo; we use SuperStore.getPointers to issue "reverse" queries on the generalizations
     * slot.)
     */
    protected void computeAllSlots() {
        log.debug("Generating complete sets of slots and contexts...");
        allSlots.clear();
        allContexts.clear();
        allSlots.add("slot");
        computeAllSlotsRecurse(allSlots, "slot", "slot");
        allContexts.add("context");
        computeAllSlotsRecurse(allContexts, "context", "context");

        log.debug("Checking for slot/context overlap...");
        boolean failure = false;
        for (String context : allContexts) {
            if (allSlots.contains(context)) {
                log.error(context + " generalizes to both slot and context!");
                failure = true;
            }
        }
        // FODO: The general notion is to be tolerant of errors (and we certainly need to be
        // operational enough to allow the situation to be fixed).  But we'll have to wait and see
        // how this validation is used.  We might have to fall back to running in "warn-only" mode
        // or something.
    }

    /**
     * What we do when opening (or when we are constructed with an already-open store)
     */
    protected void initialize() {
        if (!checkEssentials(!isReadOnly()))
            throw new RuntimeException("KB lacks essential content.  Re-open in read/write mode to attempt repair.");
        computeAllSlots();
    }

    /**
     * Guts of the addValue operation
     *
     * State-tracking and constraint-enforcing logic to be applied any time a value is added to a
     * generalizations slot.
     *
     * The given location should not end in the slot to which the value is being added.
     *
     * Adding a generalization to a non-existent primitive entity is the same as creating that
     * primitive entity.  Adding a non-generalization belief to a non-existent primitive entity will
     * result in an exception because all existent primitive entities must participate in the
     * generalizations hierarchy.  Attempting to add a generalization to the "everything" entity
     * will result in an exception.  Adding a generalization to a contextualized or other composite
     * entity will result in an exception.  If this Entity instance refers to a non-existent belief,
     * this attempt to add a subslot to a non-existent belief will result in an exception.
     *
     * Note that all generalizations must be MyEntities corresponding to existent entities, not
     * RTWStringValues.
     *
     * Because slot inverses don't exist at this level of Theo, there is no automatic maintenance or
     * guarding of specialization slots here; they are treated opaquely like ever other slot that is
     * not the generalizations slot.
     */
    protected boolean addValue(RTWLocation location, String slot, RTWValue value) {
        // Initial validation is same as for get 
        if (!allSlots.contains(slot)) 
            throw new RuntimeException("\"" + slot + "\" is not a known slot"); 

        // Checks and special cases to do with the generalizations slot
        if (slot.equals("generalizations")) {
            if (location.size() != 1)
                throw new RuntimeException("Generalizations may not be added to non-primitive entities");
            String entity = location.getPrimitiveEntity();
            if (entity.equals("everything"))
                throw new RuntimeException("Generalizations may not be added to the \"everything\" entity");
            if (!(value instanceof MyEntity))
                throw new RuntimeException("Values added to the generalizations slot must be Entity objects that come from this KB.  Given "
                        + value.getClass().getName());
            RTWLocation genloc = ((MyEntity)value).location;
            if (genloc.size() != 1)
                throw new RuntimeException("Generalizations may only exist to primitive entities");
            String gen = genloc.getPrimitiveEntity();
            if (store.getSubslots(genloc) == null)
                throw new RuntimeException("The \"" + gen + "\" entity does not exist");
            // We could have looked for a nonzero number of values in the generalizations
            // slot, but then we'd have to give "everything" a free pass, and that equality
            // check against "everything" would take longer.  We leave it to our fsck
            // operation to go the extra mile to verify that existence in the Store sense is
            // synonymous with having at least one generalization.

            // As a design feature, we need only verify that we are not in read-only mode
            // when it comes time to adjust allSlots or allContexts.  We can save time
            // otherwise by leaving the read-only check to the SuperStore that we sit on.
            if (isReadOnly())
                throw new RuntimeException("Can't addValue in read-only mode");

            if (gen.equals("slot") || allSlots.contains(gen)) {
                if (allContexts.contains(entity) || entity.equals("context"))
                    throw new RuntimeException("\"" + entity + " may not generalize to \"" + gen
                            + "\" because it is already a context and cannot also be a slot");
                if (allSlots.add(entity))
                    log.debug("bkdb: added new slot '" + entity + "'");
            } else if (gen.equals("context") || allContexts.contains(gen)) {
                if (allSlots.contains(entity) || entity.equals("slot"))
                    throw new RuntimeException("\"" + entity + " may not generalize to \"" + gen
                            + "\" because it is already a slot and cannot also be a context");
                allContexts.add(entity);
            }
        } 
        else {
            if (location.size() == 1)              // bk:context
                // FODO: is this the fastest existence check, or will RTWLocation be
                // augmented?
                if (location.subslot("generalizations").getNumValues() <= 0
                        && !location.getPrimitiveEntity().equals("everything"))
                    throw new RuntimeException("The \"" + location.getPrimitiveEntity() +
                            "\" entity must generalize to something before other beliefs about it may be added");
        }

        // Store will guard against adding to a subslot of a nonexistent belief
        RTWLocation query = location.subslot(slot);
        return store.add(query, value);
    }

    /**
     * Guts of the deleteValue operation
     *
     * As with addValue, the given location should not end in the given slot.
     *
     * SuperStore will take care of deleting all RTWPointerValues pointing to anything that we
     * delete here.
     *
     * Note that these automatic deletes within SuperStore will not affect our tracking of the
     * generalizations hierarchy (or, therefore, our complete sets of all slots and contexts)
     * because only primitive entities participate in this hierarchy, and primitive entities are not
     * subject to these automatic deletes.  Any more subtle dangers, such as to complex structures
     * that we are responsible for ensuring the existence of, can be left to being caught by our
     * fsck operation.
     *
     * Deleting the last remaining generalization from a primitive entity will result in an
     * exception if there are still any other belief employing that primitive entity.  Otherwise,
     * deleting the last generalization is synonymous with deleting that primitive entity.
     *
     * Deleting the "slot", "context", "everything", or any other entity that this layer of Theo is
     * responsible for enforcing the existence of will result in an error condition, which may take
     * the form of an exception or of a corrupt KB that produces undefined behavior.  The same goes
     * for destructive things like making the "generalizations" entity no longer a slot.  This is a
     * class of illegal operations that we might not want to always check for on the fly due to
     * speed concerns, relying instead on the ability to validate and fix a corrupt KB as a separate
     * operation.
     *
     * Deleting a primitive entity to which some other primitive entity generalizes will result in
     * connecting the specializations to the last remaining generalization being deleted.  This
     * ensures the integrity of the generalizations hierarhcy among primitive entities.  Note that
     * this may result in redundant generalizations; there is no gaurantee that a primitive entitiy
     * won't have a generalization that is implied by some other generalization through the
     * transitivity of the generalizations slot.
     *
     * Deleting a generalization that causes an entity to no longer be a slot or a context will not
     * result in the automatic deletion of all uses of that entity as a slot or context, and hence
     * may result in a KB containing invalid content.  TODO: how this gets rectified.
     */
    protected boolean deleteValue(RTWLocation location, String slot, RTWValue value) {
        // Initial validation is same as for get 
        if (!allSlots.contains(slot)) 
            throw new RuntimeException("\"" + slot + "\" is not a known slot"); 

        // Special cases when operating on the generalizations slot.  We'll restrict our checks to
        // primitive entities; generalizations shouldn't exist elsewhere, and it's not clear that we
        // need to worry about exact semantics for them in case of a violate KB.
        if (location.size() == 1 && slot.equals("generalizations")) {
            String entity = location.getPrimitiveEntity();

            // As a design feature, we need only verify that we are not in read-only mode when it
            // comes time to adjust allSlots or allContexts.  We can save time otherwise by leaving
            // the read-only check to the SuperStore that we sit on.
            if (isReadOnly())
                throw new RuntimeException("Can't deleteValue in read-only mode");

            // Are we about to delete this entity?
            RTWLocation genloc = location.subslot("generalizations");
            if (genloc.getNumValues() == 1 && genloc.containsValue(value)) {
                // It's tempting to think of just checking for a TheoSystemEntity=true value or
                // something, but additional KB fetches are to be avoided for speed purposes.  Even
                // having a simple HashSet of undeletable entities is unlikely to be faster until we
                // have many more of them.  Setting asside the possibility of switching to integer
                // IDs for slots, this is the sort of thing where I figure we could optionally relax
                // the checking where speed is key, and let fsck handle error-checking.
                if (entity.equals("slot"))
                    throw new RuntimeException("The \"slot\" entity may not be deleted");
                if (entity.equals("context"))
                    throw new RuntimeException("The \"context\" entity may not be deleted");
                if (entity.equals("everything"))
                    throw new RuntimeException("The \"everything\" entity may not be deleted");
                if (store.getSubslots(location).size() != 1)  // bkdb: rephrase size 1 in terms of primitive entity
                    throw new RuntimeException("The \"" + entity
                            + "\" entity may not be deleted because it is participant in other beliefs");

                // FODO: add other checks, like making generalizations not a slot, removing the
                // sentry slot we put in "everything", etc.  Alternatively, put that stuff in the
                // fsck.

                // For each specialization of this entity, add the generalization that we're about
                // to remove.
                for (RTWValue specialization : store.getPointers(location, "generalizations").iter()) { // FODO: pointers iterator
                    RTWPointerValue p = (RTWPointerValue)specialization;
                    RTWLocation l = store.getLoc(p.getDestination().subslot("generalizations"));
                    store.add(l, value);
                }

                // If this entity is a slot or context, it's uncomplicated to know that it
                // no longer will be one
                if (allSlots.remove(entity))
                    log.debug("bkdb: removed slot " + entity);
                allContexts.remove(entity);
                // TODO: used-as-slot constraint

                // FODO: do it on the fly if using a SuperDuperStore (here and elsewhere)
            }

            // If we're not deleting the entity, then it's more complicated to maintain our complete
            // sets of all slots and all contexts.  Because an entity could generalize to slot or
            // context through multiple paths, we're left with the laborious task of checking all
            // other generalizations.
            else {
                        
                // It's illegal to have a generalization value that is not a MyEntity that is a
                // primitive entity, but it's not illegal to try to delete one, so we have to have
                // these extra checks here.
                if (value instanceof MyEntity) {
                    RTWLocation l = ((MyEntity)value).location;
                    if (l.size() == 1) {
                        String gen = l.getPrimitiveEntity();

                        // Only check if this is still a slot if it was one to begin with.
                        if (allSlots.contains(entity)) {
                            boolean stillSlot = false;
                            for (RTWValue v : genloc.iter()) { // FODO: pointer iterator
                                RTWPointerValue p = (RTWPointerValue)v;
                                String g = ((RTWPointerValue)p).getDestination().getPrimitiveEntity();
                                if (allSlots.contains(g)) {
                                    log.debug("Deleting generalization " + value + " of " + entity + ": found other generalization " + g + " to be a slot");
                                    stillSlot = true;
                                    break;
                                } else {
                                    log.debug("Deleting generalization " + value + " of " + entity + ": found other generalization " + g + " to not be a slot");
                                }
                            }
                            if (!stillSlot) {
                                if (allSlots.remove(entity))
                                    log.debug("Deleting generalization " + value + " of " + entity + ": " + entity + " is now no longer a slot");
                                // TODO: used-as-slot constraint
                            }
                        }

                        // Only check if this is still a context if it was one to being with.  No
                        // "else if" here so that we don't get out of sync when deleting an entity
                        // that had illegally generalized to both slot and context.
                        if (allContexts.contains(entity)) {
                            boolean stillContext = false;
                            for (RTWValue v : genloc.iter()) { // FODO: pointer iterator
                                RTWPointerValue p = (RTWPointerValue)v;
                                String g = ((RTWPointerValue)p).getDestination().getPrimitiveEntity();
                                if (allContexts.contains(g)) {
                                    stillContext = true;
                                    break;
                                }
                            }
                            if (!stillContext) {
                                allContexts.remove(entity);
                                // TODO: used-as-context constraint
                            }
                        }
                    }
                }
            }
        }

        // And, now we can finally actually delete the value.  Sure is a lot easier if we're not
        // deleting a generalization, huh?
        return store.delete(location.subslot(slot).element(value), false, false);
    }

    /**
     * Construct a new StoreInverselessTheo1 based on the given Store
     */
    public StoreInverselessTheo1(SuperStore store) {
        // We don't actually have a properties file of our own yet.  The only properties that we're
        // interested in are sort of "global NELL" settings, which, for now, are accumulated in
        // MBL's properties file.  So we just continue that practice for the time being.  bk:prop
        Properties properties = TheoFactory.getProperties();
        developerMode = properties.getPropertyBooleanValue("developerMode", false);

        this.store = store;
        if (store.isOpen()) initialize();
    }
    
    public void open(String filename, boolean openInReadOnlyMode) {
        try {
            store.open(filename, openInReadOnlyMode);
            initialize();
        } catch (Exception e) { 
            throw new RuntimeException("open(\"" + filename + "\", " + openInReadOnlyMode + ")", e);
        }
    }

    @Override public boolean isOpen() {
        return store.isOpen();
    }

    @Override public boolean isReadOnly() {
        return store.isReadOnly();
    }

    @Override public void setReadOnly(boolean makeReadOnly) {
        store.setReadOnly(makeReadOnly);
    }

    @Override public void close() {
        store.close();
        allSlots.clear();
        allContexts.clear();
    }

    // bkdb: rewrite the below to be less in terms of RTWLocation when it moves out to a more library-style place
    protected void pre(PrintStream out, Entity entity) {
        try {
            RTWLocation l = entity.getRTWLocation();
            String indent = "";
            for (int i = 0; i < l.size(); i++) indent = indent + "  ";

            for (Slot slot : entity.getSlots()) {
                out.print(indent + slot + ": ");
                boolean first = true;
                Query q = entity.getQuery(slot);
                out.print(q.valueDump());
                out.print("\n");

                for (RTWValue v : entity.getQuery(slot).iter()) {
                    Entity sube = get(l.subslot(slot.getName()).element(v));
                    if (!sube.getSlots().isEmpty()) {
                        out.print(indent + "  =" + v + "\n");
                        pre(out, sube);
                    }
                }

                if (!q.getSlots().isEmpty())
                    pre(out, q);
            }
        } catch (Exception e) {
            throw new RuntimeException("pre(<out>, " + entity + ")", e);
        }
    }

    /**
     * helper function for testing fixture
     */
    protected void pre(RTWLocation l) {
        PrintStream out = System.out;
        String indent = "";
        for (int i = 0; i < l.size(); i++) indent = indent + "  ";
        out.print(l + ":\n");
        pre(out, get(l));
        out.print("\n");
    }

    // bkdb: This is different from the Inversing version on account of gen/spec, but it'd be legit
    // to make this the official version because it's smarter to know how to use gens anyway.
    protected void prer(RTWLocation l) {
        pre(l);
        for (RTWValue v : get(l).getReferringValues("generalizations").iter()) {
            if (v instanceof RTWPointerValue)
                prer(((RTWPointerValue)v).getDestination());
        }
    }

    @Override public PrimitiveEntity get(String primitiveEntity) {
        try {
            RTWLocation location = store.getLoc(primitiveEntity);
            validateEntity(location);
            return new MyPrimitiveEntity(store.getLoc(location));
        } catch (Exception e) {
            throw new RuntimeException("get(" + primitiveEntity + ")", e);
        }
    }

    /**
     * Returns an Entity object corresponding to the given location, through which the caller may
     * interact with the KB.
     *
     * Most of the methods available from Entity take a slot name as a parameter, making it
     * unnecessary to obtain an intermediate Query object corresponding to (Entity, slot).  We may
     * decide to add a getQuery and/or getBelief method if it turns out that this would be useful
     * for brevity or speed, although perhaps that would best be done in some superficial subclass
     * or composition so as to keep the various lower-level KB classes simpler.
     *
     * We may also decide that this ought to return a Query or other Entity subclass in the case
     * that the given location can be identified as something more specific.
     *
     * In effect, all this method does, in constructing an Entity instance, is verify that the given
     * location is valid according to the constraints that this class is responsible for enforcing,
     * e.g. that those things used as slots or contexts are indeed slots or contexts.  See {@link
     * Entity} for a more thorough discussion of how we enforce which constraints.
     */
    @Override public Entity get(RTWLocation location) {
        try {
            if (validateEntity(location)) {
                return new MyQuery(store.getLoc(location));
            } else {
                if (location.size() == 1)
                    return new MyPrimitiveEntity(store.getLoc(location));
                if (!location.endsInSlot())
                    return new MyBelief(store.getLoc(location));
                return new MyEntity(store.getLoc(location));
            }
        } catch (Exception e) {
            throw new RuntimeException("get(" + location + ")", e);
        }
    }

    @Override public Slot getSlot(String slotName) {
        if (!allSlots.contains(slotName))
            throw new RuntimeException("\"" + slotName + "\" is not a known slot");
        return new MySlot(store.getLoc(slotName));
    }

    @Override public RTWValue ioctl(String syscall, RTWValue params) {
        try {
            if (syscall.equals("io:sync")) {
                store.flush(true);
                RTWValue reply = super.ioctl(syscall, params);
                if (reply == null) return RTWThisHasNoValue.NONE;
                else return reply;
            } else if (syscall.equals("io:optimize")) {
                store.optimize();
                RTWValue reply = super.ioctl(syscall, params);
                if (reply == null) return RTWThisHasNoValue.NONE;
                else return reply;
            } else if (syscall.equals("hint:largeaccess")) {
                store.giveLargeAccessHint();
                RTWValue reply = super.ioctl(syscall, params);
                if (reply == null) return RTWThisHasNoValue.NONE;
                else return reply;
            } else {
                return super.ioctl(syscall, params);
            }
        } catch (Exception e) {
            throw new RuntimeException("ioctl(\"" + syscall + "\", " + params + ")", e);
        }
    }


    private static Pair<RTWLocation, Integer> parseLocation(String whole, Theo0 theo) {
        log.info("parseLocation(\"" + whole + "\")");
        try {
            List<Object> list = new ArrayList<Object>();
            int i = 0;
            int start = 0;
            while (i < whole.length()) {
                // log.info("char " + i + " is '" + whole.charAt(i) + "'");
                if (whole.charAt(i) == ' ') {
                    log.info("i is " + i + " and start is " + start);
                    if (i != start)
                        throw new RuntimeException("Space in the midst of a value at position " + i);
                    i++;
                    start = i;
                }
                else if (whole.charAt(i) == '>') {
                    if (i - start > 0) {
                        Pair<RTWValue, Integer> p = parseValue(whole.substring(start, i), theo);
                        if (p.getRight() != i-start)
                            throw new RuntimeException("Extra junk in RTWValue starting at: " + whole.substring(p.getRight()));
                        list.add(p.getLeft());
                    }

                    // Eat optional comma
                    if (i < whole.length() && whole.charAt(i) == ',') i++;
                        
                    start = i;
                    break;
                }
                else if (whole.charAt(i) == ',') {
                    if (i - start == 0)
                        throw new RuntimeException("Zero-length element at position " + i);
                    Pair<RTWValue, Integer> p = parseValue(whole.substring(start, i), theo);
                    if (p.getRight() != i-start)
                        throw new RuntimeException("Extra junk in RTWValue starting at: " + whole.substring(p.getRight()));
                    list.add(p.getLeft());
                    i++;
                    start = i;
                    log.info("now we're at " + i);
                } else if (whole.charAt(i) == '<') {
                    if (i != start)
                        throw new RuntimeException("'<' in the midst of a value at position " + i);
                    Pair<RTWLocation, Integer> lpair = parseLocation(whole.substring(i+1), theo);
                    list.add(new RTWPointerValue(lpair.getLeft()));
                    i += lpair.getRight() + 2;
                    log.info("i is " + i + " after the parselocation call because it returned " + lpair.getRight());

                    // Eat optional comma
                    if (i < whole.length() && whole.charAt(i) == ',') i++;
                    start = i;
                } else if (whole.charAt(i) == '=') {
                    if (i != start)
                        throw new RuntimeException("'=' in the midst of a value at position " + i);
                    Pair<RTWValue, Integer> vpair = parseValue(whole.substring(i+1), theo);
                    list.add(new RTWElementRef(vpair.getLeft()));
                    i += vpair.getRight() + 2;

                    // Eat optional comma
                    if (i < whole.length() && whole.charAt(i) == ',') i++;
                    start = i;
                } else {
                    // In a value.  Just eat it.
                    i++;
                }
            }

            // Coming to the end is like hitting a comma if we've accumulated anything between start
            // and i.
            if (i - start > 0) {
                Pair<RTWValue, Integer> p = parseValue(whole.substring(start, i), theo);
                // log.info("i is " + i + " and start is " + start + " and parseValue returned " + p.getRight());
                if (p.getRight() != i-start)
                    throw new RuntimeException("Extra junk in RTWValue starting at: " + whole.substring(p.getRight()));
                list.add(p.getLeft());
            }

            RTWLocation l = new AbstractRTWLocation(list.toArray());
            // log.info("parseLocation(\"" + whole + "\") returning " + l + ", " + i);
            return new Pair<RTWLocation, Integer>(l, i);
        } catch (Exception e) {
            throw new RuntimeException("parseLocation(\"" + whole + "\")", e);
        }
    }

    private static Pair<RTWValue, Integer> parseValue(String whole, Theo0 theo) {
        // log.info("parseValue(\"" + whole + "\")");
        try {
            if (whole.length() == 0)
                throw new RuntimeException("Zero-length value");
            if (whole.charAt(0) == '<') {
                Pair<RTWLocation, Integer> p = parseLocation(whole.substring(1, whole.length()-1), theo);
                if (whole.charAt(p.getRight() + 1) != '>')
                    throw new RuntimeException("RTWLocation does not end with '>' at position "
                            + (p.getRight()+1));
                return new Pair<RTWValue, Integer>(theo.get(p.getLeft()), p.getRight()+2);
            } else {
                // We'll allow only those characters that we don't use as special punctuation to be
                // part of the value.  And, for now, we'll make everything an RTWStringValue
                int end = whole.length();
                int i = -1;
                for (String test : new String[]{" ", ",", "<", ">", "="}) {
                    i = whole.indexOf(test);
                    if (i >= 0 && i < end) end = i;
                }
                // log.info("i is " + i + " and end is " + end);
                RTWValue v = new RTWStringValue(whole.substring(0, end));
                return new Pair<RTWValue, Integer>(v, end);
            }
        } catch (Exception e) {
            throw new RuntimeException("parseValue(\"" + whole + "\")", e);
        }
    }

    public static RTWLocation parseLocationArgument(String arg, Theo0 theo) {
        Pair<RTWLocation, Integer> p = parseLocation(arg, theo);
        int end = p.getRight();
        if (end < arg.length())
            throw new RuntimeException("Junk after location: \"" + arg.substring(end) + "\"");
        return p.getLeft();
    }

    public static RTWValue parseValueArgument(String arg, Theo0 theo) {
        Pair<RTWValue, Integer> p = parseValue(arg, theo);
        int end = p.getRight();
        if (end < arg.length())
            throw new RuntimeException("Junk after value: \"" + arg.substring(end) + "\"");
        return p.getLeft();
    }

    /**
     * Testing fixture
     */
    public static void main(String args[]) throws Exception {
        try {
            String filename = args[0];
            String cmd = args[1];
            String locstr = args[2];

            StoreInverselessTheo1 theo = new StoreInverselessTheo1(new StringListSuperStore(new MapDBStoreMap()));
            theo.open(filename, false);

            RTWLocation loc = parseLocationArgument(locstr, theo);
            System.out.println("For location " + loc);

            Entity e = theo.get(loc);

            if (cmd.equals("getQuery")) {
                String slot = args[3];
                System.out.println(e.getQuery(slot).valueDump());
            } else if (cmd.equals("getNumValues")) {
                String slot = args[3];
                System.out.println(e.getNumValues(slot));
            } else if (cmd.equals("getReferringValues")) {
                String slot = args[3];
                System.out.println(e.getReferringValues(slot).valueDump());
            } else if (cmd.equals("getNumReferringValues")) {
                String slot = args[3];
                System.out.println(e.getNumReferringValues(slot));
            } else if (cmd.equals("entityExists")) {
                if (args.length > 3) {
                    String slot = args[3];
                    System.out.println(e.entityExists(slot));
                } else {
                    System.out.println(e.entityExists());
                }
            } else if (cmd.equals("getSlots")) {
                System.out.println(e.getSlots());
            } else if (cmd.equals("addValue")) {
                String slot = args[3];
                RTWValue value = parseValueArgument(args[4], theo);
                System.out.println(e.addValue(slot, value));
                System.out.println(e.getQuery(slot).valueDump());
            } else if (cmd.equals("deleteValue")) {
                String slot = args[3];
                RTWValue value = parseValueArgument(args[4], theo);
                e.deleteValue(slot, value);
                System.out.println(e.getQuery(slot).valueDump());
            } else if (cmd.equals("pre")) {
                theo.pre(loc);
            } else if (cmd.equals("prer")) {
                theo.prer(loc);
            } else if (cmd.equals("gojira")) {
                // Set up (part of) the Gojira example like TCHSuperStore, but this time with
                // contexts.  Note that this example will cause various kinds of failures becuase
                // contexts don't work right yet.
                PrimitiveEntity peverything = theo.get("everything");
                PrimitiveEntity pslot = theo.get("slot");
                PrimitiveEntity pcontext = theo.get("context");

                theo.createPrimitiveEntity("livesin", pslot);
                theo.createPrimitiveEntity("attacks", pslot);
                theo.createPrimitiveEntity("causes", pslot);
                theo.createPrimitiveEntity("1990", pcontext);

                PrimitiveEntity pbob = theo.createPrimitiveEntity("bob", peverything);
                PrimitiveEntity ptokyo = theo.createPrimitiveEntity("tokyo", peverything);
                PrimitiveEntity pgojira = theo.createPrimitiveEntity("gojira", peverything);
                PrimitiveEntity pjapan = theo.createPrimitiveEntity("japan", peverything);

                pbob.addValue("livesin", ptokyo);
                pgojira.addValue("attacks",pjapan);
            } else if (cmd.equals("noop")) {
                //
            } else {
                System.out.println("Unrecognized command");
            }

            theo.close();
        } catch (Exception e) {
            log.fatal("Uncaught exception", e);
            System.exit(2);
        }
    }
}