package edu.cmu.ml.rtw.theo2012.core;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Pair;
import edu.cmu.ml.rtw.util.Properties;

// bkdb: shouldn't adjusting domain and range affect the values on the inverse automatically?  What's the policy if they don't match?<p>

/**
 * Basic standard initial implementation for {@link Theo2}<p>
 *
 * At present, it is not expected to need any alternative to this class unless, for instance, one
 * would want a Theo implementation internally different enough that it would not make sense to
 * separate Theo Layer 2 from Theo Layer 1 (as could be appropriate, for instance, if the underlying
 * storage engine already offered some or all of the features that Layer 2 adds to Layer 1.)<p>
 */
public class BasicTheo2 extends Theo2Base implements Theo2 {
    /**
     * Wraps an underlying Entity class, but extends its notion of acceptable Object values to
     * supply as Entity objects to include HFT0-style Matlab nested lists of entity names.<p>
     *
     * We also offer putValue methods here, which turn out to be a bit inconvenient to write in
     * Matlab.  It's not clear how widespread usage will be yet, nor whether we want the the "noop
     * if already there" behavior vs. the older "blow everything away first" behavior.<p>
     *
     * FODO: do the similarities with PointerInversingTheo1 etc. merit an extension of Theo1Base
     * that wraps Entity objects?  I suppose we'l have to wait for more outer layers to see how
     * regular the commonalitie wind up being.<p>
     */
    protected class MyEntity extends Entity1Base implements Entity {
        protected Entity wrappedEntity;

        /**
         * Used to check domain and range constraints when we are constraining to a primitive entity<p>
         *
         * See note on rangeCache about the possibility that the given primitive entity does not
         * exist.  Arguably, "correct" behavior here would be to say that the check passes because
         * such a constraint could not actually be stated in the KB.  But, for now, we'll call this
         * an unimportant edge case and let the check fail.<p>
         *
         * Checking ancestry seems like something we might want to cache later on.  Maybe even just
         * the most recently-compted value to cover adds of multiple values to a given (entity,
         * slot) pair.<p>
         *
         * If and only if queryOK is set, then toCheck will be permitted to be a composite entity.
         * In this case, then the check will behave as usual if toCheck is a primitive entity, but
         * it will also pass if toCheck is a query whose slot is a primitive entity that would pass
         * this check.  Additionally, if and only if queryOK is set and primitiveEntity is "" (our
         * special code for a domain setting of "belief" as a primitive string) then any toCheck
         * that is a belief will pass.<p>
         */
        protected boolean within(Entity toCheck, String primitiveEntity, boolean queryOK) {
            try {
                if (queryOK) {
                    if (toCheck.isQuery())
                        return within(toCheck.toQuery().getQuerySlot(), primitiveEntity, false);
                    if (toCheck.isBelief())
                        return true;
                }

                RTWLocation l = toCheck.getRTWLocation();
                if (toCheck.isPrimitiveEntity()) {
                    if (toCheck.toPrimitiveEntity().getName().equals(primitiveEntity))
                        return true;
                }
                for (Entity gen : toCheck.getQuery("generalizations").entityIter()) {
                    if (within(gen, primitiveEntity, false)) return true;
                }
                return false;
            } catch (Exception e) {
                throw new RuntimeException("within(" + toCheck + ", " + primitiveEntity + ", "
                        + queryOK + ")", e);
            }
        }

