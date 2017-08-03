package edu.cmu.ml.rtw.theo2012;

import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import edu.cmu.ml.rtw.util.FasterLRUCache;
import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Properties;

/**
 * Abstract caching layer for {@link StringListStoreMap} implementations.
 *
 * {@link StringListStore} uses a {@link StringListStoreMap} implementation in order to provide a
 * {@link Store} object, which is a popular way to store a NELL or Theo2012 KB.  This class can be
 * used for those StringListStoreMap implementations that lack a cache, or that are based on a
 * storage mechanism that require the values stored to be immutable (e.g. MapDB).
 *
 * The immutabily issue is important because StringListStore sometimes stores mutable RTWListValue
 * objects.  This is done for the sake of speed to avoid the overhead of copying, constructing, and
 * destroying immutable objects when the same location is repeatedly updated.  This cache class, on
 * the other hand, does gaurantee the storage of only immutable objects.  In this usage, only those
 * objects in this cache will potentially be mutable, and those flushed out to the underlying
 * storage mechanism will no longer be subject to mutation.
 *
 * A particular StringListStoreMap implemenatation will need to supply implementations of the
 * abstract methods in this class that need to interact with the underlying storage mechanism.  The
 * current set of abstract methods can be altered or expanded in the future as needed by future
 * implementations.  This cache provides roughly what a Map<String, RTWListValue>, and abstract
 * methods used to operate on the underlying storage mechanism approximate the same.
 *
 * This cache does not assume that the underlying storage mechanism is capable of storing null as a
 * value.
 *
 * This cache is threadsafe.  Because a Store only needs to be threadsafe in read-only mode, this is
 * pretty much the only thing that needs to worry about threadsafety -- nothing else has state that
 * would change.
 */
public abstract class StringListStoreMapCache {
    /**
     * RTWValue class to represent in the cache a slot that is known to not exist, as this is useful
     * to cache to avoid falling through the cache each time.
     */
    protected static class RTWNullValue extends RTWValueBase implements RTWValue {
        protected RTWNullValue() {
            super();
        }

        @Override public int hashCode() {
            return 4;
        }

        @Override public boolean equals(Object obj) {
            if (obj instanceof RTWNullValue) return true;
            else return false;
        }

        @Override public String toString() {
            return "*SLOT_DOESNT_EXIST*";
        }

        @Override public String asString() {
            return toString();
        }

        @Override public String toPrettyString() {
            return toString();
        }
    }

    /**
     * Singleton instance of RTWNullValue
     */
    private final static RTWNullValue SLOT_DOESNT_EXIST = new RTWNullValue();

    private final static Logger log = LogFactory.getLogger();

    /**
     * Das cache
     *
     * bkisiel 2011-05-06: Work has been done trying to reduce excessive burning of cycles that
     * seems to be a byproduct of rapidly creating objects that actually stick around for a while,
     * such as we'd find in this here cache when the cache is of a significant size.  Because the
     * patterns and nature of DB access have changed, and because we're back to a situation of
     * assuming that the entire KB will be resident in the OS page cache (or close to it, now that
     * NFS parameters have been adjusted to significantly improve the rate at which the KB can be
     * returend to the OS page cache), I'm starting anew on choosing the best size for this cache.
     * Using the BLITZ2 KB as a testbed, a size of 100 is sufficient to get the bulk of the benefit
     * of caching here and pretty well avoids object creation overhead costs.  A size of 1k does
     * measureably better even though it starts to increased overhead.  A size of 10k is little
     * different from 1k, shows rather little increased RAM usage, and, most importantly, still does
     * not break into the territory of high CPU overhead that we see when the cache is sufficiently
     * large to contain a large fraction of the KB.  So, aiming to err on the side of reducing
     * pressure on the DB and the OS page cache, I'm setting the default cache size to 10k.  These
     * observations were made based on using TCHStoreMap, which uses a Tokyo Cabinate hash database
     * as the underlying storage mechanism.
     *
     * bkisiel 2011-10-12: I've switched to storing explicitly in the cache locations that do not
     * exist in the DB.  These are marked by storing a value of our internal RTWNullValue class
     * (beacuse storing null makes it indistinguishable from the value not being in the cache).
     * Deletes are now subject to write-back as well.  The motiviation for this is that I discovered
     * that our higher-level delete logic (and probably other things) do a lot of existence checks,
     * e.g. to verify that there's nothing more in a slot left to delete, and so it becomes valuable
     * to cache emptiness in order to reduce DB gets.
     *
     * bkisiel 2012-02-27: Still haven't retuned (or explored a dedicated slotlist cache) the in the
     * wake of all the different kinds of changes we've had to the StringListStore format since its
     * inception.  But some recent speed investigations revealed a failure to be able to cache
     * conditionedKnownNegatives while doing relation instance iteration on the ongoing run's KB.
     * While it's questionable to want to do that vs. having a dedicated cKN cache, it seems
     * generally prudent for the time being to be able to cover that kind of a case.  Kicking the
     * size up to 100k took care of it.  What I haven't checked is if this incurs too much CPU
     * overhead on smaller KBs; it does not on this one I suppose we could try to set this based on
     * the KB size; for the record, this testing is being done on a KB with 205B records (and with
     * perhaps far too few buckets at 131M!)
     */
    protected FasterLRUCache<String, RTWValue> cache = null;
    protected FasterLRUCache.Item<String, RTWValue> decached = null;

