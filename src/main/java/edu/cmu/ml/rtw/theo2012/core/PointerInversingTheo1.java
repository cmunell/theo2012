package edu.cmu.ml.rtw.theo2012.core;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Properties;

/**
 * Wraps a {@link Theo1} implementation that does not provide automatic updates of a slot's inverse
 * and uses the facilities of {@link Entity.getReferringValues} to provide a Theo1 implementation
 * wherein slave inverse slots appear to exist and have the appropriate values corresponding to
 * their masters.
 *
 * This is the "lowest" implementation of the Theo1 interface that application code might find
 * useful to use directly.
 *
 * FODO: defining our terms "master", "slave", etc.  Also, how much of this belongs in higher-level
 * documents?
 *
 * The point of having having this class work the way it does is so that the KB physically only
 * stores values in master slots for a speed and size gain, so that applications that care more
 * about speed can know how to operate only in terms of master slots so as to bypass the extra logic
 * use to make slave slots appear to exist, and so that applications that care more about simplicity
 * or generality can operate on a KB which acts as though it does actually physically store values
 * in slave slots.
 *
 * Note that using getReferringValues only applies to values that are Entity objecst.  As such, only
 * Entity values stored in master slots will appear to be present in slave slots, and storing things
 * other than Entity values in slave slots is therefore impossible.  No coincidence that this is
 * what the Theo spec specifies.
 *
 * This class is thread-safe in read-only mode because it has no state of its own that is mutated by
 * read operations, and because it is underpinned by a Theo1 object that has the same property.
 * This class is not thread-safe in read-write mode.
 *
 * 
 * DESIGNATING SLOTS TO RECEIVE AUTOMATIC INVERSING BEHAVIOR
 * ---------------------------------------------------------
 *
 * Simply defining the inverse of a slot by giving its entity an "inverse" slot with some value has
 * no effect as far as this layer of Theo is concerned.  As far as this layer of Theo is concerned,
 * you could go on to manually double-store everything using whatever value you put in that
 * "inverse" slot.  But if you want this layer of Theo to handle all that for you (and without
 * double-storing everything), then you will need exactly one value in that "inverse" slot, and it
 * must be an Entity value that names another slot (which must necessarily be a non-contextualized
 * primitive entity in order to be a slot).  On top of that, you must set a "masterInverse" value
 * for both entities, with one value being true to designate the master slot and the other value
 * being false to designate the slave.  Then this layer will reroute all reads and writes to either
 * the master or the slave into the master transparently so that you'd never know that everything
 * were not double-stored except that it's all smaller and faster and impossible to get out of sync.
 *
 * The value of the masterInverse should be an RTWBooleanValue.  For backward compatability, an
 * RTWStringValue "true" or "false" will also be accepted.  In the future, this layer of Theo is
 * likely to autoconvert RTWStringValues that it finds to RTWBooleanValues.
 *
 * In the case of a symmetric slot (that is, a slot that is its own inverse), masterInverse should
 * be set to true.  The underlying mechanism for this case is described below in greater detail.
 *
 *
 * WHICH VALUES APPEAR TO BE IN EACH SLOT
 * --------------------------------------
 *
 * One of the subtleties that this class brings to the table is that every belief (setting aside the
 * case of symmetric slots) can be expressed in two equivalent ways: one using the master slot and
 * one using its inverse slave.  Nested beliefs (e.g. a beilef whose arg1 is a belief) result in
 * exponential growth of possible equivalent ways to express the belief because of this, namely 2^N
 * ways where N is the number of nonsymmetric slots inolved.  Thus, it could be said that the KB,
 * from the view of the user of this class, would look like the below.  This is the Gojira example
 * from {@link TCHSuperStore}, with one additional assertion to illustrate the exponentiality: that
 * it was Yesterday that Mary accorded that Bob livesIn Tokyo.
 *
 * 1990
 *   Bob
 *     livesIn = <Tokyo>
 *       =<Tokyo>
 *         causes = <Gojira, attacks, =<Japan>>, <Japan, attackedBy, =<Gojira>>
 *         accordingTo = <Mary>
 *           =<Mary>
 *             atDate = Yesterday
 *
 * Mary
 *   accordsThat = <1990, Bob, livesIn, =<Tokyo>>, <Tokyo, livedInBy, =<1990 Bob>>
 *     =<1990, Bob, livesIn, =<Tokyo>>
 *       atDate = Yesterday
 *     =<Tokyo, livedInBy, =<1990, Bob>>
 *       atDate = Yesterday
 *
 * Yesterday
 *   dateOf = <1990, Bob, livesIn, =<Tokyo>, accordingTo, =<Mary>>,
 *            <Tokyo, livedInBy, =<1990, Bob>, accordingTo, =<Mary>>,
 *            <Mary, accordsThat, =<1990, Bob, livesIn, =<Tokyo>>,
 *            <Mary, accordsThat, =<Tokyo, livedInBy, =<1990, Bob>>
 *
 * Gojira
 *   attacks = <Japan>
 *     =<Japan>
 *       raisesQuestion = <Japan, onFire>
 *       causedBy = <1990, Bob, livesIn, =<Tokyo>>, <Tokyo, livedInBy, =<1990 Bob>>
 *
 * Japan
 *   onFire
 *     easilySolved = true
 *     questionRaisedBy = <Gojira, attacks, =<Japan>>, <Japan, attackedBy, =<Gojira>>
 *   attackedBy = <Gojira>
 *     =<Gojira>
 *       raisesQuestion = <Japan, onFire>
 *       causedBy = <1990, Bob, livesIn, =<Tokyo>>, <Tokyo, livedInBy, =<1990 Bob>>
 *
 * While logically legitimate, it's not always very useful to visit all possible evquivalent ways to
 * state a single belief when iterating through the beliefs associated with a given query.  Niether
 * is always generating them always a very good way to spend time.  So here we adopt the rule that,
 * when asking questions about the set of beliefs associated with a query, which includes iteration
 * and asking about the number of values, only the "canonical" form of each belief is used.
 * "Canonical" here means the form in which a slave slot is never said to have a value in it.  Thus,
 * when iterating through the example above, only these values will be visited:
 *
 * 1990
 *   Bob
 *     livesIn = <Tokyo>
 *       =<Tokyo>
 *         causes = <Gojira, attacks, =<Japan>>
 *         accordingTo = <Mary>
 *           =<Mary>
 *             atDate = Yesterday
 *
 * Mary
 *   accordsThat = <1990, Bob, livesIn, =<Tokyo>>
 *     =<1990, Bob, livesIn, =<Tokyo>>
 *       atDate = Yesterday
 *
 * Yesterday
 *   dateOf = <1990, Bob, livesIn, =<Tokyo>, accordingTo, =<Mary>>
 *
 * Gojira
 *   attacks = <Japan>
 *     =<Japan>
 *       raisesQuestion = <Japan, onFire>
 *       causedBy = <1990, Bob, livesIn, =<Tokyo>>
 *
 * Japan
 *   onFire
 *     easilySolved = true
 *     questionRaisedBy = <Gojira, attacks, =<Japan>>
 *   attackedBy = <Gojira>
 *     =<Gojira>
 *       raisesQuestion = <Japan, onFire>
 *       causedBy = <1990, Bob, livesIn, =<Tokyo>>
 *
 * Note that the existence check, "does belief X in the KB" will still return true if X is a
 * noncanonical form of a belief that does exist.  For instance, if we ask if the value <Japan,
 * attackedBy, =<Gojira>> exists in the <Japan, onFire, questionRaisedBy> slot, then this class will
 * return true.  In this sense, the existence check could be called "inferential".
 *
 * In keeping with this, adding a non-cononical belief when the canonical form already exists is a
 * no-op.  Also, deleting a non-canonical belief will delete all equivalent forms of that belief.
 *
 * If some application really wants the expoential set of all possible forms of each belief to be
 * present during iteration etc., that can pretty easily be done by writing another class that takes
 * the time and trouble to do that.  (Or perhaps it would be sensible to make that an option in this
 * class, or to make a subclass of this class.)
 *
 *
 * SYMMETRIC SLOTS
 * ---------------
 *
 * Symmetric slots pose a particular challange because we cannot use the slot to differentiate a
 * master (or "canonical") value from its inverse slave.  We may choose to address this problem
 * differently in the future, but, to start with, we solve it in two parts as follows.
 *
 * Firstly, we have a rule that a value stored in a symmetric slot will be explicitly stored twice
 * in the KB.  If "brother" is a symmetric slot, and somebody adds Steve brother =<Joe>, then we
 * automatically add Joe brother =<Steve> as well.  Deletes are doubled similarly in order to
 * maintain consistency.  This keeps read operations simple because each instance of the brother
 * slot already contains all values.  It also has a desirable bias toward fast reads because we do
 * not need need to look in two places for one answer.  Tradeoffs include slower writes and some
 * wasted KB space, but we anticipate these to be wortwhile, and also significantly marginalized by
 * an expected bias toward the use of non-symmetric slots.
 *
 * Secondly, because we still need to be able to answer the question of which form is "canonical"
 * (for instance, we need to know whether <Steve, brother, =<Joe>, subslot> should be read/written
 * as-is or canonicalized and redirected into <Joe, brother, =<Steve>, subslot>), we define a
 * function that determines canonicaliness given a (query, value) pair.  Further implementation
 * details of this function can be found in the {@link compare} method.  One of our chief a priori
 * goals in selecting this function is to avoid the need to perform a KB read in order to return a
 * value, meaning that we want to be able to determine canonicalness based only on the query and the
 * value.
 *
 * If our canonicalness function were to impose the requirement that the value must not be
 * lexographically lesser than the entity (which of course is an insufficiently complete definition
 * to cover all necessary cases), then our KB would look like this:
 *
 * Joe
 *   brother = <Steve>
 *     =<Steve>
 *       subslot1 = whatever
 *       subslot2 = whatever
 *
 * Steve
 *   brother = <Joe>
 *
 *
 * INTERNAL DESIGN COMMENTS
 * ------------------------
 *
 * FODO: When reading from symmetric slots, we skip flipping the query.  But would it save time to
 * skip the String.equals check on that and just always flip the query?  Probably splitting hairs
 * here.
 *
 * FODO: we're leaving these layers not totally safe in read-write mode becuase we'll want to be
 * able to play with that in situ when we can execute TCFs using these
 *
 * FODO: should we replace any of all this casting with anything better?  Matlab-style casting
 * helper methods?
 *
 * 2013-02: PointerInversingTheo1 and StoreInverselessTheo1 were written in such a way as to
 * tolerate being given Slot objects that are not their own.  It would be more correct for them to
 * require their own MySlot objects, but it's not worth the reimplementation costs at this point.
 */
public class PointerInversingTheo1 extends Theo1Base implements Theo1 {
    protected class MyEntity extends Entity1Base implements Entity {
        protected Entity wrappedEntity;

        /**
         * {@link Entity1Base} uses this to implement the fancier Entity methods
         */
        @Override protected MySlot toSlot(String name) {
            return getSlot(name);
        }