        /**
         * This is invoked prior to any add operation in order to enforce the constraints that we
         * enforce.<p>
         *
         * In general, this either succeeds as a no-op because the add operation does not violate
         * any constraint, or this throws an exception.  Side-effects are possible, such as in the
         * case of automatically adjusting masterinverse when inverse slot values are set such
         * that explicit manipulation of the masterinverse slot becomes unnecessary at this layer
         * of Theo except when the caller cares which slot is "master" and which is "slave".<p>
         */
        protected void trapAdd(Slot slot, RTWValue value) {
            try {
                String slotName = slot.getName();

                // We'll filter value by known metadata, if any, first.  Then we can make a
                // reasonably reasonable assumption below that we don't have to check nrofvalues,
                // domain, or range for the presence of multiple values.
                Boolean nrofvalues = nrofvaluesCache.get(slotName);
                if (nrofvalues != null && nrofvalues == true) {
                    if (getNumValues(slot) > 0)
                        throw new RuntimeException("nrofvalues=1 slot already contains a value");
                }
                String domainPrimitiveEntity = domainCache.get(slotName);
                if (domainPrimitiveEntity != null) {
                    if (!within(this, domainPrimitiveEntity, true))
                        throw new RuntimeException(toString() + " is not in domain "
                                + domainPrimitiveEntity + " of " + slot);
                }
                RTWValue range = rangeCache.get(slotName);
                if (range != null) {
                    if (value instanceof RTWThisHasNoValue) {
                        // RTWThisHasNoValue is always in range
                    } else if (range instanceof RTWStringValue) {
                        String rangeString = range.asString();
                        if (rangeString.equals("any")) {
                            // OK
                        } else if (rangeString.equals("integer")) {
                            if (!(value instanceof RTWIntegerValue))
                                throw new RuntimeException("Non-integer value is out of range");
                        } else if (rangeString.equals("double")) {
                            if (!(value instanceof RTWDoubleValue))
                                throw new RuntimeException("Non-double value is out of range");
                        } else if (rangeString.equals("string")) {
                            if (!(value instanceof RTWStringValue))
                                throw new RuntimeException("Non-string value is out of range");
                        } else if (rangeString.equals("boolean")) {
                            if (!(value instanceof RTWBooleanValue))
                                throw new RuntimeException("Non-boolean value is out of range");
                        } else if (rangeString.equals("list")) {
                            if (!(value instanceof RTWListValue))
                                throw new RuntimeException("Non-list value is out of range");
                        } else {
                            throw new RuntimeException("Internal error: unrecognized range setting "
                                    + range);
                        }
                    } else if (range instanceof PrimitiveEntity) {
                        String rangePrimitiveEntity = ((PrimitiveEntity)range).getName();
                        if (!(value instanceof Entity) || !within((Entity)value, rangePrimitiveEntity, false))
                                throw new RuntimeException(value + " is not in range " +
                                        rangePrimitiveEntity + " of " + slot);
                    } else {
                        throw new RuntimeException("Internal error: unrecognized range setting "
                                + range);
                    }
                }
                    
                // Update our metdata caches and apply automatic masterInverse logic, but only if
                // this entity is a slot.
                if (isSlot()) {
                    String entityName = toSlot().getName();

                    // In case of maintaininvere logic...
                    if (slotName.equals("inverse")) {
                        if (value instanceof Entity) {
                            // What we're going to do here is try to respect the current settings, if
                            // any, of masterinverse, so that, if the caller did begin to set it
                            // explicity, we don't blow that away.

                            // bkdb: see, and here we have nfc what layer of Theo this entity will be
                            // and if getQuery is going to do the "right thing" here or whether we need
                            // to convert it to our level before operating.
                            Entity them = (Entity)value;
                            // TODO: this can't be intoBoolean; we want to tolerate non-scalar or wrong type as a null indicating a non-setting
                            Boolean myMaintain = getQuery("masterinverse").into1Boolean();
                            if (myMaintain == null) {
                                Boolean theirMaintain = them.getQuery("masterinverse").into1Boolean();
                                if (theirMaintain == null) {
                                    // We want a symmetric slot to have its masterinverse to true.
                                    // So here we set the false before setting the true so that if
                                    // this entity is its own inverse it winds up with the final
                                    // value of true.
                                    them.deleteAllValues("masterinverse");
                                    them.addValue("masterinverse", RTWBooleanValue.FALSE);
                                    deleteAllValues("masterinverse");
                                    addValue("masterinverse", RTWBooleanValue.TRUE);
                                } else {
                                    deleteAllValues("masterinverse");
                                    addValue("masterinverse", new RTWBooleanValue(!theirMaintain));
                                }
                            } else {
                                them.deleteAllValues("masterinverse");
                                them.addValue("masterinverse", new RTWBooleanValue(!myMaintain));
                            }
                        }
                    }

                    else if (slotName.equals("nrofvalues")) {
                        if (value instanceof RTWIntegerValue && value.asInteger() == 1)
                            nrofvaluesCache.put(entityName, true);
                        else {
                            nrofvaluesCache.remove(entityName);
                            if (value.equals("any")) {
                                // OK
                            } else {
                                log.warn("Ignoring illegal nrofvalues setting of " + value
                                        + " for " + entityName);
                            }
                        }
                    }

                    else if (slotName.equals("domain")) {
                        if (value instanceof Entity) {
                            if (((Entity)value).isPrimitiveEntity()) {
                                domainCache.put(entityName,
                                        ((Entity)value).toPrimitiveEntity().getName());
                            } else {
                                domainCache.remove(entityName);
                                log.warn("Ignoring non-primitive entity domain setting of "
                                        + value + " for " + entityName);
                            }
                        } else if (value instanceof RTWStringValue && value.asString().equals("belief")) {
                            domainCache.put(entityName, "");
                        } else {
                            domainCache.remove(entityName);
                            log.warn("Ignoring illegal domain setting of "
                                    + value + " for " + entityName);
                        }
                    }

                    else if (slotName.equals("range")) {
                        if (value instanceof RTWStringValue) {
                            String rangeString = value.asString();
                            if (rangeString.equals("any")
                                    || rangeString.equals("integer")
                                    || rangeString.equals("double")
                                    || rangeString.equals("string")
                                    || rangeString.equals("boolean")
                                    || rangeString.equals("list")) {
                                rangeCache.put(entityName, value);
                            } else {
                                rangeCache.remove(entityName);
                                log.warn("Ignoring illegal range setting of " + value
                                        + " for " + entityName);
                            }
                        } else if (value instanceof Entity) {
                            if (((Entity)value).isPrimitiveEntity()) {
                                rangeCache.put(entityName, value);
                            } else {
                                rangeCache.remove(entityName);
                                log.warn("Ignoring non-primitive entity range setting of "
                                        + value + " for " + entityName);
                            }
                        } else {
                            rangeCache.remove(entityName);
                            log.warn("Ignoring illegal range setting of "
                                    + value + " for " + entityName);
                        }
                    }
                }                
            } catch (Exception e) {
                throw new RuntimeException("trapAdd(" + slot + ", " + value + ")", e);
            }
        }

