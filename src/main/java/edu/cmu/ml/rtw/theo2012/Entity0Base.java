package edu.cmu.ml.rtw.theo2012;

import java.util.Iterator;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

/**
 * Abstract base class that provides boilerplate for Theo0 {@link Entity} implementations.<p>
 *
 * Even though Theo0 does not use an Entity interface that has a different API in terms of its
 * method signature, the API that it uses for Entity is beahviorally unique.  Specifically, for
 * Theo0, null is a permissible return value when an Entity object (or subclass thereof) would have
 * to be returned that does not correspond do something that actually exists in the KB.  This
 * abastract base class provides implementations catering to this state of affairs.  Entity
 * implementations for higher layers of Theo should use a different base class, e.g. {@link
 * Entity1Base}<p>
 *
 * bkdb: delete the comments below after NELL is running on Theo2012 and we consider possible Java 8
 * based simplifications.<br>
 * ----------------------------------------------------------------------------------------------<p>
 *
 * We'll see how things go using an abstract base class instead of an outer wrapper class to provide
 * convenience methods.  Presumed advantages include allowing each implementation to selectively
 * choose what to override and do differently, avoiding yet another layer of wrappers, and not
 * needing to face problems of how to wrap, unwrapd re-wrap as Theo objects get passed up and
 * down through higher and lower layers written for different interfaces.  Using wrapper classes in
 * any imagined future where an object might contain other objects that would then need to be
 * wrapped raises a particularly ghoulish spectre.<p>
 *
 * It's a bit early even for speculation, but Entity implementations might want to look at
 * implementing their own addValue and deleteValue methods for better speed, rather than using the
 * default implementations provided here that incur the potentially-unnecessary construction of a
 * Query object on which to invoke the add or delete.<p>
 *
 * Note that no QueryBase class is provided, nor other abstract bases for Slot, Belief, etc.  This
 * is because Java is too busy sucking hard to permit multiple inheritence, and Query
 * implementations are more likely to be interested in subclassing Entity implementations.  If they
 * were going to keep us from having multiple inheritence, why couldn't we then get "covariant
 * inheritence" to close this gap?  Fortunately, the methods that a QueryBase class would provide
 * are relatively few and small.<p>
 *
 * bkdb FODO: commentary on Entity-returning filter from 2013-02 refactor etc.<p>
 *
 * One of the bottom lines seems to be that all Entity objects returned must be passed through (the
 * overriden) wrapEntity so that what is actually returned is of the most derived type.<p>
 *
 * Implementations will typically have a corresponding unwrapEntity through which incoming Entity
 * parameters are passed.  This is a misnomer in that the real purpose is to verify that an Entity
 * object originating from the implementation in question as an error check.  The expected behavior
 * is that a higher Theo layer will unwrap its own Entity objects into those of the next layer down
 * before passing them down to the next layer.<p>
 */
public abstract class Entity0Base extends RTWValueBase implements Entity {
    /**
     * bkdb FODO 2013-02 stuff: for use with getReferringValues so that the RTWBag auto-wraps enties
     * into your entities
     */
    protected class ReferringValuesWrapper implements RTWBag {
        protected final RTWBag wrappedBag;

        public ReferringValuesWrapper(RTWBag bagToWrap) {
            wrappedBag = bagToWrap;
        }

        ////////////////////////////////////////////////////////////////////////
        // For the most part, forward RTWBag methods to our member variable "wrappedEntity" (which
        // we have to cast to Query as described in our constructor)
        //
        // We do have to filter some of the return values through wrapEntity, though, so make sure
        // that we return MyEntity instances and not Entity instances from the layer below us.
        ////////////////////////////////////////////////////////////////////////

        @Override public int getNumValues() {
            return wrappedBag.getNumValues();
        }

        @Override public String valueDump() {
            // This ensures that the entities get wrapped before dumped
            return SimpleBag.valueDump(this);
        }

