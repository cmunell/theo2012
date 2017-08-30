package edu.cmu.ml.rtw.theo2012.core;

import java.util.Map;

/**
 * Interface that extends java.util.Map that adds a few things needed for a {@link Store}
 * implementation so that we can generate a class of Store implementations based on various
 * key/value pair databases.
 *
 * One of the things we add to Map is the notion of a StoreMap being open or closed, with a String
 * location denoting the location of some (possibly pre-existent) copy of the StoreMap being
 * operated upon.  A StoreMap should reject read and write operations if it is not open.  The
 * meaning of the String is not yet well-defined, but is likely to become something along the lines
 * of a URN.  Implementations may act differently depending on the construction of the String.  For
 * instance, certain setting(s) will cause {@link HashMapStoreMap} to not load or save its content
 * to any persistent storage, making it a wholly RAM-based StoreMap whose content exists only for
 * the duration of its lifetime.
 *
 * In the future, we might add facilities for attempting to identify whether a given location
 * corresponds to no pre-existing content, whether ir corresponds to pre-existing content
 * corresponding to a given StoreMap implementation, or whether it corresponds to some pre-existing
 * thing not recognized by a given StoreMap implementation.
 *
 * In addition to the notion of being open or closed, we also introduce the notion of being in
 * read-only vs. read-write mode.  In additin rejecting attempted modification when in read-only
 * mode, implementations must be threadsafe when in read-only mode.  Thread saftey is not required
 * when in read-write mode.
 *
 * And then there are some additional provisional facilities, like flush, copy, and optimize that
 * might or might not continue to exist in the kind of form that they do now.  We need them to
 * address the realities of continuing to run NELL even though we haven't yet decided what kind of
 * abstractions and related operations we ought to have in the face of the wide variety of storage
 * mechanisms that we could potentially be using.
 */
public interface StoreMap<K, V> extends Map<K, V> {

    /**
     * Implementations should override this to provide an "open" operation
     *
     * An exception should be thrown if the open is not successful.
     *
     * The open should not succeed if this StoreMap is already open.  A new, empty StoreMap should
     * be created at the given location if one does not exist there, and if the open is not being
     * performed in read-only mode.  If there exists a StoreMap already at the given location, then
     * its content should be seen through this StoreMap object, and any modifications should be
     * reflected in that location after this StoreMap object is closed or flushed.  Two read-write
     * StoreMap objects connected to the same location may be considered to constitute an error
     * condition.
     *
     * null should be avoided as a legitimate location, as that is likely to be interpreted as an
     * erroneous value by unrelated code, and would also invalidate the semantics of the getLocation
     * method.  Use of an empty string is similarly inadvisable.
     */
    public void open(String location, boolean openInReadOnlyMode);
    
    /**
     * Implementations should override this to provide a "close" operation
     *
     * Closing an already-closed StoreMap is an error condition.
     *
     * This should make the same gaurantees as flush does about what is stored at the location
     * associated with this StoreMap.
     */
    public void close();

    /**
     * Return location associated with this StoreMap, or null if it is not open
     *
     * This doubles as an "isOpen" method
     */
    public String getLocation();

    /**
     * Return whether or not this StoreMap is in read-only mode
     *
     * Invoking this on a closed StoreMap is an error condition
     */
    public boolean isReadOnly();

    /**
     * Take reasonable measures to flush pending writes
     *
     * If sync is true, then this should also block until the database changes are committed to disk
     * or whatever.
     */
    public void flush(boolean sync);

    /**
     * Saves a copy of the Store to the given filename
     *
     * URIs etc.  Or maybe something quite different down the road?
     *
     * This will throw an exception if the Store is not open.  This operation may entail closing and
     * re-opening the Store.
     */
    public void copy(String location);

    /**
     * Give a hint that you're about to access a large portion of this Store
     *
     * This might do something like encourage the OS to hold the entire Store resident in RAM via
     * fast sequential reads.
     */
    public void giveLargeAccessHint();

    /**
     * Emit log message about how the Store is doing, including but not limited to some caching
     * statistics
     *
     * This is an informal and ultimately optional thing that may be used on an ad-hoc basis for
     * development and debugging.
     */
    public void logStats();

    /**
     * Perform routine optimization / defragmentation / compression
     *
     * This is understood to be a a potentially expensive operation and can be expected to be used
     * judiciously.
     *
     * This is an invalid operation when in read-only mode.
     */
    public void optimize();
}