        /**
         * This is invoekd after any delete operation in order to enforce the constraints that we
         * enforce
         *
         * This ought not ever to fail or throw an exception.  It exists to (try to) keep our
         * constraint-checking caches in sync.  See also the comment on rangeCache
         */
        protected void trapDelete(Slot slot, RTWValue value) {
            try {
                if (isSlot()) {
                    String slotName = slot.getName();
                    String entityName = toSlot().getName();

                    // We'll assume throughout that our nrofvalues=1 logic will be ensuring that
                    // deleting a value from this slot means that this slot is now empty.

                    if (slotName.equals("nrofvalues")) {
                        nrofvaluesCache.remove(entityName);
                    }
                    
                    else if (slotName.equals("domain")) {
                        domainCache.remove(entityName);
                    }

                    else if (slotName.equals("range")) {
                        rangeCache.remove(entityName);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("trapDelete(\"" + slot + "\", " + value + ")", e);
            }
        }

        /**
         * Extend {@link Entity0Base.toRTWValue} to pre-interpret types that MATLAB might pass in
         * that we might want to reinterpret as RTWValue types.
         */
        protected RTWValue toRTWValue(Object o) {
            try {
                if (o instanceof Object[]) {
                    Object[] l = (Object[])o;
                    RTWListValue lv = new RTWArrayListValue(l.length);
                    for (int i = 0; i < l.length; i++)
                        lv.add(toRTWValue(l[i]));
                    return lv;
                } else {
                    // Fall back to default interpretation
                    return super.toRTWValue(o);
                }
            } catch (Exception e) {
                throw new RuntimeException("toRTWValue(" + o + ")", e);
            }
        }

        @Override protected Slot toSlot(String name) {
            return t1.getSlot(name);
        }

        @Override protected Entity wrapEntity(Entity entity) {
            try {
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
            if (developerMode) s = s + "-TT2";
            return s;
        }

        @Override public int hashCode() {
            return wrappedEntity.hashCode();
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (wrappedEntity.equals(obj)) return true;
            if (!(obj instanceof MyEntity)) return false;
            // TODO: conditioned on KB: compare to parent class' this?
            return wrappedEntity.equals(((MyEntity)obj).wrappedEntity);
        }

        @Override public RTWLocation getRTWLocation() {
            return wrappedEntity.getRTWLocation();
        }

        @Override public boolean entityExists(Slot slot) {
            return wrappedEntity.entityExists(slot);
        }

        @Override public boolean entityExists() {
            return wrappedEntity.entityExists();
        }

        @Override public Collection<Slot> getSlots() {
            return wrappedEntity.getSlots();
        }

        @Override public Query getQuery(Slot slot) {
            return new MyQuery(wrappedEntity.getQuery(slot));
        }

        @Override public int getNumValues(Slot slot) {
            return wrappedEntity.getNumValues(slot);
        }

        @Override public RTWBag getReferringValues(Slot slot) {
            return wrappedEntity.getReferringValues(slot);
        }

        @Override public int getNumReferringValues(Slot slot) {
            return wrappedEntity.getNumReferringValues(slot);
        }

        @Override public Collection<Slot> getReferringSlots() {
            return wrappedEntity.getReferringSlots();
        }

        @Override public Belief getBelief(Slot slot, RTWValue value) {
            return getQuery(slot).getBelief(value);
        }

        @Override public boolean addValue(Slot slot, RTWValue value_) {
            RTWValue value = unwrapEntity(value_);
            trapAdd(slot, value);
            return wrappedEntity.addValue(slot, value);
        }

        @Override public boolean deleteValue(Slot slot, RTWValue value_) {
            RTWValue value = unwrapEntity(value_);
            boolean retval = wrappedEntity.deleteValue(slot, value);
            trapDelete(slot, value);
            return retval;
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
                throw new RuntimeException("Internal error: see comments in the source code.  This object self-identifies as a "
                        + getClass().getName() + ": " + toString());
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
     * Implementation of a Theo PrimitiveEntity object using MyEntity's added interpretation of
     * Object parameters<p>
     */
    protected class MyPrimitiveEntity extends MyEntity implements PrimitiveEntity {
        protected MyPrimitiveEntity(PrimitiveEntity wrappedPE) {
            super(wrappedPE);

            // FODO: For safety with MATLAB, we outlaw certain characters from being part of
            // primitive entity names.  We might should make this more standard, but I don't want to
            // jump to that just yet.  As long as HFT can escape things properly, it seems like we
            // should be able to stay generally out of trouble just by avoiding spaces, tabs, and
            // newlines.
            final String name = wrappedPE.getName();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r'
                        || c == '(' || c == ')' || c == '{' || c == '}' || c == ','
                        || c == '"' || c == '$' || c == '#' || c == '%')
                    throw new RuntimeException("Illegal character '" + c + "' in entity name "
                            + name);
            }
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
     * Implementation of a Theo Slot object using MyEntity's added interpretation of Object
     * parameters.
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
     * Implementation of a Theo Query object using MyEntity's added interpreatation of Object
     * parameters.
     */
    public class MyQuery extends MyEntity implements Query {
        protected MyQuery(Query wrappedQuery) {
            super(wrappedQuery);
        }

        @Override public Belief getBelief(RTWValue value_) {
            RTWValue value = unwrapEntity(value_);
            return new MyBelief(((Query)wrappedEntity).getBelief(value));
        }

        @Override public boolean addValue(RTWValue value_) {
            RTWValue value = unwrapEntity(value_);
            trapAdd(getQuerySlot(), value);
            return ((Query)wrappedEntity).addValue(value);
        }

        @Override public boolean deleteValue(RTWValue value_) {
            RTWValue value = unwrapEntity(value_);
            boolean retval = ((Query)wrappedEntity).deleteValue(value);
            trapDelete(getQuerySlot(), value);
            return retval;
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
                throw new RuntimeException("getBelief(" + value + ")", e);
            }
        }

        @Override public boolean addValue(Object value) {
            try {
                return addValue(toRTWValue(value));
            } catch (Exception e) {
                throw new RuntimeException("addValue(" + value + ")", e);
            }
        }

        @Override public Belief addValueAndGetBelief(Object value) {
            try {
                // Do a single conversion up front rather than be lazy
                RTWValue v = toRTWValue(value);
                addValue(v);
                return getBelief(v);
            } catch (Exception e) {
                throw new RuntimeException("addValueAndGetBelief(" + value + ")", e);
            }
        }

        @Override public boolean deleteValue(Object value) {
            try {
                return deleteValue(toRTWValue(value));
            } catch (Exception e) {
                throw new RuntimeException("deleteValue(" + value + ")", e);
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

        @Override public Belief addValueAndGetBelief(RTWValue value) {
            addValue(value);
            return getBelief(value);
        }

        @Override public Iterable<Belief> getBeliefs() {
            throw new RuntimeException("Not implemented");  // bkdb
        }
    }

    /**
     * Implementation of a Theo Belief object using MyEntity's added interpreatation of Object
     * parameters.
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
            throw new RuntimeException("Not implemented");  // bkdb
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
     * "Real" Theo1 that we wrap
     */
    protected Theo1 t1;

    /**
     * Cache of nrofvalues settings for all slots
     *
     * As as simplification and speedup, we only cache the nrofvalues setting for those slots having
     * a nrofvalues setting of 1.  A setting of "any" needs no constraint enforcement, which is also
     * the case when there is no setting.
     *
     * bkdb: a fast isSlot from the next layer down would allow us to do a better job expunging
     * values for slots that get deleted.  But we'll just play fast and lose with that for now.
     */
    protected Map<String, Boolean> nrofvaluesCache = new HashMap<String, Boolean>();

    /**
     * Cache of domain settings for all slots
     *
     * Value settings here are names of primitive entities, since that is (almost) the only valid
     * kind of domain setting.  We use a special case of the empty string to indicate a setting of
     * "belief" (as a primitive string).
     *
     * bkdb: a fast isSlot from the next layer down would allow us to do a better job expunging
     * values for slots that get deleted.  But we'll just play fast and lose with that for now.
     */
    protected Map<String, String> domainCache = new HashMap<String, String>();

    /**
     * Cache of range settings for all slots
     *
     * Value settings here have to be the very general RTWValue because it could be a primitive
     * entity or it could be one of several strings.  Our rule is that we only put in values that
     * are vetted as legal (to the extent that we can vet -- a primitive entity could be deleted
     * from the KB without us noticing that we ought to update this cache, for instance).
     *
     * bkdb: a fast isSlot from the next layer down would allow us to do a better job expunging
     * values for slots that get deleted.  But we'll just play fast and lose with that for now.
     *
     * bkdb: really, we'd have to trap auto-delete of domain and range settings (as from deletion of
     * the associated primitive value) in order to catch everything properly.  I guess in our case
     * it would be sufficient to auto-expuge settings during "within" calculations.  But, in
     * general, are we supposed to be doing something like caching Query objects (and maybe having
     * to re-fetch them in r/w)?
     */
    protected Map<String, RTWValue> rangeCache = new HashMap<String, RTWValue>();

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
            PrimitiveEntity e = t1.get(entity);
            Query q = e.getQuery(slot);
            int expectedNumValues = 1;
            if (value == null) {
                expectedNumValues = 0;
            } else {
                if (!q.containsValue(value)) {
                    if (autofix) {
                        e.addValue(slot, value);
                    } else {
                        log.error("KB lacks " + value + " in " + q);
                        expectedNumValues = 0;
                        ok = false;
                    }
                }
            }
            if (exactly && q.getNumValues() != expectedNumValues) {
                if (autofix) {
                    log.warn("Deleting spurious values in " + q);
                    boolean deletedSomething;
                    do {
                        deletedSomething = false;
                        for (RTWValue v : e.getQuery(slot).iter()) {
                            if (value != null && v.equals(value)) continue;
                            e.deleteValue(slot, v);
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

    protected boolean checkEssentials(boolean autofix) {
        // FODO: in the next design change, make it log that it's creating/fixing essential stuff
        // exactly once at the outset.
        if (isReadOnly() && autofix == true)
            throw new RuntimeException("Can't autofix in read-only mode");
        boolean ok = true;
        ok = ok && checkEssential("nrofvalues", "generalizations", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("domain", "generalizations", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("range", "generalizations", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("nrofvalues", "nrofvalues", new RTWIntegerValue(1), true, autofix);
        ok = ok && checkEssential("domain", "nrofvalues", new RTWIntegerValue(1), true, autofix);
        ok = ok && checkEssential("generalizations", "nrofvalues", new RTWStringValue("any"), true, autofix);
        ok = ok && checkEssential("masterinverse", "nrofvalues", new RTWIntegerValue(1), true, autofix);
        ok = ok && checkEssential("inverse", "nrofvalues", new RTWIntegerValue(1), true, autofix);

        ok = ok && checkEssential("masterinverse", "domain", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("inverse", "domain", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("nrofvalues", "domain", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("domain", "domain", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("range", "domain", t1.get("slot"), true, autofix);

        ok = ok && checkEssential("masterinverse", "range", new RTWStringValue("boolean"), true, autofix);
        ok = ok && checkEssential("inverse", "range", t1.get("slot"), true, autofix);
        ok = ok && checkEssential("nrofvalues", "range", null, true, autofix);
        ok = ok && checkEssential("domain", "range", null, true, autofix);
        ok = ok && checkEssential("range", "range", null, true, autofix);
        return ok;
        // bkdb: checkEssentials etc. should get a freshining up when all is said and done
    }

    /**
     * What we do when opening (or when we are constructed with an already-open store)
     */
    protected void initialize() {
        if (!checkEssentials(!isReadOnly()))
            throw new RuntimeException("KB lacks essential content.  Re-open in read/write mode to attempt repair.");
    }

    /**
     * Protected constructor -- use {@link TheoFactory} to obtain an instance
     */
    protected BasicTheo2(Theo1 t1) {
        // We don't actually have a properties file of our own yet.  The only properties that we're
        // interested in are sort of "global NELL" settings, which, for now, are accumulated in
        // MBL's properties file.  So we just continue that practice for the time being.  bk:prop
        Properties properties =
                Properties.loadFromClassName("edu.cmu.ml.rtw.mbl.MBLExecutionManager", null, false);
        developerMode = properties.getPropertyBooleanValue("developerMode", false);

        this.t1 = t1;
        if (t1.isOpen()) initialize();
    }

    protected void pre(PrintStream out, Entity entity) { // bkdb: are we moving things like pre out or what?
        try {
            RTWLocation l = entity.getRTWLocation();
            String indent = "";
            for (int i = 0; i < l.size(); i++) indent = indent + "  ";

            for (RTWValue s : entity.getSlots()) {
                String slot = s.asString();
                out.print(indent + slot + ": ");
                boolean first = true;
                Query q = entity.getQuery(slot);
                out.print(q.valueDump());
                out.print("\n");

                for (RTWValue v : entity.getQuery(slot).iter()) {
                    Entity sube = get(l.subslot(slot).element(v));
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

    protected void pre(Entity entity) {
        PrintStream out = System.out;
        String indent = "";
        RTWLocation l = entity.getRTWLocation();
        for (int i = 0; i < l.size(); i++) indent = indent + "  ";
        out.print(l + ":\n");
        pre(out, entity);
        out.print("\n");
    }


    ////////////////////////////////////////////////////////////////////////////
    // Public stuff
    ////////////////////////////////////////////////////////////////////////////

    @Override public boolean isOpen() {
        return t1.isOpen();
    }

    @Override public boolean isReadOnly() {
        return t1.isReadOnly();
    }

    @Override public void setReadOnly(boolean makeReadOnly) {
        t1.setReadOnly(makeReadOnly);
    }

    @Override public MyEntity get(RTWLocation location) {
        try {
            // We have to unwrap all of the entities in the given location -- this is going to be
            // the basis of our MyEntity.wrappedEntity variable, so we need everything in terms of
            // the next layer of Theo down.
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

            // And then we have to wrap the Entity from the next layer down before returning it, and
            // make it the most-derived type, since our implementation assumes that for speed in
            // places.
            Entity e = t1.get(new AbstractRTWLocation(newList));
            if (e.isSlot()) return new MySlot(e.toSlot());
            if (e.isPrimitiveEntity()) return new MyPrimitiveEntity(e.toPrimitiveEntity());
            if (e.isBelief()) return new MyBelief(e.toBelief());
            if (e.isQuery()) return new MyQuery(e.toQuery());
            return new MyEntity(e);
        } catch (Exception e) {
            throw new RuntimeException("get(" + location + ")", e);
        }
    }

    @Override public MyPrimitiveEntity get(String primitiveEntity) {
        return new MyPrimitiveEntity(t1.get(primitiveEntity));
    }
    
    @Override public Slot getSlot(String slotName) {
        // FODO: do we want to allow hypothetical slots?  Maybe the question is if there are any
        // ways in which allowing them gets us into legitimate trouble.
        return new MySlot(t1.getSlot(slotName));
    }

    @Override public MyPrimitiveEntity createPrimitiveEntity(String name, Entity generalization_) {
        try {
            Entity generalization;
            if (generalization_ instanceof MyEntity) {
                generalization = ((MyEntity)generalization_).wrappedEntity;
            } else {
                throw new RuntimeException("Given entity must be of type "
                        + MyEntity.class.getName() + ", not " + generalization_.getClass().getName());
            }

            return new MyPrimitiveEntity(t1.createPrimitiveEntity(name, generalization));
        } catch (Exception e) {
            throw new RuntimeException("createPrimitiveEntry(\"" + name + "\", " + generalization_);
        }
    }

    /**
     * Pretty-print an entity, recursively descending through all subslots and values<p>
     *
     * We will probably accumulate such general-purpose utilities elsewhere later on, but it should
     * be harmless and convenient to leave this here.<p>
     */
    public void printEntity(Entity entity) {
        pre(entity);
    }

    /**
     * Close the KB, which is essential for avoiding dataloss
     */
    @Override public void close() {
        t1.close();
        nrofvaluesCache.clear();
        domainCache.clear();
        rangeCache.clear();
    }

    /**
     * Part of ad-hoc santity check
     */
    protected static void constraintTest(BasicTheo2 t2) {
        try {
            PrimitiveEntity everything = t2.get("everything");
            PrimitiveEntity context = t2.get("context");
            PrimitiveEntity newcontext = t2.createPrimitiveEntity("newcontext", context);

            Slot nr1 = t2.createSlot("nr1", null, null);
            nr1.addValue("nrofvalues", 1);
            Slot nrany = t2.createSlot("nrany", null, null);
            nrany.addValue("nrofvalues", "any");
            Slot nrnone = t2.createSlot("nrnone", null, null);

            Slot dcontext = t2.createSlot("dcontext", null, null);
            dcontext.addValue("domain", context);
            Slot dnone = t2.createSlot("dnone", null, null);

            Slot rint = t2.createSlot("rint", null, null);
            rint.addValue("range", "integer");
            Slot rdouble = t2.createSlot("rdouble", null, null);
            rdouble.addValue("range", "double");
            Slot rstring = t2.createSlot("rstring", null, null);
            rstring.addValue("range", "string");
            Slot rboolean = t2.createSlot("rboolean", null, null);
            rboolean.addValue("range", "boolean");
            Slot rlist = t2.createSlot("rlist", null, null);
            rlist.addValue("range", "list");
            Slot rany = t2.createSlot("rany", null, null);
            rany.addValue("range", "any");
            Slot rcontext = t2.createSlot("rcontext", null, null);
            rcontext.addValue("range", context);
            Slot rnone = t2.createSlot("rnone", null, null);

            PrimitiveEntity test = t2.createPrimitiveEntity("test", everything);

            try {
                test.addValue(nr1, "first");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(nr1, "second");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(nrany, "first");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(nrany, "second");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(nrnone, "first");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(nrnone, "second");
            } catch (Exception e) {
                log.error("Exception", e);
            }


            try {
                context.addValue(dcontext, "domaintest");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                newcontext.addValue(dcontext, "domaintest");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                everything.addValue(dcontext, "domaintest");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                context.addValue(dnone, "domaintest");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                newcontext.addValue(dnone, "domaintest");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                everything.addValue(dnone, "domaintest");
            } catch (Exception e) {
                log.error("Exception", e);
            }


            try {
                test.addValue(rint, 3);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rint, "string");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rdouble, 3.2);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rdouble, "string");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rstring, "string");
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rstring, 4);
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rboolean, true);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rboolean, "string");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rlist, new RTWArrayListValue(new RTWStringValue("element1"), new RTWStringValue("element2")));
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rlist, "string");
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rany, 1);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rany, everything);
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rcontext, context);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rcontext, newcontext);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rcontext, everything);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rcontext, RTWThisHasNoValue.NONE);
            } catch (Exception e) {
                log.error("Exception", e);
            }

            try {
                test.addValue(rnone, 27);
            } catch (Exception e) {
                log.error("Exception", e);
            }
            try {
                test.addValue(rnone, context);
            } catch (Exception e) {
                log.error("Exception", e);
            }
        } catch (Exception e) {
            throw new RuntimeException("contraintTest(<Theo>)", e);
        }
    }

    /**
     * Ad-hoc testing fixture
     */
    public static void main(String[] args) throws Exception {
        BasicTheo2 t2 = new BasicTheo2(TheoFactory.openTheo1(args[0], false, true, null));

        if (false) {
            Entity e = t2.get("bob");
            Entity ev = t2.get("everything");
            Belief b = e.getBelief("generalizations", ev);
            System.out.println(b.entityExists());
        }

        if (false) {
            Entity jake = t2.get("jake");
            Entity everything = t2.get("everything");
            Entity slot = t2.get("slot");
            jake.addValue("generalizations", slot);
            jake.addValue("generalizations", everything);
        }

        if (false) {
            Entity bob = t2.get("bob");
            Entity everything = t2.get("everything");
            boolean success = bob.deleteValue("generalizations", everything);
            log.info("HELLO " + success);
        }

        if (false) {
            Entity bob = t2.get("bob");
            t2.createPrimitiveEntity("jake", bob);
            t2.deleteEntity(bob);
        }

        /*
        if (true) {
            Entity bob = t2.get("bob");
            Entity everything = t2.get("everything");
            ((MyEntity)bob).putValue("generalizations", everything);
        }
        */

        if (false) {
            constraintTest(t2);
        }

        t2.close();
    }
}