        @Override public boolean isEmpty() {
            return wrappedBag.isEmpty();
        }

        @Override public boolean has1Value() {
            return wrappedBag.has1Value();
        }

        @Override public boolean has1String() {
            return wrappedBag.has1String();
        }

        @Override public boolean has1Integer() {
            return wrappedBag.has1Integer();
        }

        @Override public boolean has1Double() {
            return wrappedBag.has1Double();
        }

        @Override public boolean has1Boolean() {
            return wrappedBag.has1Boolean();
        }

        @Override public boolean has1Entity() {
            return wrappedBag.has1Entity();
        }

        @Override public Iterable<RTWValue> iter() {
            return wrapEntities(wrappedBag.iter());
        }

        @Override public Iterable<RTWBooleanValue> booleanIter() {
            return wrappedBag.booleanIter();
        }

        @Override public Iterable<RTWDoubleValue> doubleIter() {
            return wrappedBag.doubleIter();
        }

        @Override public Iterable<RTWIntegerValue> integerIter() {
            return wrappedBag.integerIter();
        }

        @Override public Iterable<RTWStringValue> stringIter() {
            return wrappedBag.stringIter();
        }

        @Override public Iterable<Entity> entityIter() {
            return wrappedBag.entityIter();
        }

        @Override public RTWValue into1Value() {
            return wrapEntity(wrappedBag.into1Value());
        }

        @Override public String into1String() {
            return wrappedBag.into1String();
        }
       
        @Override public Integer into1Integer() {
            return wrappedBag.into1Integer();
        }

        @Override public Double into1Double() {
            return wrappedBag.into1Double();
        }

        @Override public Boolean into1Boolean() {
            return wrappedBag.into1Boolean();
        }

        @Override public Entity into1Entity() {
            return wrappedBag.into1Entity();
        }

        @Override public RTWValue need1Value() {
            return wrapEntity(wrappedBag.need1Value());
        }

        @Override public boolean need1Boolean() {
            return wrappedBag.need1Boolean();
        }

        @Override public double need1Double() {
            return wrappedBag.need1Double();
        }

        @Override public int need1Integer() {
            return wrappedBag.need1Integer();
        }

        @Override public String need1String() {
            return wrappedBag.need1String();
        }

        @Override public Entity need1Entity() {
            return wrappedBag.need1Entity();
        }

        @Override public boolean containsValue(RTWValue v) {
            return wrappedBag.containsValue(v);
        }
    }

    /**
     * bkdb FODO 2013-02 stuff: wrapping an Iterator
     */
    protected class EntityWrappingIterator implements Iterator<RTWValue> {
        protected final Iterator<RTWValue> it;

        public EntityWrappingIterator(Iterator<RTWValue> it) {
            this.it = it;
        }

        public boolean hasNext() {
            return it.hasNext();
        }

        public RTWValue next() {
            RTWValue v = it.next();
            RTWValue wrapped = wrapEntity(v);
            return wrapped;
        }

        public void remove() {
            it.remove();
        }
    }

    /**
     * bkdb FODO 2013-02 stuff: wrapping an Iterable
     */
    protected class EntityWrappingIterable implements Iterable<RTWValue> {
        protected final Iterable<RTWValue> iterable;

        public EntityWrappingIterable(Iterable<RTWValue> iterable) {
            this.iterable = iterable;
        }

        public Iterator<RTWValue> iterator() {
            return new EntityWrappingIterator(iterable.iterator());
        }
    }

    /**
     * bkdb FODO 2013-02 stuff: wrapping an Iterator
     */
    protected class EntityWrappingIterator2 implements Iterator<Entity> {
        protected final Iterator<Entity> it;

        public EntityWrappingIterator2(Iterator<Entity> it) {
            this.it = it;
        }

        public boolean hasNext() {
            return it.hasNext();
        }