        /**
         * {@link Entity0Base} uses this to ensure that all Entity values that we return originate
         * from PointerInversingTheo1 and not from some lower layer of Theo.
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
                // Could do some more clever checks here, but we'll opt for simplicity and speed.
                MyEntity ent = get(entity.getRTWLocation());
                return ent;
            } catch (Exception e) {
                throw new RuntimeException("wrapEntity(" + entity + ")", e);
            }
        }

        protected MyEntity(Entity wrappedEntity) {
            this.wrappedEntity = wrappedEntity;
        }

        @Override public String toString() {
            String s = wrappedEntity.toString();
            if (developerMode) s = s + "-PIT1";
            return s;
        }

        @Override public int hashCode() {
            return wrappedEntity.hashCode();
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            // bkdb: don't want these, right? if (wrappedEntity.equals(obj)) return true;
            if (!(obj instanceof MyEntity)) return false;
            return wrappedEntity.equals(((MyEntity)obj).wrappedEntity);
            // TODO: conditioned on KB: compare to parent class' this?
        }

        @Override public RTWLocation getRTWLocation() {
            return wrappedEntity.getRTWLocation();
        }

        @Override public boolean entityExists(Slot slot) {
            try {
                MySlot master = slave2Master.get(slot.getName());
                if (master != null && !master.equals(slot)) {
                    // Behavior here seems iffy at first, but actually mirrors the other case.  If
                    // there are beliefs asserted that result in this inverse slave query having
                    // beliefs, then return true.  If there are subslots under this slave slot, then
                    // return true.  (And do that check in the other order on the assumption that
                    // the subslot check is less expensive, and potentially at least as important
                    // since the caller is not using getNumBeliefs.)
                    if (wrappedEntity.entityExists(slot)) return true;
                    if (wrappedEntity.getNumReferringValues(master) > 0)
                        return true;
                    return false;
                } else {
                    return wrappedEntity.entityExists(slot);
                }
            } catch (Exception e) {
                throw new RuntimeException("entityExists(" + slot + ") on " + toString(), e);
            }
        }

        @Override public boolean entityExists() {
            return wrappedEntity.entityExists();
        }

        @Override public Collection<Slot> getSlots() {
            Collection<Slot> subslots = wrappedEntity.getSlots();
            Collection<Slot> inverseSubslots = wrappedEntity.getReferringSlots();
            List<Slot> all = new ArrayList<Slot>(subslots.size() + inverseSubslots.size());
            for (Slot s : subslots)
                all.add(new MySlot(s));
            for (Slot s : inverseSubslots) {
                MySlot slave = master2Slave.get(s.getName());
                // Not all slots participate in a master/slave relationship.  We only want to show
                // those that do, and that aren't symmetric (because they will have already been
                // included above).  Fortunately, we constructed master2Slave such that symmetric
                // slots are not present.
                // bkdb oldcode if (slave != null && all.contains(slave) && !slave.getName().contains("specializations")) throw new RuntimeException("all already contains slave " + slave + " from invesreSubslot " + s + " at " + this);
                if (slave != null && all.contains(slave)) throw new RuntimeException("all already contains slave " + slave + " from invesreSubslot " + s + " at " + this);
                if (slave != null && !all.contains(slave)) all.add(slave);
            }
            return all;
        }

        @Override public Query getQuery(Slot slot) {
            try {
                // We know that wrappedEntity contains no inverse slave slots that we would want to
                // flip.  So it remains to either return a regular MyQuery if the given slot is not
                // an inverse slave, or return an MyInverseQuery if it is.
                //
                // In the case of a symmetric slot, all values stored in symmetric slots are
                // double-stored as both (E, S, V) and (V, S, E), so we can simply always use a
                // regular MyQuery.  Note, however, that subslots on a value stored in a symmetric
                // slot are only present on the canonical belief, and so getBelief may have to
                // create a new MyInverseQuery to base the new Belief object upon.
                String slotName = slot.getName();
                MySlot master = slave2Master.get(slotName);
                if (master != null && !master.getName().equals(slotName)) {
                    return new MyInverseQuery(wrappedEntity.getQuery(slot),
                            wrappedEntity.getReferringValues(master));
                } else {
                    // The point of wrapping the result in an MyQuery object is that then we
                    // know that it's already been checked for inverse slave slots that need to be
                    // flipped around.
                    return new MyQuery(wrappedEntity.getQuery(slot));
                }
            } catch (Exception e) {
                throw new RuntimeException("getQuery(" + slot + ") on " + toString(), e);
            }
        }

        @Override public int getNumValues(Slot slot) {
            try {
                // Same deal as for getQuery
                String slotName = slot.getName();
                MySlot master = slave2Master.get(slotName);
                if (master != null && !master.getName().equals(slotName)) {
                    return wrappedEntity.getNumReferringValues(master);
                } else {
                    return wrappedEntity.getNumValues(slot);
                }
            } catch (Exception e) {
                throw new RuntimeException("getNumValues(" + slot + ") on " + toString(), e);
            }
        }

        @Override public RTWBag getReferringValues(Slot slot) {
            try {
                // This is the opposite of getQuery.  We know wrappedEntity is canonical for our
                // purposes.  If we've been given a non-symmetric slave inverse, then flip things
                // around to issue a normal get.  Otherwise, fall through to getPointers on
                // wrappedEntity.
                String slotName = slot.getName();
                MySlot master = slave2Master.get(slotName);
                if (master != null && !master.getName().equals(slotName)) {
                    return new ReferringValuesWrapper(wrappedEntity.getQuery(master));
                } else {
                    return new ReferringValuesWrapper(wrappedEntity.getReferringValues(slot));
                }
            } catch (Exception e) {
                throw new RuntimeException("getReferringValues(" + slot + ") on " + toString(), e);
            }
        }

        @Override public int getNumReferringValues(Slot slot) {
            try {
                String slotName = slot.getName();
                MySlot master = slave2Master.get(slot);
                if (master != null && !master.getName().equals(slotName)) {
                    return wrappedEntity.getNumValues(master);
                } else {
                    return wrappedEntity.getNumReferringValues(slot);
                }
            } catch (Exception e) {
                throw new RuntimeException("getNumReferringValues(" + slot + ") on " + toString(), e);
            }
        }

        @Override public Collection<Slot> getReferringSlots() {
            Collection<Slot> slots = wrappedEntity.getReferringSlots();
            Collection<Slot> mySlots = new ArrayList<Slot>(slots.size());
            for (Slot slot : slots) mySlots.add(new MySlot(slot));
            return slots;
        }

        @Override public Belief getBelief(Slot slot, RTWValue value_) {
            try {
                // Can be sped up later if need be.  bkdb: make these default in the abstract base?
                // bkisiel 2014-11-03: taking this back out, but making a record of it just in case
                // I'm taking crazy pills.  I don't quite remember why I'd put it in the other day,
                // but this is only calling other PIT1 methods, so any unwrapping should be left to
                // them, right?
                //
                // RTWValue value = unwrapEntity(value_);
                return getQuery(slot).getBelief(value_);
            } catch (Exception e) {
                throw new RuntimeException("getBelief(" + slot + ", " + value_ + ")", e);
            }
        }

        /**
         *
         * Note that we don't need a read-only check here because the layer below us will check that
         * before we do anything that has any consequences
         */
        @Override public boolean addValue(Slot slot, RTWValue value_) {
            // FODO: I guess we're leaving modification of our essentials to an fsck rather than
            // trying to trap them, in keeping with the next layer down?
            
            // FODO: I guess resyncing all existing values can only be an fsck thing as well, eh?
            try {
                RTWValue value = unwrapEntity(value_);
                boolean returnValue = false;
                String slotName = slot.getName();
                MySlot master = slave2Master.get(slotName);
                if (master != null) {
                    // TODO: Some or all of this should go into the class-level documentation
                    // 
                    // Here we need to flip.  Say the caller is trying to assert ((Bob livesIn =
                    // <Tokyo>) accordingTo = <Mary>), and accordingTo is our slave that we just
                    // identified, and the master is accordsThat.  Then we need to instead assert
                    // (Mary accordsThat = <Bob, livesIn, =<Tokyo>>), where that value we're writing
                    // is an Entity.  Therefore:
                    //
                    // 1.: The value (here, <Mary>, i.e. an Entity object referring to the Mary
                    // entity.  We can't put an accordsThat slot on anything other than an Entity.
                    // We can't do anything better than throw an exception if it is not; the caller
                    // will have to figure out what he is truly trying to do, and how to better set
                    // up either his choice of which slot to be master or his choice of storing
                    // primitive values instead of pointers to entities.
                    //
                    // 2.: The entity referred to by the value (here, Mary) needs to be an existent
                    // entity.  But we will not check for that here; we'll let the Theo layer below
                    // us catch that and complain that Mary needs to generalize to something
                    // (i.e. exist) before Mary can have other slots on it.
                    //
                    // 3.: As with #2, per Theo's semantics, <Bob, livesIn> must have =<Tokyo> as
                    // one of its values.  But that's sensible anyway, because the caller shouldn't
                    // be thinking that he can attach an accordingTo slot to a nonexistent value.
                    // So, here again, we'll leave it to the layer below us to complain about that.

                    if (value instanceof RTWStringValue && value.asString().equals("")) {
                        log.error("bkdb: Ignoring attempt to add \"\" as an inverse value to "
                                + toString());
                        return true;
                    }
                    if (!(value instanceof Entity))
                        throw new RuntimeException("The given value must be an Entity object because the given slot is a slave inverse");
                    Entity entityValue = (Entity)value;

                    // Now, if this is a symmetric slot, first store the non-flipped version because
                    // symmetric beliefs need to be stored in both directions.
                    // log.debug("bkdb: addValue(" + slot + ", " + value + " master.getName()=" + master.getName() + " , slotName=" + slotName + " equals=" + master.getName().equals(slotName));
                    if (master.getName().equals(slotName)) {   // bkdb: seems it could really simplify to have Slot.equals required to be defined in terms of slot name, but I'd be willing to give it a pass if it's not justified by being needed elsewhere
                        log.debug("bkdb: symmetric case: adding " + wrappedEntity + " " + slot + " " + value);
                        returnValue = wrappedEntity.addValue(slot, value);
                    }

                    returnValue = entityValue.addValue(master, wrappedEntity) || returnValue;
                } else {
                    returnValue = wrappedEntity.addValue(slot, value);
                }

                // We need to keep slave2Master and master2Slave up to date, meaning that we need to
                // trap modifications to the inverse and masterinverse slots.  The value of
                // masterinverse is what determines whether or not the entry needs to be there,
                // and the value of inverse determines what the entry is.
                //
                // Technically, we should also require that the entity in question is a slot,
                // meaning that we'd have to trap modifications to the generalizations slot.  But,
                // to keep things faster and simpler, we won't do that because spurious entries in
                // our maps do not result in incorrect functionality (it's up to the layer below us
                // to require that those things used as slots are actually slots).  We'll just
                // restrict ourselves to primitive entities as a simpler and faster filter.
                //
                // Note that we do our updates after executing the add so that we only do the update
                // if the execution didn't fail in such a way as to throw an exception and.  This
                // way, we can also skip the update if the value added was already there.
                if (returnValue && wrappedEntity.getRTWLocation().size() == 1) {
                    if (slotName.equals("masterinverse") || slotName.equals("inverse")) {
                        // We use a single path of control flow for all cases here becuase there are
                        // many orders in which the user might have changed values.  To keep things
                        // implementationally simple, we take the following approach: delete any
                        // entries to do with this entity.  Then, check the current configuration of
                        // this entity, and, if it is found to be valid, add new entries as
                        // appropriate.

                        // First delete
                        String entityName = wrappedEntity.getRTWLocation().getPrimitiveEntity();
                        if (master != null) {
                            master2Slave.remove(master.getName());  // No-op if symmetric, but that's OK
                            try {
                                throw new RuntimeException("Dummy exception to get a stack trace");
                            } catch (Exception e) {
                                log.debug("bkdb: ALART: removing slave2Master entry for " + entityName, e);
                            }
                            slave2Master.remove(entityName);
                        } else {
                            master2Slave.remove(entityName);
                        }

                        // Attempt re-add.
                        //
                        // TODO: We're in a bind here because we'll inevitably encounter invalid
                        // config as the user sets things up.  Maybe we should define a "proper"
                        // order to set things, and only complain when they're set out of order?
                        // For now, I'm leaving this as a log.debug for debugging purposes as we
                        // begin to use this stuff for the first time.
                        String errmsg = addMasterSlaveEntries(entityName);
                        if (errmsg != null) log.debug(errmsg);
                    }
                }
                return returnValue;
            } catch (Exception e) {
                throw new RuntimeException("addValue(" + slot + ", " + value_ + ") on " + toString(), e);
            }
        }

