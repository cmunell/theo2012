package edu.cmu.ml.rtw.theo2012;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This is an interface that may be used to implement back-end database storage mechanisms for Theo
 * that is appropriate to use wherein the back-end storage is modeled as a key-value store where the
 * key type is {@link RTWLocation} and the value type is a set of {@link RTWValue}.
 *
 * FODO: Will be be able to point to some kind of Theo whitepaper in order to explain the structure
 * of what's in a Store?  Also, point to TheoStore as a potential alternative and StringListStoreMap
 * as an even simpler key-value interface to use that might more directly map to the database being
 * used.
 *
 * It is expected that Store implementations will also subclass RTWLocation, and that these
 * subclasses will know how to coordinate with the Store on the matter of fetching values from the
 * Store in a way that maintains cache coherency and thread-safety.  We can't do otherwise because
 * RTWLocation is immutable.  This means, then, that a Store implementation must be
 * responsible for constructing RTWLocation instances used to access values in that Store.  This
 * includes the case where the application code has an already-constructed RTWLocation that is
 * attahed to a different Store (or no Store at all), meaning that, because RTWLocation instances
 * are immutable, Stores must then construct a new RTWLocation attached to itself.  So we'll have to
 * be on the lookout for situations where we can't seem to avoid this kind of unnecessary
 * reconstruction, and maybe we'll find that we can safely make it impossible to construct an
 * RTWLocation without the help of a Store.
 *
 * Many immutable RTWValue subclasses exist, particularly those used for the fundamental types of
 * values stored in a Store.  Our general assumption is that the RTWValues returned by a Store's
 * RTWLocations may also be immutable.  In general, we can expect that this makes life simpler for
 * Store implementations as long as they do, in fact, only ever furnish immutable RTWValues.
 *
 * We are not making a public "get" or "fetch" function at this time; we're going to see if it is
 * sufficient to always perform fetches of elements from slots reads through RTWLocation instances.
 *
 * We haven't addressed thread-safety yet, but it's probably reasonable to think in terms of a need
 * to support concurrent reads (and constructions of RTWLocation instances) when in read-only mode,
 * and not needing to be extravagant in terms of what is supported in read-write mode.
 *
 * So far it seems as though passing an RTWLocation to a Store that specifies a subslot on a
 * nonexistent value may be considered to be worthy of throwing an exception.  We may need to change
 * that as Theo2012 develops, but it's an extra little bit of error detection that we have for now.
 *
 * MOST IMPORTANTLY: Full and correct documentation for this class has been deferred until we're
 * ready to write the abstract Store class and then begin to add real Theo layers.
 *
 * This isn't what we're going to want to settle on as the API for a KB storage engine, but
 * segregating this lowest level of KB/DB operations has become the sensible way to proceed because
 * we need to support subslots on values now instead of later.
 *
 * Database keys are text strings.  Leaving subslots on values out of the picture, they are formed
 * by delimiting the names of the subslots with spaces.  This makes the space character illegal to
 * have in an entity or slot name, e.g. "food populate" or "cake generalizations candidateValues".
 * Database values are RTWValues rendered into a human-friendly UTF-8 format.
 *
 * NOTE: There is no forced-lowercasing here, and, more importantly, no attempts made to enforce the
 * requirement that slot names cannot contain space characters.  For now, this is left to
 * KbManipulation to keep things simple until we're ready to try to factor that around properly.
 *
 * {@link TCHStore} offers getBag methods that return RTWBag instead of RTWLocation (for use by
 * {@link TCHSuperStore}).  If this seems like a good thing to add to Store, accumulate the reasons
 * for that here in this comment to consider during some future revision.
 *
 * To simplify implementation, a Store places no requirements on the use of RTWPointerValues.  To a
 * Store, they are entirely opaque, no different from other RTWValues.  That way, all interpretation
 * and enforcement to do with RTWPointerValues is left to {@link SuperStore} implementations, where
 * it can be addressed comprehensively.
 */
public interface Store {
    /**
     * Construct an RTWLocation attached to this store that names the same location as the one given
     * (which may be attached to some other store)
     */
    public RTWLocation getLoc(RTWLocation l);

    /**
     * Construct an RTWLocation attached to this Store
     *
     * See the corresponding constructor in {@link RTWLocation} for more information.
     */
    public RTWLocation getLoc(Object... list);

    /**
     * Construct an RTWLocation attached to this Store
     *
     * See the corresponding constructor in {@link RTWLocation} for more information.
     */
    public RTWLocation getLoc(List<String> list);

    /**
     * Construct an RTWLocation attached to this Store
     *
     * See the corresponding constructor in {@link RTWLocation} for more information.
     */
    public RTWLocation getLoc(String[] list);

    /**
     * Open a Store
     *
     * We'll probably have to switch to URIs or something for generality.
     *
     * This will throw an exception if the Store is already open or if there is a problem opening
     * the Store.
     */
    public void open(String filename, boolean openInReadOnlyMode);

    /**
     * Return whether or not the Store is currently open.
     */
    public boolean isOpen();

    /**
     * Return whether or not the Store is currently in read-only mode
     *
     * This will throw an exception if the Store is not open.
     */
    public boolean isReadOnly();

    /**
     * Switch the DB into or out of read-only mode
     *
     * This operation may be equivalent to a close-and-reopen.
     *
     * This will throw an exception if the Store is not open.
     */
    public void setReadOnly(boolean makeReadOnly);