    // 2013-03-07: It became clear during testing of the JSON0 query API that thread starvation was
    // a real issue.  The presumed mode of failure is that most KB operations are KB-intensive
    // (i.e. require many KB reads in rapid succession) and therefore the thread that manages to
    // perform a cache-mutating read tends to re-acquire the lock on the cache on a subsequent read
    // because so little time is spent doing anything other than performing the read -- time spent
    // inside Tokyo Cabinet tends to dominate most operations that are not computationally
    // significant things like gradient descent or recursive/polynomial things that stay within the
    // cache.
    //
    // Common wisdom seems to be that switching from making getValueWithCache a synchronized method
    // to havign it use a ReentrantLock is a toss-up in terms of being faster.  This is a concern
    // because the bulk of our current use cases are batch-style things where throughput is more
    // important than the responsiveness of any one thread, or single-threaded operations where more
    // sophisticated locking mechanisms tend to slow things down.
    //
    // In the context of multiple simultaneous requests to the query server, casual observation
    // shows that using ReentrantLock provides clearly superior operation. Considering that parts of
    // the KB are likely to be swapped out or sitting on an SSD, it becomes especially true that the
    // time spent inside the lock dominates time spent outside of the lock, meaning that the time
    // spent on the locking logic itself will tend to be unimportant.
    //
    // Finally, use of ReentrantLock merits being made standard because the particular needs of and
    // impacts to our batch-mode operations are not yet known.  Effects on total run time for the
    // typical single-threaded things will be discovered befefore log.  Recent forays into
    // multithreading our batch-mode operations (AllPairsToy, CMC3, etc.) demonstrate that we don't
    // yet know the best way to arrange threading, and that contention for KB access can be a
    // killer, so it's safe to make this change in the sense that we'll wind up reinvestigating this
    // locking mechanism if it winds up making a difference.
    private final ReentrantLock getValueWithCacheLock = new ReentrantLock(true);

    protected int kbCacheSize;
    protected boolean writeBack;
    protected boolean readOnly;
    protected boolean forceAlwaysDirty;

    /**
     * Constructor
     *
     * Comments on cacheSize setting can be found above.  As of Fall 2014, we had settled in to a
     * default size of 100,000 when used with Tokyo Cabinet.
     *
     * Setting writeBack true is not generally useful, but might be interesting for special
     * debugging cases.
     *
     * The readOnly setting can't be changed on the fly.  Reconstruct the cache in that case
     *
     * In order to ensure that modifications directly to the values in the map (i.e. without using
     * something like {@link put}) are not lost, this class must flush all cache entries to disk
     * upon being closed.  This may result in many spurious writes.  This behavior may be defeated
     * through the use of {@link setForceAlwaysDirty} when the caller is willing to gauarantee that
     * modifications will never be made except via {@link putValue}.
     */
    public StringListStoreMapCache(int cacheSize, boolean writeBack, boolean readOnly,
            boolean forceAlwaysDirty) {
        decached = new FasterLRUCache.Item<String, RTWValue>();

        kbCacheSize = cacheSize;
        this.writeBack = writeBack;
        this.readOnly = readOnly;
        this.forceAlwaysDirty = forceAlwaysDirty;

        // This will create the cache object if our size is > 0
        resize(kbCacheSize);
    }