        public Entity next() {
            Entity v = it.next();
            Entity wrapped = wrapEntity(v);
            return wrapped;
        }

        public void remove() {
            it.remove();
        }
    }

    /**
     * bkdb FODO 2013-02 stuff: wrapping an Iterable
     */
    protected class EntityWrappingIterable2 implements Iterable<Entity> {
        protected final Iterable<Entity> iterable;

        public EntityWrappingIterable2(Iterable<Entity> iterable) {
            this.iterable = iterable;
        }

        public Iterator<Entity> iterator() {
            return new EntityWrappingIterator2(iterable.iterator());
        }
    }

    private final static Logger log = LogFactory.getLogger();

    @Override public Object clone() throws CloneNotSupportedException {
        try {
            throw new RuntimeException("bkdb: return hypothetical parlance here?");
        } catch (Exception e) {
            throw new RuntimeException("clone() on " + toString(), e);
        }
    }

    /**
     * bkdb FODO 2013-02 stuff
     */
    protected abstract Entity wrapEntity(Entity entity);

    /**
     * bkdb FODO 2013-02 stuff
     */
    protected RTWValue wrapEntity(RTWValue v) {
        if (v instanceof Entity) return wrapEntity((Entity)v);
        else return v;
    }

    /**
     * bkdb FODO 2013-02 stuff
     */
    protected Iterable<RTWValue> wrapEntities(Iterable<RTWValue> iterable) {
        return new EntityWrappingIterable(iterable);
    }

    /**
     * bkdb FODO 2013-02 stuff
     */
    protected Iterable<Entity> wrapEntities2(Iterable<Entity> iterable) {
        return new EntityWrappingIterable2(iterable);
    }

    /**
     * Implementations should override this in order to provide a way to come up with an instance of
     * a Slot object given a String containing its name.
     *
     * This should throw an exception if the given String does not name a legitimate Slot.
     *
     * This will be used by Entity0Base to implement methods taking a slot as a String.
     * Implementations will want to take care to convert Slot objects that are from other Theo
     * layers into Slot objects of their own where necessary, since a Slot object is also an Entity
     * object with all of Entity's various methods.
     */
    protected abstract Slot toSlot(String name);

    /**
     * Forwards to toSlot if a String is given, returns the given Slot if a Slot is given, and
     * throws an exception otherwise
     *
     * This makes no attempt to convert a Slot object to a Slot object generated by any particular
     * layer of Theo.  Implementations will want to take care to convert Slot objects that are from
     * other Theo layers into Slot objects of their own where necessary, since a Slot object is also
     * an Entity object with all of Entity's various methods.
     */
    protected Slot toSlot(Object name) {
        if (name instanceof Slot)
            return (Slot)name;
        if (name instanceof String)
            return toSlot((String)name);
        if (name instanceof Entity) {
            Entity e = (Entity)name;
            if (!e.isSlot())
                throw new RuntimeException(e + " is not a slot");
            return e.toSlot();
        }
        throw new RuntimeException("Unable to accept an object of type "
                + name.getClass().getName() + " to specify a slot.");
    }

