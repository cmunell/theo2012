package edu.cmu.ml.rtw.theo2012.core;

/**
 * Abstract base class that provides boilerplate for Theo1 {@link Entity} implementations.<p>
 *
 * This winds up being so similar to {@link Entity0Base} that it may seem pointless.  We justify its
 * existence primarily as room for future growth.  See {@link Entity0Base} for further commentary on
 * the role of abstract base classes for Entity and subclasses thereof.<p>
 */
public abstract class Entity1Base extends Entity0Base implements Entity {

    /**
     * It's a bit early even for speculation, but Entity implementations might want to look at
     * implementing their own addValue and deleteValue methods for better speed, rather than using
     * the default implementations provided here that incur the potentially-unnecessary construction
     * of a Query object on which to invoke the add or delete.<p>
     *
     * FODO: remove these?<p>
     */
    @Override public boolean addValue(Slot slot, RTWValue value) {
        try {
            Query q = getQuery(slot);
            return q.addValue(value);
        } catch (Exception e) {
            throw new RuntimeException("addValue(\"" + slot + "\", " + value + ")", e);
        }
    }

    /**
     * It's a bit early even for speculation, but Entity implementations might want to look at
     * implementing their own addValue and deleteValue methods for better speed, rather than using
     * the default implementations provided here that incur the potentially-unnecessary construction
     * of a Query object on which to invoke the add or delete.<p>
     *
     * FODO: remove these?<p>
     */
    @Override public boolean deleteValue(Slot slot, RTWValue value) {
        try {
            Query q = getQuery(slot);
            return q.deleteValue(value);
        } catch (Exception e) {
            throw new RuntimeException("deleteValue(\"" + slot + "\", " + value + ")", e);
        }
    }
}