    // FODO: 2016-06: It becomes clear from analyzing some slow things that holding a lock
    // throughout this entire method can easily reduce multithreaded KB-intensive operations down to
    // almost effectively a single thread in spite of spending a large amount of time on things like
    // building the key and the value in a TCHStoreMap that don't necessarily require any locking.
    // It doesn't seem to be worth spending time at it at the moment, but it seems like we could get
    // some real performance gains refactoring in such a way as to lock in a less coarse fashion.
    // This could be especially useful in read/write situtions where we necessarily want to be able
    // to go as fast as the underlying storage mechanism can possibly allow.
    protected RTWListValue getValueWithCache(String location) {
        getValueWithCacheLock.lock();
        try {
            RTWValue value = cache.get(location);
            if (value != null) {
                if (value.equals(SLOT_DOESNT_EXIST)) return null;
                else return (RTWListValue)value;
            }
         
            value = get(location);
                    
            RTWValue valueForCache = value;
            if (valueForCache == null) valueForCache = SLOT_DOESNT_EXIST;

            decached.clear();
            cache.put(location, valueForCache, false || (forceAlwaysDirty && !readOnly), decached);
            if (decached.getKey() != null)
                commitDirty(decached.getKey(), decached.getValue(), false);

            return (RTWListValue)value;
        } finally {
            getValueWithCacheLock.unlock();
        }
    }

    /**
     * Write a a cache entry to the KB (doesn't actually have to be dirty...)
     *
     * Note that this does not clean the cache entry
     */
    protected void commitDirty(String location, RTWValue value, boolean mightMutate) {
        try {
            if (readOnly)
                throw new RuntimeException("Internal error: Should not have had a dirty entry to commit on a read-only KB");

            if (value.equals(SLOT_DOESNT_EXIST)) {
                remove(location);
            } else {
                put(location, (RTWListValue)value, mightMutate);
            }
        } catch (Exception e) {
            throw new RuntimeException("commitDirty(\"" + location + "\", " + value + ")", e);
        }
    }

    protected RTWListValue getValueGuts(String location) {
        try {
            if (location == null)
                throw new RuntimeException("location is null");

            RTWListValue v;
            if (cache == null) v = get(location);
            else v = getValueWithCache(location);
            return v;
        } catch (Exception e) {
            throw new RuntimeException("getValueGuts(\"" + location + "\")", e);
        }
    }

    protected synchronized RTWListValue getValueGutsSynchronized(String location) {
        return getValueGuts(location);
    }

    /**
     * Read from KB, using cached value if available and caching the gotten value if not.
     *
     * This will may or may not return immutable RTWValue objects.  The underlying KbUtility
     * methods used to parse the RTWValues out of the KB (currently) do, but calling code may
     * have stored a mutable RTWValue.
     */
    public RTWListValue getValue(String location) {
        try {
            if (readOnly) return getValueGuts(location);
            else return getValueGutsSynchronized(location);
        } catch (Exception e) {
            throw new RuntimeException("getValue(\"" + location + "\")", e);
        }
    }

    /**
     * Write a value to the KB by way of the cache
     */
    public synchronized RTWListValue putValue(String location, RTWListValue value) {
        try {
            if (location == null)
                throw new RuntimeException("location is null");
            if (value == null)
                throw new RuntimeException("value is null");

            RTWValue previous;
            if (cache == null) {
                previous = get(location);
                commitDirty(location, value, true); // no guarantee of immutability
            } else if (!writeBack) {
                decached.clear();
                previous = cache.put(location, value, false || (forceAlwaysDirty && !readOnly), decached);
                if (decached.getKey() != null)
                    commitDirty(decached.getKey(), decached.getValue(), false);
                commitDirty(location, value, true); // no gaurantee of immutability
            } else {
                decached.clear();
                previous = cache.put(location, value, true, decached);
                if (decached.getKey() != null)
                    commitDirty(decached.getKey(), decached.getValue(), false);
            }
            if (previous == null || previous.equals(SLOT_DOESNT_EXIST)) return null;
            else return (RTWListValue)previous;
        } catch (Exception e) {
            throw new RuntimeException("putValue(\"" + location + "\", " + value + ")", e);
        }
    }

    /**
     * Remove a value from the KB in a cache-consistent manner
     */
    public synchronized RTWListValue removeValue(String location) {
        if(readOnly)
            throw new RuntimeException("Can't remove on read-only KB");

        if (cache == null) {
            RTWValue cur = get(location);
            remove(location);
            if (cur == null || cur.equals(SLOT_DOESNT_EXIST)) return null;
            else return (RTWListValue)cur;
        }

        RTWValue cur = cache.get(location);
        if (cur == null) {
            remove(location);
            decached.clear();
            cache.put(location, SLOT_DOESNT_EXIST, false, decached);
            if (decached.getKey() != null)
                commitDirty(decached.getKey(), decached.getValue(), false);
            return null;
        } else if (cur.equals(SLOT_DOESNT_EXIST)) {
            return null;
        } else {
            if (!writeBack) {
                remove(location);
                decached.clear();
                cache.put(location, SLOT_DOESNT_EXIST, false, decached);
                if (decached.getKey() != null)
                    commitDirty(decached.getKey(), decached.getValue(), false);
            } else {
                decached.clear();
                cache.put(location, SLOT_DOESNT_EXIST, true, decached);
                if (decached.getKey() != null)
                    commitDirty(decached.getKey(), decached.getValue(), false);
            }
            return (RTWListValue)cur;
        }
    }