        /**
         *
         * Note that we don't need a read-only check here because the layer below us will check that
         * before we do anything that has any consequences
         */
        @Override public boolean deleteValue(Slot slot, RTWValue value_) {
            try {
                RTWValue value = unwrapEntity(value_);
                boolean returnValue = false;
                RTWLocation wrappedLocation = wrappedEntity.getRTWLocation();
                String slotName = slot.getName();
                MySlot master = slave2Master.get(slotName);
                if (master != null) {
                    // This parallels add.  We flip things around to use the master slot and delete
                    // there.  And, if this is a symmetric slot, also delete to the given location
                    // as-is.
                    if (!(value instanceof Entity))
                        throw new RuntimeException("The given value must be an Entity because the given slot is a slave inverse");

                    // Now, if this is a symmetric slot, first delete the non-flipped version
                    // because symmetric beliefs need to be stored in both directions.
                    if (master.equals(slot))
                        returnValue = wrappedEntity.deleteValue(slot, value);

                    returnValue = ((Entity)value).deleteValue(master, wrappedEntity) || returnValue;
                } else {
                    returnValue = wrappedEntity.deleteValue(slot, value);
                }

                // We need to keep slave2Master and master2Slave up to date.  This case is a bit
                // easier than for addValue because it's safe to simply delete any relevant entries
                // that we have on file.
                if (wrappedEntity.getRTWLocation().size() == 1) {
                    if (slotName.equals("masterinverse") || slotName.equals("inverse")) {
                        String entityName = wrappedEntity.getRTWLocation().getPrimitiveEntity();
                        master = slave2Master.get(entityName);
                        if (master != null) {
                            master2Slave.remove(master.getName());  // No-op if symmetric, but that's OK
                            try {
                                throw new RuntimeException("Dummy exception to get a stack trace");
                            } catch (Exception e) {
                                log.debug("bkdb: ALART: removing slave2Master entry for " + entityName, e);
                            }
                            slave2Master.remove(entityName);
                        } else {
                            master2Slave.remove(entityName);
                        }
                    }
                }
                return returnValue;
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(" + slot + ", " + value_ + ") on " + toString(), e);
            }
        }

        @Override public boolean isQuery() {
            return wrappedEntity.isQuery();
        }

        @Override public Query toQuery() {
            // I don't think there's a way to get a query entity except through the construction of
            // a MyQuery or MyInverseQuery.  If there is, that probably represents a design
            // deficiency to consider more thoroughly.
            if (isQuery())
                throw new RuntimeException("Internal error: see comments in the source code");
            throw new RuntimeException(toString() + " is not a Query");
        }

        @Override public boolean isBelief() {
            return wrappedEntity.isBelief();
        }

        @Override public Belief toBelief() {
            // I don't think there's a way to get a Belief entity except through the construction of
            // a MyBelief.  If there is, that probably represents a design deficiency to consider
            // more thoroughly.
            if (isBelief())
                throw new RuntimeException("Internal error: see comments in the source code");
            throw new RuntimeException(toString() + " is not a Belief");
        }

        @Override public boolean isPrimitiveEntity() {
            return wrappedEntity.isPrimitiveEntity();
        }

        @Override public PrimitiveEntity toPrimitiveEntity() {
            return new MyPrimitiveEntity(wrappedEntity.toPrimitiveEntity());
        }

        @Override public boolean isSlot() {
            return wrappedEntity.isSlot();
        }

        @Override public Slot toSlot() {
            return new MySlot(wrappedEntity.toSlot());
        }
    }

    /**
     * Implementation of a Theo PrimitiveEntity object used by PointerInversingTheo1
     */
    protected class MyPrimitiveEntity extends MyEntity implements PrimitiveEntity {
        protected MyPrimitiveEntity(PrimitiveEntity wrappedPE) {
            super(wrappedPE);
        }

        @Override public boolean isPrimitiveEntity() {
            return true;
        }

        @Override public PrimitiveEntity toPrimitiveEntity() {
            return this;
        }

        @Override public String getName() {
            return ((PrimitiveEntity)wrappedEntity).getName();
        }
    }

    /**
     * Implementation of a Theo Slot object used by PointerInversingTheo1
     *
     * In read-only mode, the existence of one of these objects implies that it has been vetted as
     * an existent and legitimate slot
     *
     * It's tempting to forego a MySlot class and just use the Slot implementation of the underlying
     * Theo1 implementation.  But we need to get the behaviors of some of MyEntity's methods, and so
     * we are forced into having a MySlot afterall.
     */
    protected class MySlot extends MyPrimitiveEntity implements Slot {
        protected MySlot(Slot wrappedSlot) {
            super(wrappedSlot);
        }

        @Override public boolean isSlot() {
            return true;
        }

        @Override public Slot toSlot() {
            return this;
        }
    }

    /**
     *
     * The rule for one of these is that they do not end in inverse slave slots.  (Although we still
     * have to be careful if the slot is symmetric).  This is in keeping with the fact that MyQuery
     * is a subclass of MyEntity, and MyEntity employs no inverse slave slots that are used in a
     * value-holding capacity (they may be present as superslots that are not dereferenced,
     * however).
     *
     * Because we're saving keystrokes by extending MyEntity, we have to assign the Query we've been
     * given to wrappedEntity.  Then, to save needless duplication, we'll have to explicitly cast it
     * back to a Query in order to forward Query's method signature to it.  So, not only does Java
     * not allow us to do the equivalent of C++'s templatized subclassing, but it doesn't offer us
     * the ability to make wrappedEntity a "covariant" member variable.
     */
    public class MyQuery extends MyEntity implements Query {
        protected MyQuery(Query wrappedQuery) {
            super(wrappedQuery);
        }

        @Override public Belief getBelief(RTWValue value_) {
            // If this is a symmetric slot and the resulting belief is not in canonical form, then
            // we'll have to make a new MyInverseQuery and return a new Belief object based on that.
            RTWValue value = unwrapEntity(value_);
            Query queryToUse = this;
            String slotName = ((Query)wrappedEntity).getQuerySlot().getName();
            MySlot master = slave2Master.get(slotName);
            if (master != null && master.getName().equals(slotName)) {
                // I don't know if this is exactly right, but it's the wrong approach anyway
                // MyInverseQuery queryToUse = new MyInverseQuery((Query)wrappedEntity,
                //        ((Query)wrappedEntity).getQueryEntity().getReferringValues(master));

                // bkdb: I hope everything is this easy after the composition / subclassing revision
                RTWLocation cl = canonicalize(getRTWLocation().element(value));
                // log.debug("bkdb: getBelief " + toString() + " " + value + " canonicalizes to " + cl);
                return new MyBelief(theo1.get(cl).toBelief());
            }

            // OK, now construct and return the Belief
            return new MyBelief(((Query)wrappedEntity).getBelief(value));
        }

        @Override public boolean addValue(RTWValue value_) {
            try {
                // This follows MyEntity.addValue.  Maybe we should factor out the common parts.
                RTWValue value = unwrapEntity(value_);
                Query wrappedQuery = (Query)wrappedEntity;
                boolean returnValue;
                
                // We know slot is not an inverse slave, but we do have to check for symmetry and
                // then do a double-add if this is an Entity value.  Obvious oportunity for
                // precomputation if this turns out to be a hotspot.
                if (value instanceof Entity) {
                    String slotName = ((Query)wrappedEntity).getQuerySlot().getName();
                    MySlot master = slave2Master.get(slotName);
                    if (master != null && master.getName().equals(slotName)) {
                        // log.debug("bkdb: symmetric case: adding " + value + " " + slotName + " " + wrappedQuery.getQueryEntity());
                        ((Entity)value).addValue(slotName, wrappedQuery.getQueryEntity());
                    }
                }

                returnValue = wrappedQuery.addValue(value);

                // And then slave2Master and master2Slave.  Again with the precomputation
                // opporunity.
                //
                // We're going to get a little bit hackish here and work with the RTWLocation
                // directly to check if this is a query about a primitive entity, and to check the
                // name of the slot.  This does things like avoid constructing a Slot entity when we
                // don't need one.  We can come back and add add a better-designed way to do this
                // later // bk:contexts
                if (returnValue && wrappedEntity.getRTWLocation().size() == 2) {
                    String slotName = wrappedEntity.getRTWLocation().lastAsSlot();
                    if (slotName.equals("masterinverse") || slotName.equals("inverse")) {
                        String entityName = wrappedQuery.getQueryEntity().toPrimitiveEntity().getName();  // FODO: object creation overhead
                        MySlot master = slave2Master.get(entityName);
                        if (master != null) {
                            master2Slave.remove(master.getName());  // No-op if symmetric, but that's OK
                            try {
                                throw new RuntimeException("Dummy exception to get a stack trace");
                            } catch (Exception e) {
                                log.debug("bkdb: ALART: removing slave2Master entry for " + entityName, e);
                            }
                            slave2Master.remove(entityName);
                        } else {
                            master2Slave.remove(entityName);
                        }
                        String errmsg = addMasterSlaveEntries(entityName);
                        if (errmsg != null) log.debug(errmsg);
                    }
                }
                return returnValue;
                } catch (Exception e) {
                throw new RuntimeException("addValue(" + value_ + ") on " + toString(), e);
            }
        }