    /**
     * Standard way to turn an Object into an RTWValue per the rules in Entity's class-level
     * comments
     *
     * FODO: Move this off to RTWValue?  I guess let's see how universally-applicable and
     * mono-dominant our conversion rules wind up being.
     */
    protected RTWValue toRTWValue(Object o) {
        try {
            if (o instanceof RTWValue) {
                // If we wanted to be more pedantic when it comes to checking for uses of Entity
                // objects that do not originate from this KB, then this might be an easy place to
                // perform such a check.  But we'll opt for speed by default.
                return (RTWValue)o;
            } else if (o instanceof String) {
                return new RTWStringValue((String)o);
            } else if (o instanceof Double) {
                return new RTWDoubleValue((Double)o);
            } else if (o instanceof Integer) {
                return new RTWIntegerValue((Integer)o);
            } else if (o instanceof Boolean) {
                return new RTWBooleanValue((Boolean)o);
            } else {
                throw new RuntimeException("Unrecognized type " + o.getClass().getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("toRTWValue(" + o + ")", e);
        }
    }

    /**
     * Standard (as of 2015) way to express an arbitrary Entity in a canonical, unambiguous,
     * human-friendly string.<p>
     *
     * This is what the current MATLAB Theo2012 implementation uses, what HFT uses, and quite nearly
     * what humans use on whiteboards and in papers.  Entity implementations should default to using
     * this implementation.<p>
     */
    @Override public String toString() {
        if (isPrimitiveEntity()) {
            return toPrimitiveEntity().getName();
        } else if (isBelief()) {
            Belief b = toBelief();
            String qstr = b.getBeliefQuery().toString();
            String vstr = b.getBeliefValue().toString();
            return "(" + qstr + " = " + vstr + ")";
        } else if (isQuery()) {
            Query q = toQuery();
            String estr = q.getQueryEntity().toString();
            return "(" + estr + " " + q.getQuerySlot().getName() + ")";
        } else {
            throw new RuntimeException("Unrecognized Entity subtype " + getClass().getName());
        }
    }

    /**
     * Special case deviant implementation for asString for backward compatability with
     * KbManipulation-era legacy code.<p>
     *
     * No new code should use this method.<p>
     *
     * If this is a primitive entity, then this returns what getName does, which is 100% consistent
     * with what used to happen with asString when we were storing primitive strings rather than
     * entity references.  Otherwise, this throws an exception.<p>
     *
     * We plan to revisit the use of and need for a method somewhat like this, and to perhaps do
     * something a bit more general in the future, like maybe providing a durable, fast, and
     * "friendly" (e.g. no whitespace) string for an arbitrary Theo entity.<p>
     */
    @Override public String asString() {
        if (isPrimitiveEntity())
            return toPrimitiveEntity().getName();
        throw new RuntimeException("This backward-comatability method only works for primitive entities.  This entity is "
                + toString());
    }

    @Override public String toPrettyString() {
        if (isPrimitiveEntity()) {
            return toPrimitiveEntity().getName();
        } else if (isBelief()) {
            Belief b = toBelief();
            String qstr = b.getBeliefQuery().toPrettyString();
            String vstr = b.getBeliefValue().toPrettyString();
            return "(" + qstr + " = " + vstr + ")";
        } else if (isQuery()) {
            Query q = toQuery();
            String estr = q.getQueryEntity().toPrettyString();
            return "(" + estr + " " + q.getQuerySlot().getName() + ")";
        } else {
            throw new RuntimeException("Unrecognized Entity subtype " + getClass().getName());
        }
    }

    @Override public long getId() {
    	throw new RuntimeException("Not implemented");
    }
    
    @Override public boolean entityExists(String slot) {
        try {
            return entityExists(toSlot(slot));
        } catch (Exception e) {
            throw new RuntimeException("entityExists(\"" + slot + "\")", e);
        }
    }

    @Override public Query getQuery(String slot) {
        try {
            return getQuery(toSlot(slot));
        } catch (Exception e) {
            throw new RuntimeException("getQuery(\"" + slot + "\")", e);
        }
    }

    @Override public int getNumValues(String slot) {
        try {
            return getNumValues(toSlot(slot));
        } catch (Exception e) {
            throw new RuntimeException("getNumValues(\"" + slot + "\")", e);
        }
    }

    @Override public RTWBag getReferringValues(String slot) {
        try {
            return getReferringValues(toSlot(slot));
        } catch (Exception e) {
            throw new RuntimeException("getReferringValues(\"" + slot + "\"", e);
        }
    }

    @Override public int getNumReferringValues(String slot) {
        try {
            return getNumReferringValues(toSlot(slot));
        } catch (Exception e) {
            throw new RuntimeException("getNumReferringValues(\"" + slot + "\"", e);
        }
    }

    @Override public Belief getBelief(Slot slot, RTWValue value) {
        try {
            return getQuery(slot).getBelief(value);
        } catch (Exception e) {
            throw new RuntimeException("getBelief(\"" + slot + "\", " + value + ")", e);
        }
    }

    @Override public Belief getBelief(Object slot, Object value) {
        try {
            return getQuery(toSlot(slot)).getBelief(value);
        } catch (Exception e) {
            throw new RuntimeException("getBelief(\"" + slot + "\", " + value + ")", e);
        }
    }

    @Override public Iterable<Belief> getBeliefs(Slot slot) {
        try {
            return getQuery(slot).getBeliefs();
        } catch (Exception e) {
            throw new RuntimeException("getBeliefs(" + slot + ")", e);
        }
    }

    @Override public Iterable<Belief> getBeliefs(String slot) {
        try {
            return getQuery(slot).getBeliefs();
        } catch (Exception e) {
            throw new RuntimeException("getBeliefs(\"" + slot + "\")", e);
        }
    }

    @Override public Iterable<Belief> getReferringBeliefs(Slot slot) {
        try {
            RTWBag vals = getReferringValues(slot);
            throw new RuntimeException("Not implemented");  // bkdb
        } catch (Exception e) {
            throw new RuntimeException("getReferringBeliefs(" + slot + ")", e);
        }
    }

    @Override public Iterable<Belief> getReferringBeliefs(String slot) {
        try {
            RTWBag vals = getReferringValues(slot);
            throw new RuntimeException("Not implemented");  // bkdb
        } catch (Exception e) {
            throw new RuntimeException("getReferringBeliefs(\"" + slot + "\")", e);
        }
    }
    
    @Override public boolean addValue(Object slot, Object value) {
        try {
            return addValue(toSlot(slot), toRTWValue(value));
        } catch (Exception e) {
            throw new RuntimeException("addValue(\"" + slot + "\", " + value + ")", e);
        }
    }

    @Override public Belief addValueAndGetBelief(Slot slot, RTWValue value) {
        try {
            addValue(slot, value);
            return getBelief(slot, value);
        } catch (Exception e) {
            throw new RuntimeException("addValueAndGetBelief(" + slot + ", " + value + ")", e);
        }
    }        

    @Override public Belief addValueAndGetBelief(Object slot, Object value) {
        try {
            // Do a single conversion up front rather than be lazy
            Slot s = toSlot(slot);
            RTWValue v = toRTWValue(value);
            return addValueAndGetBelief(s, v);
        } catch (Exception e) {
            throw new RuntimeException("addValueAndGetBelief(\"" + slot + "\", " + value + ")", e);
        }
    }

    @Override public boolean deleteValue(Object slot, Object value) {
        try {
            return deleteValue(toSlot(slot), toRTWValue(value));
        } catch (Exception e) {
            throw new RuntimeException("deleteValue(\"" + slot + "\", " + value + ")", e);
        }
    }

    @Override public boolean deleteAllValues(Slot slot) {
        try {
            // Use iterative fetch-next-and-delete approach so that we don't invite the risks of
            // iterating over the things we're modifying.  Deleting a value from a Query object does
            // not invalidate that Query object, so we don't need to reconstruct it.
            Query q = getQuery(slot);
            if (q == null) return false;
            int numDeleted = 0;
            boolean deletedSomething;
            do {
                deletedSomething = false;
                for (RTWValue v : q.iter()) {
                    q.deleteValue(v);
                    deletedSomething = true;
                    numDeleted++;
                    break;
                }
            } while (deletedSomething);
            return numDeleted > 0;
        } catch (Exception e) {
            throw new RuntimeException("deleteAllValues(\"" + slot + "\")", e);
        }
    }

    @Override public boolean deleteAllValues(String slot) {
        return deleteAllValues(toSlot(slot));
    }
}