    /**
     * Write all dirty cache entries to the KB and clear the cache.
     */
    public synchronized void clear() {
        commitAllDirty(false);
        if (cache != null) cache.clear();
    }

    /**
     * Write all dirty cache entries to the KB
     *
     * Does not remove entries from cache, but does mark them all clean.
     */
    public void commitAllDirty(boolean mightMutate) {
        if (cache != null) {
            // Make sure to not mark anything in the cache clean if forceAlwaysDirty has been
            // set because otherwise we have to assume everything is dirty at all times due to
            // the possibility of the calling code directly modifying one of the values on its
            // own.
            Iterator<FasterLRUCache.Item<String, RTWValue>> it;
            if (forceAlwaysDirty) it = cache.dirtyIterator();
            else it = cache.cleaningDirtyIterator();

            while (it.hasNext()) {
                FasterLRUCache.Item<String, RTWValue> d = it.next();
                commitDirty(d.getKey(), d.getValue(), mightMutate);
            }
        }
    }

    /**
     * Return cache size
     */
    public int size() {
        return kbCacheSize;
    }

    /**
     * Resize the cache
     *
     * This is likely to entail a cache flush.
     */
    public synchronized void resize(int newSize) {
        commitAllDirty(false);
        kbCacheSize = newSize;
        if (kbCacheSize > 0) {
            cache = new FasterLRUCache<String, RTWValue>(kbCacheSize);
        } else {
            cache = null;
        }
    }

    /**
     * Control the dirtiness assumptions about cached values
     *
     * As mentioned in the class comments, this class by default must assume that all entries in the
     * cache are dirty beacuse the calling code could modify the value objects without signalling
     * anything about it to the cache.  Of course, this can be expected to result in many spurious
     * writes.
     *
     * If the forceAlwaysDirty parameter is turned off, then this class will only consider cached
     * values to be dirty when they are modified through methods of this class such as {@link put}.
     * The onus, then, is on the calling code to ensure that values are never modified directly, as
     * changes in that case might be lost.
     */
    public synchronized void setForceAlwaysDirty(boolean forceAlwaysDirty) {
        if (forceAlwaysDirty != this.forceAlwaysDirty) {
            this.forceAlwaysDirty = forceAlwaysDirty;

            // If this is being turned off, we might wonder if we need to assume that those things
            // already in the cache all have to be marked dirty.  But the cache already forces all
            // entries to be marked dirty whenever forceAlwaysDirty is true, so we need take no
            // further action here.
        }
    }

    /**
     * Log cache statistics
     */
    public void logStats() {
        log.debug("Main KB Cache: " + cache.logStats());
    }

    /**
     * Retrieve the RTWListValue value associated with key key, or null if no value is stored at
     * with that key.
     *
     * The value returned may be immutable if the underlying storage mechanism requires that (or for
     * whatever other reason).  If it is mutable, then the implementation should assume that it
     * might be mutated.
     *
     * This method should be threadsafe.
     */
    protected abstract RTWListValue get(String key);

    /**
     * Save the given value with the given key
     *
     * value will never be null.
     *
     * If mightMutate is set, then the given value, if not immutable, might be mutated.  The
     * implementation should check for this and save an immutable version instead if that is an
     * issue.  This method will be invoked at least once more with the finally-mutated value to save
     * if any further mutations are made, meaning that there is no danger in further mutations not
     * being immediately represented in the underlying storage mechanism.  mightMutate will only be
     * set when the cache is of zero size, in write-through mode, or is writing all dirty entries
     * without flushing them, so the additional overhead of constructing immutable copies of the
     * value, if necessary, is not expected to be substantially important.
     *
     * If mightMutate is not set, then no further mutations to the given value need to be tracked by
     * the underlying storage mechanism.  If the implementation requires immutability, it is
     * recommended that it check for mutability and save an immutable copy as necessary.  This
     * protects against the case that some other piece of code is maintaining a reference to the
     * given value, and decides to mutate it.
     *
     * Write operations (put and remove) will not recieve concurrent invokations.
     */
    protected abstract void put(String key, RTWListValue value, boolean mightMutate);

    /**
     * Remove the value (if any) associated with the given key
     *
     * Write operations (put and remove) will not recieve concurrent invokations.
     */
    protected abstract void remove(String key);
}
    