        @Override public boolean deleteValue(RTWValue value_) {
            try {
                // Follows MyEntity.deleteValue, except that we only need to worry about maybe doing
                // a double-delete in case of deleting an Entity from a symmetric slot (because we
                // store a Belief in which the value is an Entity in both "forward" and "backward"
                // forms in symmetric slots).
                RTWValue value = unwrapEntity(value_);
                Query wrappedQuery = (Query)wrappedEntity;

                boolean returnValue;
                RTWLocation wrappedLocation = wrappedEntity.getRTWLocation();
                String slotName = wrappedQuery.getQuerySlot().getName();  // FODO: object creation overhead

                // Handle an Entity in a symmetric slot.
                if (value instanceof MyEntity) {
                    MySlot master = slave2Master.get(slotName);
                    if (master != null && master.getName().equals(slotName)) {
                        if (value_ == null) {
                            log.error("Given value " + value + " to delete from " + toString()
                                    + " is not an Entity from this KB.  Not attempting to delete from symmetric inverse");
                        } else {
                            ((Entity)value).deleteValue(master, wrappedQuery.getQueryEntity());
                        }
                    }
                }

                returnValue = wrappedQuery.deleteValue(value);

                if (wrappedQuery.getRTWLocation().size() == 2) {
                    if (slotName.equals("masterinverse") || slotName.equals("inverse")) {
                        String entityName = wrappedQuery.getQueryEntity().toPrimitiveEntity().getName();  // FODO: object creation overhead
                        MySlot master = slave2Master.get(entityName);
                        if (master != null) {
                            master2Slave.remove(master.getName());  // No-op if symmetric, but that's OK
                            try {
                                throw new RuntimeException("Dummy exception to get a stack trace");
                            } catch (Exception e) {
                                log.debug("bkdb: ALART: removing slave2Master entry for " + entityName, e);
                            }
                            slave2Master.remove(entityName);
                        } else {
                            master2Slave.remove(entityName);
                        }
                    }
                }
                return returnValue;
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(" + value_ + ") on " + toString(), e);
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

        @Override public Entity getQueryEntity() {
            Entity e = ((Query)wrappedEntity).getQueryEntity();
            if (e.isBelief()) return new MyBelief(e.toBelief());
            if (e.isQuery()) return new MyQuery(e.toQuery());
            if (e.isSlot()) return new MySlot(e.toSlot());
            if (e.isPrimitiveEntity()) return new MyPrimitiveEntity(e.toPrimitiveEntity());
            return new MyEntity(e);
        }
        
        @Override public Slot getQuerySlot() {
            return new MySlot(((Query)wrappedEntity).getQuerySlot());
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

        @Override public boolean isQuery() {
            return true;
        }

        @Override public Query toQuery() {
            return this;
        }

        ////////////////////////////////////////////////////////////////////////
        // For the most part, forward RTWBag methods to our member variable "wrappedEntity" (which
        // we have to cast to Query as described in our constructor)
        //
        // We do have to filter some of the return values through wrapEntity, though, so make sure
        // that we return MyEntity instances and not Entity instances from the layer below us.
        ////////////////////////////////////////////////////////////////////////

        @Override public int getNumValues() {
            return ((Query)wrappedEntity).getNumValues();
        }

        @Override public String valueDump() {
            // This ensures that the entities get wrapped before dumped
            return SimpleBag.valueDump(this);
        }

        @Override public boolean isEmpty() {
            return ((Query)wrappedEntity).isEmpty();
        }

        @Override public boolean has1Value() {
            return ((Query)wrappedEntity).has1Value();
        }

        @Override public boolean has1String() {
            return ((Query)wrappedEntity).has1String();
        }

        @Override public boolean has1Integer() {
            return ((Query)wrappedEntity).has1Integer();
        }

        @Override public boolean has1Double() {
            return ((Query)wrappedEntity).has1Double();
        }

        @Override public boolean has1Boolean() {
            return ((Query)wrappedEntity).has1Boolean();
        }

        @Override public boolean has1Entity() {
            return ((Query)wrappedEntity).has1Entity();
        }

        @Override public Iterable<RTWValue> iter() {
            return wrapEntities(((Query)wrappedEntity).iter());
        }

        @Override public Iterable<RTWBooleanValue> booleanIter() {
            return ((Query)wrappedEntity).booleanIter();
        }

        @Override public Iterable<RTWDoubleValue> doubleIter() {
            return ((Query)wrappedEntity).doubleIter();
        }

        @Override public Iterable<RTWIntegerValue> integerIter() {
            return ((Query)wrappedEntity).integerIter();
        }

        @Override public Iterable<RTWStringValue> stringIter() {
            return ((Query)wrappedEntity).stringIter();
        }

        @Override public Iterable<Entity> entityIter() {
            return wrapEntities2(((Query)wrappedEntity).entityIter());
        }

        @Override public RTWValue into1Value() {
            return wrapEntity(((Query)wrappedEntity).into1Value());
        }

        @Override public String into1String() {
            return ((Query)wrappedEntity).into1String();
        }
       
        @Override public Integer into1Integer() {
            return ((Query)wrappedEntity).into1Integer();
        }

        @Override public Double into1Double() {
            return ((Query)wrappedEntity).into1Double();
        }

        @Override public Boolean into1Boolean() {
            return ((Query)wrappedEntity).into1Boolean();
        }

        @Override public Entity into1Entity() {
            return wrapEntity(((Query)wrappedEntity).into1Entity());
        }

        @Override public RTWValue need1Value() {
            return wrapEntity(((Query)wrappedEntity).need1Value());
        }

        @Override public boolean need1Boolean() {
            return ((Query)wrappedEntity).need1Boolean();
        }

        @Override public double need1Double() {
            return ((Query)wrappedEntity).need1Double();
        }

        @Override public int need1Integer() {
            return ((Query)wrappedEntity).need1Integer();
        }

        @Override public String need1String() {
            return ((Query)wrappedEntity).need1String();
        }

        @Override public Entity need1Entity() {
            return wrapEntity(((Query)wrappedEntity).need1Entity());
        }

        @Override public boolean containsValue(RTWValue v) {
            return ((Query)wrappedEntity).containsValue(unwrapEntity(v));
        }

        @Override public Belief addValueAndGetBelief(RTWValue value) {  // bkdb: TODO: why do we need this if we already accept Object?
            try {
                addValue(value);
                return getBelief(value);
            } catch (Exception e) {
                throw new RuntimeException("addValueAndGetBelief(" + value + ") on " + toString(), e);
            }
        }

        @Override public Iterable<Belief> getBeliefs() {
            throw new RuntimeException("Not implemented");  // bkdb
        }
    }

    /**
     * Query object that we use to make inverse slave slots appear to have values in them.
     *
     * This class has two main parts.  One part is its behavior as a subclass of MyEntity, where it
     * serves to represent the apparent location of the values in an inverse slave slot.  The other
     * part is an RTWBag obtained from the underlying layer of Theo via {@link
     * Entity.getReferringValues} that has the values to which this class provides access.  It is
     * this RTWBag member variable that is used to add the methods that a Query must add to its
     * Entity superclass.  All of the methods fom Entity that we have to implement are to do with
     * subslots on this query, in which case we don't care that our apparent location ends in an
     * inverse slave slot.
     *
     * That leaves the non-RTWBag methods of Query that we have to implement, like
     * addValue(RTWValue), which do actually need to get flipped around to act via the master slot.
     * For implementational ease, we choose to extend MyEntity and then let the Query-specific
     * methods suffer a small speed penalty of having to compute the canonical location on which to
     * operate.  We can revisit this tradeoff as needed, but, in general, our answer is that people
     * who need to worry about the last degree of speed probably should not be operating through
     * this automatic-inversing logic.
     */
    public class MyInverseQuery extends MyEntity implements Query {
        /**
         * The values present in this query
         */
        protected RTWBag values;

        /**
         * Constructor
         *
         * The given Entity should be the one generated by the underlying SuperStore by asking it
         * for the slave inverse subslot that we're hiding.  For instance, if this Query object
         * represents <Gojira, attacks, =<Japan>, causedBy>, then that is the Query that should be
         * passed here.  It will be used as the wrappedEntity member of MyEntity, of which we
         * are a subclass, because then we won't have to override any of MyEntity's methods
         * because they will do exactly the corret thing given that value for wrappedEntity.
         *
         * The given RTWBag should be the one returned by a call to getPointers on the underlying
         * SuperStore that representes the RTWBag of pointers that exist for the location we
         * represent.  Following the above, it should be the RTWBag that contains one Entity object
         * referring to <1990, Bob, livesIn, =<Tokyo>>.
         */
        protected MyInverseQuery(Query e, RTWBag v) {
            super(e);
            this.values = v;
        }

        @Override public Belief getBelief(RTWValue value_) {
            try {
                // Here we have to flip back around to canonicalize the belief.
                RTWValue value = unwrapEntity(value_);
                if (!(value instanceof Entity))
                    throw new RuntimeException("Cannot construct a belief using a slave inverse query in which the value is not an Entity");

                String slotName = ((Query)wrappedEntity).getQuerySlot().getName();  // FODO: object creation overhead (or we could have Query carry around the Slot name, master, symmetry, etc.)
                MySlot master = slave2Master.get(slotName);
                if (master == null)  // TODO: deal with read-write metadata changes in existing objects
                    throw new RuntimeException("Missing entry for \"" + slotName
                            + "\" in slave2Master despite existence of a MyInverseQuery ending in it.");
                
                // log.debug("bkdb: getBelief on " + toString() + " " + value + " redirects to a getBelief on " + value_);

                // We could invoke getBelief on MyEntity instead of the entity that it wraps, but I
                // guess it probably saves a few cycles to skip that.
                Belief b = ((Entity)value).getBelief((Slot)master.wrappedEntity,
                        ((Query)wrappedEntity).getQueryEntity());
                return new MyBelief(b);
            } catch (Exception e) {
                throw new RuntimeException("getBelief(" + value_ + ") on " + toString(), e);
            }
        }

        @Override public boolean addValue(RTWValue value_) {
            try {
                // Here we have to flip back around to canonicalize the belief.
                RTWValue value = unwrapEntity(value_);
                if (!(value instanceof Entity))
                    throw new RuntimeException("Can't add things to slave inverse slots that aren't Entities");

                String slotName = ((Query)wrappedEntity).getQuerySlot().getName();  // FODO: object creation overhead
                MySlot master = slave2Master.get(slotName);
                if (master == null)  // TODO: deal with read-write metadata changes in existing objects
                    throw new RuntimeException("Missing entry for \"" + slotName
                            + "\" in slave2Master despite existence of a MyInverseQuery ending in it.");

                return ((Entity)value).addValue((Slot)master.wrappedEntity,
                        ((Query)wrappedEntity).getQueryEntity());
            } catch (Exception e) {
                throw new RuntimeException("addValue(" + value_ + ") on " + toString(), e);
            }
        }

        @Override public boolean deleteValue(RTWValue value_) {
            try {
                // Here we have to flip back around to canonicalize the belief.
                RTWValue value = unwrapEntity(value_);
                if (!(value instanceof Entity))
                    return false;  // Non-Entity values are never in slave inverse slots

                String slotName = ((Query)wrappedEntity).getQuerySlot().getName();  // FODO: object creation overhead
                MySlot master = slave2Master.get(slotName);
                if (master == null)  // TODO: deal with read-write metadata changes in existing objects
                    throw new RuntimeException("Missing entry for \"" + slotName
                            + "\" in slave2Master despite existence of a MyInverseQuery ending in it.");

                return ((Entity)value).deleteValue((Slot)master.wrappedEntity,
                        ((Query)wrappedEntity).getQueryEntity());
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(" + value_ + ") on " + toString(), e);
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

        @Override public Entity getQueryEntity() {
            Entity e = ((Query)wrappedEntity).getQueryEntity();
            if (e.isBelief()) return new MyBelief(e.toBelief());
            if (e.isQuery()) return new MyQuery(e.toQuery());
            if (e.isSlot()) return new MySlot(e.toSlot());
            if (e.isPrimitiveEntity()) return new MyPrimitiveEntity(e.toPrimitiveEntity());
            return new MyEntity(e);
        }

        @Override public Slot getQuerySlot() {
            return new MySlot(((Query)wrappedEntity).getQuerySlot());
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

        @Override public boolean isQuery() {
            return true;
        }

        @Override public Query toQuery() {
            return this;
        }

        ////////////////////////////////////////////////////////////////////////
        // For the most part, forward RTWBag methods to our member variable "values"
        //
        // We do have to filter some of the return values through wrapEntity, though, so make sure
        // that we return MyEntity instances and not Entity instances from the layer below us.
        ////////////////////////////////////////////////////////////////////////

        @Override public int getNumValues() {
            return values.getNumValues();
        }

        @Override public String valueDump() {
            // This ensures that the entities get wrapped before dumped
            return SimpleBag.valueDump(this);
        }

        @Override public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override public boolean has1Value() {
            return values.has1Value();
        }

        @Override public boolean has1String() {
            return values.has1String();
        }

        @Override public boolean has1Integer() {
            return values.has1Integer();
        }

        @Override public boolean has1Double() {
            return values.has1Double();
        }

        @Override public boolean has1Boolean() {
            return values.has1Boolean();
        }

        @Override public boolean has1Entity() {
            return values.has1Entity();
        }

        @Override public Iterable<RTWValue> iter() {
            return wrapEntities(values.iter());
        }

        @Override public Iterable<RTWBooleanValue> booleanIter() {
            return values.booleanIter();
        }

        @Override public Iterable<RTWDoubleValue> doubleIter() {
            return values.doubleIter();
        }

        @Override public Iterable<RTWIntegerValue> integerIter() {
            return values.integerIter();
        }

        @Override public Iterable<RTWStringValue> stringIter() {
            return values.stringIter();
        }

        @Override public Iterable<Entity> entityIter() {
            return wrapEntities2(values.entityIter());
        }

        @Override public RTWValue into1Value() {
            return wrapEntity(values.into1Value());
        }

        @Override public String into1String() {
            return values.into1String();
        }
       
        @Override public Integer into1Integer() {
            return values.into1Integer();
        }

        @Override public Double into1Double() {
            return values.into1Double();
        }

        @Override public Boolean into1Boolean() {
            return values.into1Boolean();
        }

        @Override public Entity into1Entity() {
            return wrapEntity(values.into1Entity());
        }

        @Override public RTWValue need1Value() {
            return wrapEntity(values.need1Value());
        }

        @Override public boolean need1Boolean() {
            return values.need1Boolean();
        }

        @Override public double need1Double() {
            return values.need1Double();
        }

        @Override public int need1Integer() {
            return values.need1Integer();
        }

        @Override public String need1String() {
            return values.need1String();
        }

        @Override public Entity need1Entity() {
            return wrapEntity(values.need1Entity());
        }

        @Override public boolean containsValue(RTWValue v) {
            return values.containsValue(unwrapEntity(v));
        }

        @Override public Belief addValueAndGetBelief(RTWValue value) {
            addValue(value);
            return getBelief(value);
        }

        @Override public Iterable<Belief> getBeliefs() {
            throw new RuntimeException("Not implemented");  //bkdb
        }
        
        // TODO: direct Query methods get jiggy with wrappedEntity
    }

    /**
     * Implementation of a Theo Belief object used by PointerInversingTheo1
     *
     * Same deal as in MyQuery with having to cast wrappedEntity to a Belief
     *
     * bkdb TODO: The current setup has MyBelief canonicalizing the whiteboard expression that it
     * represents, which can lead to the end user encountering unexpected and unsound behavior in
     * terms of whiteboard-centric manipulations.  I suspect that subqueries may be similarly prone.
     * But I'm going to try foraging ahead for now, and using a Belief object that maintains both an
     * underlying RTWLocation and (optional) apparant RTWLocation later on when this all gets
     * rearranged for the composition / subclassing fix.
     */
    protected class MyBelief extends MyEntity implements Belief {
        protected MyBelief(Belief wrappedBelief) {
            super(wrappedBelief);
        }

        @Override public Query getBeliefQuery() {
            return new MyQuery(((Belief)wrappedEntity).getBeliefQuery());
        }

        @Override public RTWValue getBeliefValue() {
            return wrapEntity(((Belief)wrappedEntity).getBeliefValue());
        }

        @Override public Entity getQueryEntity() {
            Entity e = ((Belief)wrappedEntity).getQueryEntity();
            if (e.isBelief()) return new MyBelief(e.toBelief());
            if (e.isQuery()) return new MyQuery(e.toQuery());
            if (e.isSlot()) return new MySlot(e.toSlot());
            if (e.isPrimitiveEntity()) return new MyPrimitiveEntity(e.toPrimitiveEntity());
            return new MyEntity(e);
        }

        @Override public Slot getQuerySlot() {
            return new MySlot(((Belief)wrappedEntity).getQuerySlot());
        }

        @Override public boolean isBelief() {
            return true;
        }
        
        @Override public Belief toBelief() {
            return this;
        }
        
        @Override public boolean hasMirrorImage() {
            throw new RuntimeException("Not Implemented");  // bkdb
        }
        
        @Override public Belief getMirrorImage() {
            throw new RuntimeException("Not Implemented");  // bkdb
        }
    }

    /**
     * Our log
     */
    private final static Logger log = LogFactory.getLogger();

    /**
     * Developer mode to control stuff like ugly outputs helpful to developers
     */
    protected final boolean developerMode;

    /**
     * TheoStore we wrap
     */
    protected StoreInverselessTheo1 theo1;  // FODO: generalize to Theo1

    /**
     * Complete set of inverse slave slots, and the masters of which they are the inverse.
     *
     * Symmetric slots are included in this map, and can be identified because they map to themselves.
     *
     * We use a key value of String in order to facilitate efficient lookups given the common case
     * where we have a Slot object that might or might not be a MySlot object; in the (ideal) case
     * of MySlot, using getName to get the corresponding String name is efficient for MySlot.  We
     * use a value type of MySlot in order to avoid the need to needlessly (re) MySlot objects to
     * use.
     */
    protected Map<String, MySlot> slave2Master = new HashMap<String, MySlot>();

    /**
     * Inverse of slave2Master, except that entries for symmetric slots are not present.
     *
     * We use a key value of String in order to facilitate efficient lookups given the common case
     * where we have a Slot object that might or might not be a MySlot object; in the (ideal) case
     * of MySlot, using getName to get the corresponding String name is efficient for MySlot.  We
     * use a value type of MySlot in order to avoid the need to needlessly (re) MySlot objects to
     * use.
     */
    protected Map<String, MySlot> master2Slave = new HashMap<String, MySlot>();

    /**
     * Used by computeS2MRecurse
     */
    protected Set<String> otherSet = new HashSet<String>();

    /**
     * Recursive ordering function used by isSymmetricCanonical
     *
     * bkdb TODO: should we not adjust this to reuse RTWValue's own compareTo?  Is there a good way
     * to do that in such a way that we can continue to treat an RTWLocation as an Entity?
     *
     * The o1 and o2 parameters may be any RTWValue subtype.  Alternatively, an RTWLocation may be
     * given, in which case it will be interpreted as having come from a MyEntity.  This returns a
     * negative value if o1 is less than o2, zero if they are considered to be equal, and a positive
     * value if o1 is greater than o2.
     *
     * We face the challange of comparing dissimilar types, and of lists in the form of both
     * RTWListValues and the RTWLocations representation of Entity objects, and of the nesting
     * thereof.  So, foremost, we define a global ordering among the possible RTWValue types so that
     * we needn't consider the question of how to choose an ordering based on the content of two
     * RTWValues of dissimilar type.  (And, considering the preponderance of lists and of nesting,
     * this will hopefully save us computational time as well.)  For simplicity, we will use a
     * global ordering that follows the lexographical ordering of the names of the different
     * RTWValue types.  Going from least to greatest, this is:
     *
     * RTWThisHasNoValue
     * RTWBooleanValue
     * RTWIntegerValue
     * RTWDoubleValue
     * RTWListValue
     * Entity
     * RTWStringValue
     * 
     * For boolean, integer, real, and string values, the ordering is easy.  (For boolean, we say
     * that true comes after false.)
     *
     * For lists, both RTWListValue, and the RTWLocation representation an Entity object, we say
     * that a longer list comes after a shorter list.  In the case of equal-length lists, we proceed
     * to do an element-by-element comparison, treating the earlier elements like digits of greater
     * significant value than the later.  That way, we only need to iterate through the lists to the
     * extent that they are exactly equal.  Encountering an RTWListValue or Entity necessarily means
     * recursion.
     *
     * We could apply this ordering to RTWValue globally by making RTWValue subtypes implement
     * Comparable, but I'm not sure that this particular ordering is one that we'd want to reuse
     * globally.  It might also be preferable to have this kind of cross-type comparison contained
     * in a single method rather than spread across many source files.
     */
    protected int compare(Object o1, Object o2) {
        try {
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
                        int c = compare(l1.get(i), l2.get(i));
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

            // If o1 was none of those types, then it should be an Entity or the RTWLocation from
            // within an Entity.  Either way, turn it into an RTWLocation so that we handle both
            // cases with the same code.
            RTWLocation l1;
            if (o1 instanceof RTWLocation) l1 = (RTWLocation)o1;
            else if (o1 instanceof Entity) l1 = ((Entity)o1).getRTWLocation();
            else
                throw new RuntimeException("Unrecognized type for o1: " + o1.getClass().getName());
            
            // Trivially less than string values
            if (o2 instanceof RTWStringValue) return -1;

            // Trivially greater unless o2 is also an Entity or its RTWLocation
            RTWLocation l2;
            if (o2 instanceof RTWLocation) l2 = (RTWLocation)o2;
            else if (o2 instanceof Entity) l2 = ((Entity)o2).getRTWLocation();
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
                        int c = compare(v1, v2);
                        if (c != 0) return c;
                    }
                }
            }
            return 0;  // Everything was equal
        } catch (Exception e) {
            throw new RuntimeException("compare(" + o1 + ", " + o2 + ")", e);
        }
    }

    /**
     * Returns true if the given belief that uses a symmetric slot is in canonical order, and false
     * if it would need to be argument-swapped in order to be canonical
     *
     * The basic idea for this implementation is to consider the (E, S, V) structure of a belief and
     * to define an ordering between E and V.  In order to compare RTWValue against RTWValue, we
     * consider E, which is fundamentally an RTWLocation, to be referring to that location.  A
     * belief is said to be in canonical form when we V does not come before E in our ordering.
     * 
     * We use whatever ordering is defined by {@link compare} above.
     */
    protected boolean isSymmetricCanonical(RTWLocation entity, RTWValue value) {
        return compare(entity, value) <= 0;
    }

    /**
     * Given an RTWLocation, returns it if it does not refer to any values stored in slave slots, or
     * returns an alternate RTWLocation that specifies the same semantic thing by refering to all
     * values only through mater slots.
     *
     * In the algorithm below, we perform the following steps of transformation to go from an
     * RTWLocation that uses all slave slots to all master slots, e.g.:
     *
     * Japan attackedBy =<Gojira> causedBy =<Tokyo livedInBy =<Bob>>
     * Gojira attacks =<Japan> causedBy =<Tokyo livedInBy =<Bob>>
     * Tokyo livedInBy =<Bob> causes =<Gojira attacks =<Japan>>
     * Bob livesIn =<Tokyo> causes =<Gojira attacks =<Japan>>
     *
     * Note that we'll have to admit the use of a slave slot in situations where it doesn't actually
     * hold any values.  For instance, there is no transformation we could apply to the folowing
     * query:
     * 
     * Tokyo livedInBy subslot
     *
     * Note also that we have to look inside of all MyEntity objects, including nested entities.
     * For instance, given:
     *
     * Bob livesIn =<Tokyo> causes =<Japan attackedBy =<Gojira>>
     *
     * Clearly, we would need to recurse into <Japan attackedBy =<Gojira>> in order to turn
     * attackedBy into attacks.
     *
     * Note that we face a special case when the slot is symmetric (i.e. when it is its own inverse,
     * i.e. when its entry in slave2Master is itself).  In that case, we use {@link
     * isSymmetricCanonical} to determine what to do.
     */
    protected RTWLocation canonicalize(RTWLocation location) {

        // Mechanics of this algorithm:
        // 
        // Each time around this while loop, we'll search left-to-right for an inverse slave slot
        // directly preceding an RTWElementRef, and flip it around to be a master if we find one.
        // We drop out of the while loop when we fail to find an inverse slave.  This could leave us
        // with the last element in location as an inverse slave slot, but we take care of that
        // afterward because it is a special case.
        //
        // Note that, in our left-to-right search, we have to recurse into the location that an
        // Entity object names because we might have another Entity nested within that location.
        // All Entity values, even nested ones, must refer to existent things in the KB, and only
        // master slots contain non-Entity values, so no Entity objects that refer to values in
        // slave slots are permitted.
        //
        // It's somewhat tempting to recursively explode out the RTWLocation into an Object[],
        // perform any necessary swaps within that Object[], and then create a new RTWLocation from
        // it if in fact we needed to do anything.  I suppose we might want, in that case, to
        // optimize for the common cases of simple RTWLocations that don't need any changes.  Maybe
        // we could take a quick jaunt through the RTWLocation and look for slave slots or nested
        // Entities, and do our big, expensive canonicalization only as needed.

        try {
            RTWLocation newLocation = location;
            while (true) {
                try {
                    // We can start at 1 because the first element can't be a slot.  We don't have
                    // any special handling for contexts here, and we don't really need any.  We
                    // really only care to distinguish between an inverse slave slot and "everything
                    // else".
                    RTWLocation replacement = null;   // Make non-null if we do a slave swap.
                    for (int i = 1; i < newLocation.size()-1; i++) {
                        if (!newLocation.isSlot(i+1)) {
                            String slot = newLocation.getAsSlot(i);
                            MySlot master = slave2Master.get(slot);
                            if (master != null) {
                                RTWValue eref = newLocation.getAsElement(i+1).getVal();

                                // Special case: if this is a symmetric slot, then skip the flipping
                                // that is about to happen if isSymmetricCanonical says that it's
                                // fine the way it is
                                if (master.getName().equals(slot)) {
                                    if (isSymmetricCanonical(newLocation.firstN(i), eref)) {
                                        // log.debug("bkdb: already canonical: "
                                        //         + newLocation.firstN(i) + " " + slot + " " + eref);
                                        i++;
                                        continue;
                                    }
                                }

                                // This means eref ought to be an Entity whose RTWLocation will
                                // become the beginning of our replacement newLocation.  Our new
                                // RTWLocation begins with referent, will be followed by our master
                                // slot, and then an RTWElementRef containing an Entity pointing to
                                // everything we've seen thusfar, followed by everything we haven't
                                // seen yet.  So if newLocation is this:
                                //
                                // E1 E2 S1 slave <E3 E4> more stuff
                                //
                                // Then we replace it with this:
                                //
                                // E3 E4 master <E1 E2 S1> more stuff

                                // But if eref is not an Entity, then we're stuck because we can't
                                // hang the master slot off of a primitive value; we can only hang a
                                // slot off of an entity.  So we have no choice but to throw an
                                // exception.
                                if (!(eref instanceof Entity))
                                    throw new RuntimeException("Value " + eref
                                            + " cannot be in inverse slave slot " + slot
                                            + " because it is not an Entity");
                                RTWLocation referent = ((Entity)eref).getRTWLocation();

                                // Now build the thing.  bkdb: come back later and make this faster.

                                // 2012-08 update: in the bigger picture, what makes this a mess
                                // when we write it in terms of Theo layers is that it can't quite
                                // be as simple as fumbling a whiteboard expression around.  The
                                // problem is that, in whiteboard terms, we have composite Entities
                                // that have other Entitie within them; but what we need at the end
                                // of it is a composite MyEntity with MyEntities within it, and, to
                                // get there, we have to go build the same thing out of the parlance
                                // objects of the layer below us.  So, while this started out
                                // looking like a bit of an awkard way to jockey and shift an
                                // RTWLoction object, what we really have to do is build up one of
                                // our own big MyEntity complexes by building up an Entity complex
                                // of the layer below us.  It's pathological unless we retain some
                                // analouge of Entity.get(RTWLocation) that accepts
                                // RTWPointerValue-based whiteboard-style expressions.
                                //
                                // But, on the other hand, it's worth noticing that only our
                                // get(RTWLocation) invokes this method afterall.  So maybe there's
                                // an even bigger picture out there that will get rid of the
                                // uglyness here by way of obviating the need to focus on
                                // get(RTWLocation).
                                //
                                // So, for now, we'll just drop down to the simplicity of
                                // RTWLocation with RTWPointerValue element references, knowing that
                                // we're going to be sitting on top of StoreInverselessTheo1 for the
                                // time being, and then we can come back later with at least some
                                // empirical legs to stand on.

                                replacement = referent.subslot(master.getName())
                                        .element(new RTWPointerValue(newLocation.firstN(i))); 
                                for (int j = i+2; j < newLocation.size(); j++) {
                                    if (newLocation.isSlot(j))
                                        replacement = replacement.append(newLocation.getAsSlot(j));
                                   else
                                        replacement = replacement.append(newLocation.getAsElement(j));
                                }
                                newLocation = replacement;
                                break;
                            }
                            else {
                                // Not a slave slot.  Keep searching forward.  And we can jump ahead one
                                // because we already know that i+1 is not going to be a slot.
                                i++;
                            }
                        } else {
                            // Next element is not an RTWElementRef, so we can't flip this slot around
                            // even if it is a slave inverse.

                            // But that also means that this element might be an RTWElementRef, and,
                            // if it contains an Entity, then we need to canonicalize the
                            // RTWLocation represented by that Entity.inside that RTWPointerValue
                            if (!newLocation.isSlot(i)) {
                                RTWValue v = newLocation.getAsElement(i).getVal();
                                // Here, we know that newLocation will have entity references only
                                // in the form of RTWPointerValue instances because that's how we
                                // build newLocation above..
                                if (v instanceof RTWPointerValue) {
                                    RTWLocation l = ((RTWPointerValue)v).getDestination();
                                    RTWLocation newl = canonicalize(l);
                                    if (newl != null) {
                                        // bkdb: come back later with a better splicing algorithm
                                        replacement = l.firstN(i);
                                        for (int j = i+1; j < newLocation.size(); j++) {
                                            if (newLocation.isSlot(j))
                                                replacement = replacement.append(newLocation.getAsSlot(j));
                                            else
                                                replacement = replacement.append(newLocation.getAsElement(j));
                                        }
                                        newLocation = replacement;
                                        break;
                                    }
                                }
                                
                                // ...or we were given an RTWLocation that we
                                // assume was built with MyEntity instances because its illegal for
                                // the application cod to supply us with somebody else's Entity
                                // objects.
                                else if (v instanceof MyEntity) {
                                    RTWLocation l = ((MyEntity)v).getRTWLocation();
                                    RTWLocation newl = canonicalize(l);
                                    if (newl != null) {
                                        // bkdb: come back later with a better splicing algorithm
                                        replacement = l.firstN(i);
                                        for (int j = i+1; j < newLocation.size(); j++) {
                                            if (newLocation.isSlot(j))
                                                replacement = replacement.append(newLocation.getAsSlot(j));
                                            else
                                                replacement = replacement.append(newLocation.getAsElement(j));
                                        }
                                        newLocation = replacement;
                                        break;
                                    }
                                }

                                else if (v instanceof Entity) {
                                    throw new RuntimeException("Foreign entity " + v + " detected");
                                }
                            }
                        }
                    }
                    // if (replacement != null)
                    //     log.debug("bkdb: now " + newLocation + ", was " + location);

                    // No need to re-search if we didn't replace newLocation with something new
                    if (replacement == null) break;
                } catch (Exception e) {
                    throw new RuntimeException("newLocation=" + newLocation, e);
                }
            }

            return newLocation;
        } catch (Exception e) {
            throw new RuntimeException("canonicalize(" + location + ")", e);
        }
    }

    /**
     * This is used to verify that RTWValues we are given that are Entities are in fact MyEntity
     * objects as we expect, and to unwrap them into the Entity objects of the Theo layer below
     * us.
     *
     * FODO: This could be extended to verify that they're from this Theo KB as well
     */
    protected RTWValue unwrapEntity(RTWValue value) {
        try {
            if (value instanceof Entity) {
                if (value instanceof MyEntity) {
                    return ((MyEntity)value).wrappedEntity;
                }
                throw new RuntimeException("Given entity must be of type "
                        + MyEntity.class.getName() + ", not " + value.getClass().getName());
            }
            return value;
        } catch (Exception e) {
            throw new RuntimeException("unwrapEntity(" + value + ")", e);
        }
    }

    /**
     * Helper function for checkEssentials
     */
    protected boolean checkEssential(String entity, String slot, RTWValue value, boolean exactly, boolean autofix) {
        try {
            if (value instanceof MyEntity)
                throw new RuntimeException("Entity objects must belong to the Theo layer below us because we are adding them directly");
            boolean ok = true;

            // We don't use our own Entity class etc. because the infrastructure they require might
            // not yet exist.
            PrimitiveEntity e = theo1.get(entity);
            Query q = e.getQuery(slot);
            int expectedNumValues = 1;
            if (!q.containsValue(value)) {
                if (autofix) {
                    log.debug("Adding " + value + " to " + q);
                    q.addValue(value);
                } else {
                    log.error("KB lacks " + value + " in " + q);
                    expectedNumValues = 0;
                    ok = false;
                }
            }
            if (exactly && q.getNumValues() != expectedNumValues) {
                if (autofix) {
                    log.warn("Deleting spurious values in " + q);
                    boolean deletedSomething;
                    do {
                        deletedSomething = false;
                        for (RTWValue v : q.iter()) {
                            if (v.equals(value)) continue;
                            log.debug("Deleting " + v + ", which is not " + value);
                            q.deleteValue(v);
                            deletedSomething = true;
                            break;
                        }
                    } while (deletedSomething);
                } else {
                    ok = false;
                    log.error("Spurious values detected in " + q);
                }
            }
            return ok;
        } catch (Exception e) {
            throw new RuntimeException("checkEssential(\"" + entity + "\", \"" + slot + "\", "
                    + value + ", " + exactly + ", " + autofix, e);
        }
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
        ok = ok && checkEssential("masterinverse", "generalizations", theo1.get("slot"), true, autofix);
        ok = ok && checkEssential("inverse", "generalizations", theo1.get("slot"), true, autofix);
        ok = ok && checkEssential("inverse", "inverse", theo1.get("inverse"), true, autofix);
        ok = ok && checkEssential("inverse", "masterinverse", RTWBooleanValue.TRUE, true, autofix);
        ok = ok && checkEssential("specializations", "generalizations", theo1.get("slot"), true, autofix);
        ok = ok && checkEssential("specializations", "inverse", theo1.get("generalizations"), true, autofix);
        ok = ok && checkEssential("specializations", "masterinverse", RTWBooleanValue.FALSE, true, autofix);

        // This next one is normally automatic because inverse's inverse is inverse, but
        // checkEssential works not through our own Entity objects but those of the next layer down
        // of Theo.  Therefore, we have to explicitly add the inverse in both directions here.
        ok = ok && checkEssential("generalizations", "inverse", theo1.get("specializations"), true, autofix);

        ok = ok && checkEssential("generalizations", "masterinverse", RTWBooleanValue.TRUE, true, autofix);
        return ok;
        // bkdb: checkEssentials etc. should get a freshining up when all is said and done
    }

    /**
     * Helper function that either returns the single boolean value in the given slot specified by
     * an RTWBooleanValue or RTWStringValue, or null.  value in the given slot, or null
     *
     * Illigitimate slot values will result in error messages about ignoring the current value(s),
     * and in a return value of null
     *
     * FODO: autorepair here or only in fsck or what?  Maybe we'll wind up with merged code
     *
     * FODO: the trapped modifications would probably prefer that this throws an exception
     */
    protected Boolean getBooleanScalar(String entity, String slot) {
        // bkdb: should be able to reduce further the use of theo1 after phase 2 is done, no?
        //// yeah, except for the wrapping and unwrapping
        Query q = get(entity).getQuery(slot);
        int numValues = q.getNumValues();
        if (numValues == 0) return null;
        if (numValues > 1) {
            log.error("Multiple values found for in the " + slot + " slot of " + entity
                    + ".  Ignoring them all.");
            return null;
        }
        RTWValue v = q.need1Value();
        if (v instanceof RTWBooleanValue) {
            return v.asBoolean();
        } else if (v instanceof RTWStringValue) {
            if (v.equals("true")) return true;
            if (v.equals("false")) return false;
            log.error("Ignoring the " + slot + " value of " + entity
                    + " because it is the uninterpretable string " + v);
            return null;
        } else {
            log.error("Ignoring the " + slot + " value of " + entity
                    + " because it does not denote a boolean value");
            return null;
        }
    }

    /**
     * Helper function that either returns the String name of the primitive entity pointed to by the
     * single value in the given slot, or null
     *
     * Illigitimate slot values will result in error messages about ignoring the current value(s),
     * and in a return value of null
     *
     * FODO: autorepair here or only in fsck or what?  Maybe we'll wind up with merged code
     *
     * FODO: Take a Slot as the second parameter?  (then again we only invoke this with "inverse"
     * atm)
     *
     * FODO: return Slot instead?
     */
    protected String getScalarAsPrimitiveEntityName(String entity, String slot) {
        Query q = theo1.get(entity).getQuery(slot);
        int numValues = q.getNumValues();
        if (numValues == 0) return null;
        if (numValues > 1) {
            log.error("Multiple values found for in the " + slot + " slot of " + entity
                    + ".  Ignoring them all.");
            return null;
        }
        RTWValue v = q.need1Value();
        if (!(v instanceof Entity)) {
            log.error("Ignoring the " + slot + " value of " + entity
                    + " because it is not a Entity");
            return null;
        }
        Entity ve = (Entity)v;  // bkdb: asEntity (and more?) on RTWValue?
        if (!ve.isPrimitiveEntity()) {
            log.error("Ignoring inverse value " + entity + " for " + slot
                    + " because it is not a primitive entity");
            return null;
        }
        // bkdb: 2012-11-01: working around to see if we can get away without giving RTWPointerValue the "full compliment" return ve.toPrimitiveEntity().getName();
        // 2013-02-14: When we decide RTWLocation's fate, it'd be neat if we could make RTWPointerValue no longer an Entity.  Because now we have to wrap and unwrap everything at every layer anyway.
        return ve.getRTWLocation().getPrimitiveEntity();
    }

    /**
     * Check the inverse and masterinverse settings for the given entity (and its inverse, if
     * applicable), and add entries to slave2Master and master2Slave as appropriate.
     *
     * This assumes that slave2Master and master2Slave have not already been populated with an entry
     * related to this entity (or its previous settings, if any).  In other words, use this only
     * after clearing those two maps or after deleting any entries relating to this entity.
     *
     * This returns an error message as to why the configuration found is invalid, or null if it is
     * not invalid.  The lack of any value for inverse and masterinverse is considered to be a
     * valid configuration.
     *
     * This assumes that "inverse" and "maintaininvere" are already correctly-set-up slots.
     * Subtlety: inverse is a slot that maintains its own inverse.  Ordinarily, this would mean that
     * we should not assume that it will be found on both the master and the slave.  But our rule
     * for symmetric slots like inverse is that we double-store them, so we can afterall assume it
     * to be present on both the master and the slave.
     *
     * FODO: we could take entity as a String for speed purposes by rejiggering some consequent
     * internals to use String as well.
     */
    protected String addMasterSlaveEntries(String entity) {
        try {
            String inverse = getScalarAsPrimitiveEntityName(entity, "inverse");
            Boolean isMaster = getBooleanScalar(entity, "masterinverse");
            
            // If this slot is not participating in our inversing system, make sure that it is
            // indeed not participating in our inversing system.
            if (isMaster == null) {
                MySlot master = slave2Master.get(entity);
                if (master != null) {
                    // FODO: is this an autorepair condition, then?
                            try {
                                throw new RuntimeException("Dummy exception to get a stack trace");
                            } catch (Exception e) {
                                log.debug("bkdb: ALART: removing slave2Master entry for " + entity, e);
                            }
                    slave2Master.remove(entity);
                    return "Inverse slave slot " + entity
                            + " lacks corresponding metadata.  Inversing disabled for " + master;
                }
                return null;
            }

            if (inverse == null) {
                return entity + " has a masterInverse value but does not specify an inverse";
            }
            if (!theo1.get(inverse).isSlot())
                return "Inverse " + inverse + " of " + entity + " is not a slot.";

            // Verify that the settings are reciprocal.
            String inverseInverse = getScalarAsPrimitiveEntityName(inverse, "inverse");
            Boolean inverseIsMaster = getBooleanScalar(inverse, "masterinverse");
            if (inverseInverse == null || !inverseInverse.equals(entity)) {
                try {
                    throw new RuntimeException("bkdb for non-reciprocal");
                } catch (Exception e) {
                    log.debug("bkdb", e);
                }
                return "Ignoring non-reciprocal inverse between " + entity + " and " + inverse;
            }
            if (!inverse.equals(entity)
                    && (inverseIsMaster == null || isMaster == inverseIsMaster)) {
                return "Ignoring inverse between " + entity + " and " + inverse +
                        " due to inconsistent masterinverse settings";
            } 

            // Finally, we're OK.  Note here that in case of a symmetric slot the entry goes in
            // slave2Master but not master2Slave, per our requirement.  Note that we always add
            // mappings in both directions because, typically, it will be only after the last
            // invokation of this method that the inverse/masterInverse settings are fully correct
            // for both the given entity and its inverse.
            if (isMaster) {
                if (!inverse.equals(entity))
                    master2Slave.put(entity, getSlot(inverse));
                slave2Master.put(inverse, getSlot(entity));
            } else {
                if (!inverse.equals(entity))
                    master2Slave.put(inverse, getSlot(entity));
                slave2Master.put(entity, getSlot(inverse));
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("addMasterSlaveEntries(\"" + entity + "\")", e);
        }
    }

    /**
     * Recursive guts of computeS2M.
     *
     * Proceses the given entity and then recurses into all entities that generalize to it.
     *
     * This will complain and reject recursion into anything that doesn't qualify as a primitive
     * entity (FODO: we'd arguably run a tighter ship by asking the layer below us for an iterator
     * over known slots, but that's unnecessary API complication at this point.)
     *
     * This assumes that "inverse" is a slot.  Subtlety: inverse is a slot that maintains its own
     * inverse.  Ordinarily, this would mean that we should not assume that it will be found on both
     * the master and the slave.  But our rule for symmetric slots like inverse is that we
     * double-store them, so we can afterall assume it to be present on both the master and the
     * slave.
     *
     * All conditionalities and requirements of {@link addMasterSlaveEntries} apply here.
     *
     * TODO: coordination of tolerance; we'll want in all of these things a mode that throws
     * exceptions.
     */
    protected void computeS2MRecurse(String entity) {
        try {
            // Terminate recursion if we've been here before
            if (master2Slave.get(entity) != null) return;
            if (slave2Master.get(entity) != null) return;
            if (!otherSet.add(entity)) return;

            String errmsg = addMasterSlaveEntries(entity);
            if (errmsg != null) log.debug(errmsg);

            // And then recurse
            for (RTWValue v : theo1.get(entity).getReferringValues("generalizations").iter()) { // bkdb: pointeriteration  // bkdb: legit for this to be cumbersome because we want entity as a string here, but quite possibly a good case study in ease
                // bkdb: isPrimitiveEntity etc.
                if (!(v instanceof Entity)) {
                    // Don't complain; we assume that the layer below us will have already done that.
                    continue;
                }
                if (!((Entity)v).isPrimitiveEntity()) {
                    // Don't complain; we assume that the layer below us will have already done that.
                    continue;
                }
                computeS2MRecurse(((Entity)v).toPrimitiveEntity().getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("computeS2MRecurse(<dst>, \"" + entity + "\")", e);
        }
    }

    /**
     * Repopulate slave2Master based on the content of the KB
     *
     * This performs a recursive traversal on all slots, and checks each one for an "inverse" slot.
     */
    protected void computeS2M() {
        log.debug("Collecting metdata for inversing layer...");
        slave2Master.clear();
        master2Slave.clear();
        otherSet.clear();
        computeS2MRecurse("slot");
        otherSet.clear();
        log.debug("bkdb:slave2Master=" + slave2Master.toString());
        log.debug("bkdb:master2Slave=" + master2Slave.toString());

        // FODO: fsck will have to check all uses of slave inverses
        // FODO: fsck will have to check for double-stored symmetrics (rule should be to add rather than remove in this case I suppose on grounds of erring toward retaining what might have been written by a lower layer without the benefit of this higher layer, and same, then, I suppose, when it comes to use of slave inverses)
    }

    /**
     * What we do when opening (or when we are constructed with an already-open store)
     */
    protected void initialize() {
        if (!checkEssentials(!isReadOnly()))
            throw new RuntimeException("KB lacks essential content.  Re-open in read/write mode to attempt repair.");
        computeS2M();
    }

    /**
     * Construct a new PointerInversingTheo1 based on the given StoreInverselessTheo1
     *
     * FODO: seems like a nice idea to generalize this to taking a Theo1.  We don't for now so as to
     * simplify coordination of open and close because they're not part of the Theo1 interface yet.
     */
    public PointerInversingTheo1(StoreInverselessTheo1 theo1) {
        // We don't actually have a properties file of our own yet.  The only properties that we're
        // interested in are sort of "global NELL" settings, which, for now, are accumulated in
        // MBL's properties file.  So we just continue that practice for the time being.  bk:prop
        Properties properties =
                Properties.loadFromClassName("edu.cmu.ml.rtw.mbl.MBLExecutionManager", null, false);
        developerMode = properties.getPropertyBooleanValue("developerMode", false);

        this.theo1 = theo1;
        if (theo1.isOpen()) initialize();
    }

    public void open(String filename, boolean openInReadOnlyMode) {
        try {
            theo1.open(filename, openInReadOnlyMode);
            initialize();
        } catch (Exception e) { 
            throw new RuntimeException("open(\"" + filename + "\", " + openInReadOnlyMode + ")", e);
        }
    }

    public boolean isOpen() {
        return theo1.isOpen();
    }

    public boolean isReadOnly() {
        return theo1.isReadOnly();
    }

    public void setReadOnly(boolean makeReadOnly) {
        theo1.setReadOnly(makeReadOnly);
    }

    public void flush(boolean sync) {
        theo1.flush(sync);
    }

    @Override public void close() {
        theo1.close();
    }

    @Override public MyEntity get(RTWLocation location) {
        try {
            if (location.size() == 0)
                throw new RuntimeException("Zero-length location");

            // Length == 1 means primitve entity, and so no opportunity for use of a slave inverse slot.
            if (location.size() == 1)
                return new MyPrimitiveEntity(theo1.get(location.getPrimitiveEntity()));

            // Then we have to unwrap all of the entities in the given location -- this is going to
            // be the basis of our MyEntity.wrappedEntity variable, so we need everything in terms
            // of the next layer of Theo down.
            Object[] newList = new Object[location.size()];
            for (int i = 0; i < location.size(); i++) {
                if (location.isSlot(i)) {
                    newList[i] = location.getAsSlot(i);
                } else {
                    RTWValue v = location.getAsValue(i);
                    if (v instanceof MyEntity) newList[i] = new RTWElementRef(unwrapEntity(v));
                    else newList[i] = location.getAsElement(i);
                }
            }
            location = new AbstractRTWLocation(newList);

            // Use canonicalize to get rid of all uses of a slave slot to refer to a value
            RTWLocation canonicalizedLocation = canonicalize(location);

            // If canonicalizedLocation is a query, then return some kind of Query object.  In
            // particular, if that ending slot is a slave inverse, then flip the whole thing around
            // and return a MyInverseQuery.  But skip this extra complexity in the case of a
            // symmetric slot.
            Entity e = theo1.get(canonicalizedLocation);
            if (e.isQuery()) {
                String slot = canonicalizedLocation.lastAsSlot();
                MySlot master = slave2Master.get(slot);
                if (master != null && !master.getName().equals(slot)) {
                    Entity pointedTo = theo1.get(canonicalizedLocation.parent());
                    return new MyInverseQuery(e.toQuery(), pointedTo.getReferringValues(master));
                } else {
                    return new MyQuery(e.toQuery());
                }
            } else {
                // Not a query.
                if (e.isSlot()) return new MySlot(e.toSlot());
                if (e.isPrimitiveEntity()) return new MyPrimitiveEntity(e.toPrimitiveEntity());
                if (e.isBelief()) return new MyBelief(e.toBelief());
                return new MyEntity(e);
            }
        } catch (Exception e) {
            throw new RuntimeException("get(" + location + ")", e);
        }
    }

    @Override public MyPrimitiveEntity get(String primitiveEntity) {
        // Primitve entity means no opportunity for use of a slave inverse slot.
        PrimitiveEntity pe1 = theo1.get(primitiveEntity);
        if (pe1.isSlot()) return new MySlot(pe1.toSlot());
        else return new MyPrimitiveEntity(pe1);
    }

    @Override public MySlot getSlot(String slotName) {
        return new MySlot(theo1.getSlot(slotName));
    }


    /**
     * Cleanup step used for now by Theo2012Converter
     *
     * Theo2012Converter needs to strip out of the KB all assertions using a symmetric slot that are
     * not in canonical form.  Otherwise, they remain as bits of invalidness clogging up the KB.  It
     * ought to be sufficient for our purposes with regard to the KB of the ongoing run to simply
     * delete all non-canonical assertions.  For those cases where they are not already asserted in
     * both directions (or for which a justification happens to not exist in the canonical
     * direction, which MBL will delete on sight), we should recover a correct canonical assertion
     * after the next round of learning where things largely get reasserted.  The idea is that those
     * things that fall through the cracks and wind up dropping out of the training data ought to be
     * marginal.
     *
     * The alternative might involve something like reasserting all beliefs using a symmetric slot,
     * such as by copying them to a temporary slot first, and that might be more work than we really
     * need to do.
     *
     * This is a recursive function that assumes that entity is not a query whose values need to be
     * checked for canonicalness.  If entity is a primitie entity, then this will recurse down the
     * specializations hierarchy.
     *
     * FODO: Presumably, this will be folded into some kind of more general fsck down the line,
     * although Theo2012Converter will still need to be able to invoke all or part of that in such a
     * way as to meet its needs.
     */
    public void deleteNonCanonicalSymmetrics(Entity entity) {
        try {
            // Drop down to the Entity type below us if we've been given one of our own objects
            if (entity instanceof MyEntity)
                entity = ((MyEntity)entity).wrappedEntity;

            // If this entity is a query ending in a symmetric slot, then it's showtime.
            if (entity.isQuery()) {
                Query query = entity.toQuery();
                Slot slot = query.getQuerySlot();
                MySlot master = slave2Master.get(slot.getName());
                if (master != null && slot.getName().equals(master.getName())) {
                    // Check each value in this slot.  If it is canonical, then assert the inverse
                    // form to ensure that all symmetric values are asserted in both directions.  If
                    // it is not canonical, then delete any subslots we find on it.
                    //
                    // We shouldn't need to worry about mutating what we're iterating over here.
                    String masterName = master.getName();
                    Entity qe = query.getQueryEntity();
                    RTWLocation qel = qe.getRTWLocation();
                    for (RTWValue v : query.iter()) {
                        if (isSymmetricCanonical(qel, v)) {
                            // Assert in inverse direction if not already there
                            if (!(v instanceof Entity)) {
                                // FODO: delete these beforehand because it's not convenient to do here
                                log.error("Ignoring non-entity value " + v + " in " + query);
                            } else {
                                Entity ve = (Entity)v;
                                Query vq = ve.getQuery(masterName);
                                if (!vq.containsValue(qe)) {
                                    log.info("Assert inverse of " + query + " " + v);
                                    //log.info("bkdb: " + vq + " is " + vq.valueDump() + " where " + qe + " is not found");
                                }
                            }
                        } else {
                            // Delete any subslots we find
                            for (Slot subslot : query.getBelief(v).getSlots()) {
                                log.info("Delete from non-canonical: " + query + " " + v + " " + subslot);
                            }
                        }
                    }
                }

                // Now recurse into any values in this query, regardless of whether or not the slot
                // is symmetric
                for (RTWValue v : query.iter()) {
                    deleteNonCanonicalSymmetrics(query.getBelief(v));
                }
            }

            // Recurse into all subslots of this entity
            for (Slot slot : entity.getSlots()) {
                deleteNonCanonicalSymmetrics(entity.getQuery(slot));
            }

            // Recurse into specializations if this is a primitive entity
            if (entity.isPrimitiveEntity()) {
                for (RTWValue spec : entity.getReferringValues("generalizations").iter()) {
                    deleteNonCanonicalSymmetrics((Entity)spec);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("deleteNonCanonicalSymmetrics(" + entity + ")", e);
        }
    }

    protected void pre(PrintStream out, Entity e) {
        try {
            RTWLocation l = e.getRTWLocation();
            String indent = "";
            for (int i = 0; i < l.size(); i++) indent = indent + "  ";

            for (Slot slot : e.getSlots()) {
                Query q = e.getQuery(slot);
                out.print(indent + slot + ": ");
                out.print(q.valueDump());
                out.print("\n");

                for (RTWValue v : e.getQuery(slot).iter()) {
                    Entity sube = e.getBelief(slot, v);
                    if (!sube.getSlots().isEmpty()) {
                        out.print(indent + "  =" + v + "\n");
                        pre(out, sube);
                    }
                }

                if (!q.getSlots().isEmpty())
                    pre(out, q);
            }
        } catch (Exception ex) {
            throw new RuntimeException("pre(<out>, " + e + ")", ex);
        }
    }

    protected void pre(RTWLocation l) {
        try {
            PrintStream out = System.out;
            String indent = "";
            for (int i = 0; i < l.size(); i++) indent = indent + "  ";
            out.print(l + ":\n");
            pre(out, get(l));
            out.print("\n");
        } catch (Exception e) {
            throw new RuntimeException("pre(" + l + ")", e);
        }
    }

    protected void pre(String primitive) {
        pre(new AbstractRTWLocation(primitive));
    }

    // bkdb: ditch these for the version in the layer below or what?
    protected void prer(RTWLocation l) {
        try {
            pre(l);
            for (RTWValue v : get(l).getQuery("specializations").iter()) {
                if (v instanceof Entity)
                    prer(((Entity)v).getRTWLocation());
            }
        } catch (Exception e) {
            throw new RuntimeException("prer(" + l + ")", e);
        }
    }

    /**
     * Testing fixture
     */
    public static void main(String args[]) throws Exception {
        try {
            String filename = args[0];
            String cmd = args[1];
            String locstr = null;
            if (args.length > 2) locstr = args[2];

            PointerInversingTheo1 theo;
            if (true || filename.contains(".mdb")) {
                theo = new PointerInversingTheo1(new StoreInverselessTheo1(new StringListSuperStore(new MapDBStoreMap())));
            } else {
                //bkb: reinstate this when TCH is in github: theo = new PointerInversingTheo1(new StoreInverselessTheo1(new StringListSuperStore(new TCHStoreMap())));
            }
            theo.open(filename, false);

            RTWLocation loc = null;
            Entity e = null;
            if (locstr != null) {
                loc = StoreInverselessTheo1.parseLocationArgument(locstr, theo);
                System.out.println("For location " + loc);
                e = theo.get(loc);
            }

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
                RTWValue value = StoreInverselessTheo1.parseValueArgument(args[4], theo);
                System.out.println(e.addValue(slot, value));
                System.out.println(e.getQuery(slot).valueDump());
            } else if (cmd.equals("deleteValue")) {
                String slot = args[3];
                RTWValue value = StoreInverselessTheo1.parseValueArgument(args[4], theo);
                e.deleteValue(slot, value);
                System.out.println(e.getQuery(slot).valueDump());
            } else if (cmd.equals("pre")) {
                theo.pre(loc);
            } else if (cmd.equals("prer")) {
                theo.prer(loc);
            } else if (cmd.equals("gojira") || cmd.equals("gojira1") || cmd.equals("gojira2")) {
                if (!cmd.equals("gojira2")) {
                    // Set up the Gojira example from the class-level comments (less the 1990 context,
                    // just to simplify slightly).  We also add generalizations, as required.  In this
                    // phase 1, we just create all the necessary entities and slots

                    theo.createSlot("livesin", null, "livedinby");
                    theo.createSlot("causes", null, "causedby");
                    theo.createSlot("accordingto", null, "accordsthat");
                    theo.createSlot("atdate", null, "dateof");
                    theo.createSlot("attacks", null, "attackedby");
                    theo.createSlot("raisesquestion", null, "questionraisedby");
                    theo.createSlot("onfire", null, null);
                    theo.createSlot("easilysolved", null, null);

                    Entity everything = theo.get("everything");
                    theo.createPrimitiveEntity("bob",  everything);
                    theo.createPrimitiveEntity("mary", everything);
                    theo.createPrimitiveEntity("tokyo", everything);
                    theo.createPrimitiveEntity("yesterday", everything);
                    theo.createPrimitiveEntity("gojira", everything);
                    theo.createPrimitiveEntity("japan", everything);
                }
                if (!cmd.equals("gojira1")) {
                    // phase 2: Add the relations

                    Entity bob = theo.get("bob");
                    Entity blt = bob.addValueAndGetBelief("livesin", theo.get("tokyo"));
                    Entity bltam = blt.addValueAndGetBelief("accordingto", theo.get("mary"));
                    Entity bltamay = bltam.addValueAndGetBelief("atdate", theo.get("yesterday"));

                    Entity gojira = theo.get("gojira");
                    Entity japan = theo.get("japan");
                    Entity gaj = gojira.addValueAndGetBelief("attacks", japan);
                    blt.addValue("causes", gaj);

                    // For japan onfire, note that we have to add the easilysolved value before we say
                    // that the question is raised, because the storing a pointer to the query requires
                    // that the query exit.  (Note that there is no such requirement if one wanted to
                    // store the query itself as some kind of RTWValue; it is only because of the
                    // pointerness of the value we're storing that we have to do this.)

                    Entity jo = japan.getQuery("onfire");
                    jo.addValue("easilysolved", true);
                    gaj.addValue("raisesquestion", jo);
                }
            } else if (cmd.equals("bkdb")) {
                theo.deleteNonCanonicalSymmetrics(e);
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