    /**
     * Return the file that is currently open, or null if none is
     *
     * Here again we'll probably have to switch to URIs or something.
     */
    public String getFilename();

    /**
     * Take reasonable measures to flush pending writes
     *
     * If sync is true, then this will also block until the database changes are committed to disk
     * or whatever.
     */
    public void flush(boolean sync);

    /**
     * Close the Store.
     *
     * This should always be called when done using the Store.  Store implementations may want to
     * use a finalizer to ensure that this is done.
     *
     * This will throw an exception if the Store is not open.
     */
    public void close();

    /**
     * Saves a copy of the Store to the given filename
     *
     * URIs etc.  Or maybe something quite different down the road?
     *
     * This will throw an exception if the Store is not open.  This operation may entail closing and
     * re-opening the Store.
     */
    public void copy(String filename);

    /**
     * Emit log message about how the Store is doing, including but not limited to some caching
     * statistics
     */
    public void logStats();

    /**
     * Return the list of subslots that exist beneath the given location, or null if there are none.
     *
     * The given location may be a primitive entity, a slot, or a value in a slot (i.e. an
     * RTWLocation that ends in an RTWElementRef).  This should never return an empty list.
     *
     * Here, we are assuming that the magnitude of the number of subslots at any particular location
     * is reasonably small to store in an ArrayList.  We'll see how well that assumption holds.  We
     * may want to switch this to returning List<String> or some iterator over Strings instead;
     * RTWListValue is really only being returned because that's what TCHStore already does.
     *
     * This will throw an exception if the Store is not open.
     */
    public RTWListValue getSubslots(RTWLocation location);

    /**
     * Return the number of elements in the given location.
     *
     * This will return 1 when given a location that ends in an RTWElementRef.  A slot that has no
     * elements is the same as a slot that doesn't exist, and this will return 0 in that case.
     *
     * We don't yet know whether or not it should be an error condition to specify a primitive
     * entity.  Provisionally, Stores need not support that.
     *
     * This will throw an exception if the Store is not open.
     *
     * bkdb: should we follow TheoStore et al and get rid of methods like this that are available
     * through an RTWLocation directly?
     */
    public int getNumValues(RTWLocation location);

    /**
     * Add the given value to the given location, which must specify a slot.
     *
     * A top-level entity may not be used as a slot.  In other words, it is an error condition if
     * the given location contains only one element.  Accordingly, a location whose second element
     * is an RTWElementRef is illegal.
     *
     * Returns true if and only if the given value was not already present in the given location.
     * If this returns falls, then the add was effectivley a no-op.
     *
     * This will throw an exception if the Store is not open, or if it is open in read-only mode.
     */
    public boolean add(RTWLocation location, RTWValue value);

    /**
     * Delete all values at the given location.
     *
     * If the location ends in a slot, then all elements in that slot will be deleted, and this
     * always entails deleting any subslots that exist under those elements.  Subslots under that
     * slot will be deleted if and only if recursive is set.  If the location ends in an
     * RTWElementRef, then only that value is deleted, again along with any subslots it has.
     *
     * If errIfMissing is set, then this will throw an exception if the indicated thing to delete
     * did not actually exist; otherwise, the delete will be a no-op.
     *
     * Returns true if and only if something was deleted.  If this returns false, then the delete
     * was effectively a no-op because the thing being deleted wasn't there to begin with.
     *
     * Deleting a slot with no values can result in something being deleted if recursive is true and
     * there is some subslot to delete.  If this happens, true would be returned because the delete
     * was not a no-op, and no exception would have been thrown if errIfMissing were true.
     *
     * This will throw an exception if the Store is not open, or if it is open in read-only mode.
     */
    public boolean delete(RTWLocation location, boolean errIfMissing, boolean recursive);

    /**
     * Give a hint that you're about to access a large portion of this Store
     *
     * This might do something like encourage the OS to hold the entire Store resident in RAM via
     * fast sequential reads.
     */
    public void giveLargeAccessHint();

    /**
     * Perform routine optimization / defragmentation / compression
     *
     * This is likely to be an expensive operation and should not be done pell nell.
     *
     * As of this writing, this operation is only performed when a very large amount of deletes have
     * taken place or after enough time has passed that the cost of this operation is marginal.
     */
    public void optimize();

    /**
     * Return an iterator over primitive entities
     *
     * The given iterator makes no gaurantees about the order of iteration, and is meant to visit
     * all primitive entities as quickly as possible.  There may be at most one iterator in use at
     * any one time, and any attempt to use mroe than one iterator at a time may provoke undefined
     * behavior (although an exception would be preferable).  Writing to the Store during iteration
     * may invalidate the iterator or lead to undefined behavior.
     *
     * This operation may be of use to the lowest layer of Theo for fsck-style operations, such as
     * checking for primitive entities that exist outside of the generalizations hierarchy.  But it
     * is not clear that it will our should be useful to any higher layer.  As such, while we can
     * wonder whether we should iterate over String, RTWLocation, RTWPointerValue, etc., we need not
     * expect that the choice will have far-reaching consequences, and so it should be safe to stick
     * to something simple like String.  We can of course adjust this as needed.
     *
     * We might also consider oferring a version of this that is safe to use with writes, perhaps
     * with the semantics of possibly iterating over the Store only as it was when the iterator was
     * first obtained.  The idea here would be that a Store implementation that could handle such a
     * thing more efficiently than loading all primitive entities into RAM or spooling them into a
     * file could take advantage of that opportunity.
     */
    public Iterator<String> getPrimitiveEntityIterator();
 }
