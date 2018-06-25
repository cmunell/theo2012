package edu.cmu.ml.rtw.theo2012.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.cmu.ml.rtw.util.FasterLRUCache;
import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Pair;
import edu.cmu.ml.rtw.util.Properties;
import edu.cmu.ml.rtw.util.Timer;

/**
 * A way of implementing a {@link Store} given a {@link StoreMap} implementation to use as the
 * back-end database.
 *
 * This will not directly modify any values in the store; they will only be modified through {@link
 * StringListStoreMap.put}.  Therefore, StringListStoreMap implementations like {@link TCHStoreMap}
 * should be notified of this so that they can safely make assumptions about cache dirtiness for
 * improved performance.
 *
 * HIGH-LEVEL DESIGN
 * -----------------
 *
 * In particular, this class uses a StoreMap<String, RTWListValue> to implement a Store.  The basic
 * idea is that each RTWLocation in the store maps to a String key that can be used to obtain the
 * RTWListValue that is the set of values stored in that slot.  Additional metadata entries exist so
 * as to be able to provide Store's capabilities efficiently assuming that the StoreMap has
 * properties similar to a hash map.
 *
 * Using a list to store the set of values in each slot is generally a decent tradeoff of simplicity
 * (and its attendent speed) for scalability, but this implementation will have obvious weak spots
 * for certain operations when dealing with slots containing many values.  As of this writing, we
 * don't really have a handle on when that starts to be a relevant consideration for end-to-end
 * system speed, and so doing better is left for future work.  There do exist plans that extend the
 * current implementation so as to distribute the values of a single slot amont a set of entries in
 * order to achieve set-like performance charactaristics when there are a large number of values, if
 * that becomes desirable.
 *
 * This is basically the {@link TCHStore} of Spring 2012 with Tokyo Cabinet factored out of it.
 * Aside from storing the set of values in each slot as a list, the other main contribution of this
 * class is its method for translating an RTWLocation into a String to use as the key value.  The
 * particular choice of translation is one predominantly of evolutionary implementation ease
 * (believe it or not!), not one that's necessarily highly efficient or clever.
 *
 * WARNING: There is something to say about the immutability of values stored, but we must first
 * distinguish between the invidual values stores in each slot and the RTWListValue object associate
 * with each slot used to contain those individual values.  To simplify our lives, this class will
 * only ever store individual values that are immutable (for instance, by converting RTWListValues
 * that it is given to RTWImmutableListValues).  The containing list, however, is never accessed
 * directly by anything other than this class, and so no immutability is enforced there (although it
 * may be the case that the StringListStoreMap being used produces immutable containers from the
 * underlying storage mechansim) so that mutating operations need not waste time copying and
 * converting those containers.
 *
 * Note that there may remain some unnecessary usages of immutable containers; these are vestigial
 * and may be removed as needed for speed reasons.
 *
 * This class is threadsafe only in read-only mode.  This greatly simplifies our implementation.
 *
 *
 * NOTES ON IMPLEMENTATION
 * -----------------------
 *
 * Recent profiling with NELL has suggested that we may be headed toward a situation where we'd be
 * well served by improving the speed of this translation.  One potentially useful idea is to
 * maintain a cache of RTWLocation -> String arragned as a tree such that the depth of each node is
 * equal to the number of elements in the RTWLocation, and the children of each node are the set of
 * values appended to the RTWLocation at that node that are thought to be worth caching.  Further
 * work in this direction is left for the future; for all we know, we'll be using an entirely
 * different storage engine before it becomes worth spending more time improving this one.
 *
 * On a related note, this class does not feature any cache.  It is assumed that concrete
 * implementations will use an internal cache like TCHStore does as needed.  We have never explored
 * the tradeoff of attempting to cache things at the RTWLocation -> RTWValue level.  That could
 * perhaps marginalize the cost of translating from RTWLocation to String.  The "slotlist cache"
 * this class uses does not at this time actually cache anything itself but rather rests on whatever
 * caching is done by the conrete implementation.
 * 
 * Note that one item of backward compatability left out of this class an in TCHStore is tolerance
 * for pre-existing entries in the database that contain an RTWValue type of other than
 * RTWListValue.  That makes things slightly more simple for us at the expense of removing some
 * future flexability (e.g. forgoing the list if there is only one value in the slot).
 *
 *
 * ALGORITHM FOR TRANSLATING RTWLOCATION TO STRING
 * -----------------------------------------------
 *
 * For each slot for which there exists a subslot, there will be an additional DB entry key equal to
 * the key of the slot with "  S" appended (two spaces).  Two spaces after part of a slot address is
 * implicitly indicative of some kind of metainformation because a subslot name cannot contain a
 * space.  The value of this entry will be an RTWListValue containing the names of all subslots.
 *
 * In order to place a subslot on a value, that value must be encoded in the DB key.  We can't use
 * the value verbatim; even if it's a string then we'd have to escape special characters.  And then
 * we'd have to account for collisions.  So instead of potentially-expensive string computations, we
 * have a two-step process to come up with a string element to use in the key to indicate the value
 * under which the subslot exists.
 *
 * First, we compute a hash value from the value under which the subslot exists, and prepend "  #"
 * (two spaces).  This maps to a value in the DB that indicates the actualy string element to use.
 * This two-step process is necessary because we may have a hash value collision whereas we need a
 * unique string to use, and we are free to gaurantee uniqueness by whatever means we like with this
 * extra layer of indirection.  We need a unique string because otherwise things get out of hand
 * when we have subslots on subslots etc.
 *
 * We call the DB entry with the hash in its key the "value subslot name partition" -- it is a
 * partition among colliding values that partitions the names to use for their subslots.  Its value
 * is an RTWListValue of RTWListValues that contain (value under which the subslot exists, subslot
 * name) pairs.  Chosing the likelihood of collision allows us to trade off between the overhead of
 * the collisions and the overhead of more database keys, but the fact that we store a list of the
 * hashes used in a list in the "value subslot directory" described below means that we are assuming
 * collisions at a rate similar to what happens when we store RTWValues that are encoded as sets.
 * We follow a rule of prepending "  =" (two spaces) to the names of the subslots.
 *
 * To place a "source" subslot on the value "food" in the slot "cake generalizations", we might wind
 * up with something like this (note also the record indicating the subslots under "food"):
 *
 * "cake generalizations  #HASH1" -> {"food", "  =HASH1HASH2"}
 * "cake generalizations  =HASH1HASH2 source" -> whatever the source value is
 * "cake generalizations  =HASH1HASH2  S" -> {"source"}
 *
 * By concatenating HASH1 and HASH2 to name the "=" subslot, it becomes sufficient to gaurantee
 * uniqueness of a HASH2 value only with respect to the other HASH2 values associated with that
 * HASH1.
 *
 * Finally, we need to be able to efficiently obtain the set of subslots-on-values that exist for
 * any given slot.  Probing for each one would be inefficient and we cannot leverage similarity
 * among database keys because the underlying database is a hash table.  So we have one more DB
 * entry that we call the "value subslot directory" that contains an RTWListValue of all hash values
 * used.  The key is formed by appending "  D" (two spaces) to the key of the slot.
 *
 * "cake generalizations  D" -> {"  #HASH1"}
 *
 * Note that in all of the above that the prepended spaces are stored on the hash values and subslot
 * names.  This is meant to foster faster / easier construction of keys.
 *
 * A subslot on a value may itself need a subslot list.  The key for this is formed in the usual
 * fashion, e.g. "cake generalizations  =HASH1HASH2 source  S" (two spaces in both places).
 */
public class StringListStore<SLSM extends StringListStoreMap> implements Store {
    private final static Logger log = LogFactory.getLogger();

    /**
     * RTWLocation subclass used by StringListStore
     *
     * This is based on {@link SimpleLocation}, which in turn is based on {@link SimpleBag}.  All we
     * have to do is provide the two methods that SimpleBag needs, getValues and getNumValues.  This
     * is pretty trivially easy considering that StringListStore gives us an RTWListValue of the
     * values for any given location.
     *
     * That and we need to override newRTWLocation for {@link RTWLocationBase} and provide
     * RTWLocation constructors.
     *
     * The one sophisticated thing we do is fetch that RTWListValue lazily by leaving our "val"
     * member null until such time as we may come to need it.
     */
    protected class SLSRTWLocation extends SimpleLocation implements RTWLocation {
        /**
         * The value of the location that we represent
         *
         * This will be null up to the point where we try to retrieve it.
         *
         * TODO: we need to coordinate with our cache such that, when in read-write mode, no value
         * in the cache is kicked out of the cache if one of these SLSRTWLocations is still pointing
         * that value.  Otherwise, somebody could write a new value to the location that we're
         * representing here without this RTWValue getting modified as a result.  I don't have any
         * watertight reason why we seem to be getting away with not doing this so far, but I'm
         * hoping that we can put off fixing that until such time as StringListStore is able to store
         * proper sets of values because we'll have to rewrite SLSRTWLocation at that point anyway.
         */
        protected RTWListValue val = null;

        /**
         * Override this so that operations like .subslot return SLSRTWLocation instances that are
         * attached to the this store
         */
        @Override protected SLSRTWLocation newRTWLocation(Object[] list) {
            return new SLSRTWLocation(list);
        }

        /**
         * One half of our SimpleBag implementation
         */
        @Override protected RTWListValue getValues() {
            if (val != null) return val;
            if (endsInSlot()) {
                RTWListValue tmp = StringListStore.this.get(this);
                if (tmp == null) tmp = RTWArrayListValue.EMPTY_LIST;
                if (isReadOnly()) val = tmp;
                return tmp;
            } else {
                throw new RuntimeException("Ought it to be an error condition to wind up invoking getValues on a location not ending in a slot?");
                // If not, please document why not here.  One would also want to know whether we
                // need the "traditional" behavior of returning the value that the location ends in
                // (which would be a drag because we'd have to wrap it in an RTWListValue,
                // suggesting that the caller might should find a better way to do whatever seems to
                // be necessitating this case), or whether we could more easily get away with
                // returning RTWArrayListValue.EMPTY_LIST.
            }
        }

        /**
         * Other half of our SimpleBag implementation
         */
        @Override public int getNumValues() {
            return StringListStore.this.getNumValues(this);
        }

        protected SLSRTWLocation(Object... list) {
            super(list);
        }

        protected SLSRTWLocation(List<String> list) {
            super(list);
        }

        protected SLSRTWLocation(String[] list) {
            super(list);
        }

        /**
         * TODO: can we get rid of this, or does this need to move into some kind of base class?
         */
        @Override public boolean isEmpty() {
            if (endsInSlot()) {
                return getNumValues() == 0;
            } else {
                // bkisiel 2013-02-13: StringListSuperStore.validateRTWPointerValue would like to
                // use this, but I'm having it bypass its use in order to see if there's anything
                // else that really wants this.  If validateRTWPointer is the only place using this,
                // and we also don't need the ends-in-value case for getValues above, then it would
                // probably be nicer and cleaner to get rid of both and make sure the RTWLocation
                // documentation defines bag operations as illegal for locations not ending in a
                // slot.

                if (true)
                    throw new RuntimeException("Can we get rid of this, or does this need to move into some kind of base class?");
                // FODO: It's a little bit of a bummer to have to invoke parent(), but really this
                // belongs in the StringListStore-specific RTWLocation, and, once it's there, it could
                // try to get more clever more easily.
                return !parent().containsValue(lastAsValue());
            }
        }

        /**
         * Override contains because we can do it (much) faster than SimpleBag if we happen to have
         * a set on our hands
         */
        @Override public boolean containsValue(RTWValue v) {
            try {
                RTWListValue list = getValues();
                if (list.isEmpty()) return false;
                return list.contains(v);
            } catch (Exception e) {
                throw new RuntimeException("contains(" + v + ") for location " + this, e);
            }
        }
    }

    /**
     * Iterator over primitive entities
     *
     * Store only supports one iterator at a time, so having two of these in action simultaneously
     * should be guarded against.  To achieve this, we give StringListStore an activeIterator
     * member, and have instances of this class throw an exception if activeIterator isn't set to
     * them.  That way, constructing a new KeyIterator invalidates all older KeyIterator instances.
     */
    protected class PrimitiveEntityIterator implements Iterator<String> {
        protected String next;
        protected Iterator<String> keyIterator;

        protected void setNext() {
            if (keyIterator == null) {
                next = null;
                return;
            }

            // Look for a subslot list attached to a key with no spaces.  These indicate the
            // existence of primitive entities.
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();

                // Mind that we have to put these through subslotUntranslationTable first, both to
                // get real enttiy names back, and to not get tripped up by the initial space that
                // most such entries have.
                if (subslotUntranslationTable != null && key.length() > 1) {
                    // Look for end of first part of key.  Start at position 1 because we expect
                    // translated slots to begin with a space, and we can't have a zero-length first
                    // part of the key anyway.
                    int pos = key.indexOf(' ', 1); 
                    if (pos > 0) {
                        String firstPart = key.substring(0, pos);
                        String newFirstPart = subslotUntranslationTable.get(firstPart);
                        if (newFirstPart != null) {
                            String newKey = newFirstPart + key.substring(pos);
                            key = newKey;
                        }
                    }
                }

                // Similarly, untranslate keys that start with ' C'.
                if (key.length() > 2 && key.charAt(0) == ' ' && key.charAt(1) == 'C') {
                    String newKey = "concept:" + key.substring(2);
                    key = newKey;
                }

                int pos = key.indexOf(' ');
                if (pos < 0) continue;
                if (key.length() != pos+3) continue;
                if (key.charAt(pos+1) != ' ') continue;
                if (key.charAt(pos+2) != 'S') continue;
                next = key.substring(0, pos);
                return;
            }
            next = null;
        }

        public PrimitiveEntityIterator() {
            keyIterator = slsm.keySet().iterator();
            setNext();
            StringListStore.this.activeIterator = this;
        }

        @Override public boolean hasNext() {
            if (StringListStore.this.activeIterator != this)
                throw new RuntimeException("This iterator has been invalidated because a newer one has been constructed.");
            return (next != null);
        }

        @Override public String next() throws NoSuchElementException {
            if (StringListStore.this.activeIterator != this)
                throw new RuntimeException("This iterator has been invalidated because a newer one has been constructed.");
            if (next == null) throw new NoSuchElementException();
            String tmp = next;
            setNext();
            return tmp;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Class that manages the caching of each entity's list of slot addresses
     *
     * Actually, this does not actually itself cache anything at this time.  To simplify
     * implementation, this rests on any caching supplied by the concrete implementation.  Earlier
     * work suggested that we might benefit from caching slotlist entries separately, either with a
     * dedicated level of caching or through segregation of kinds of things cached in the underlying
     * storage mechanism.  Revisiting this is left for future work.
     *
     *
     * HISTORICAL PERSPECTIVE AND JUSTIFICATION
     * ----------------------------------------
     *
     * When using Tokyo cabinet, there is a serious problem when we want to know what slots exist
     * under a given slot address, or any other time that calling fwmkeys.  So we store a hidden "
     * slotlist" slot in each entity whose content is an RTWListValue listing every slot address
     * within that entity.  This class is in charge of managing those lists and providing access to
     * them.
     *
     * Background on why this is slow: The database is a key-value store.  Each key in the database
     * is a slot address, so getSubSlots requires finding the set of keys with a given prefix.  But,
     * we are using a hash table for the database, meaning the keys are unordered, and so the key
     * search is inefficient.
     *
     * Tokyo Cabinet offers a B+-tree mode as an alternative to the hash table, but it appears to be
     * a few times slower, and I think we can do better than that.  Besides, I don't see any reason
     * to believe that other DB choices of the future (e.g. tripplestores) will be inherently fast
     * for this operation.
     *
     * So we have an additional record for each entity with key "<entity>  S" -- note the two
     * spaces, making this an impossible key to ever exist on its own.  For initial simplicity, the
     * value will be a plain old RTWListValue, and it will be a list of every slot address within
     * the entity.  Nominally, every DB-level put or delete will have to check this value and add to
     * it as necessary.
     *
     * Slotlist entries used to be optional; if one was expected but missing then it would simply be
     * regenerated silently.  This also made missing slotlist entries in a read-only KB into a
     * nonissue.  Nowadays, after a format adjustment, they are mandatory.  This requirement is
     * addressed by automatically generating new-style slot lists if they are not found,
     * converting/removing any old ones in the process.
     *
     * Perhaps in the future we may investigate changing from a Set of slot addresses to a
     * SortedSet, in order to further aid methods like getSubSlots or something like that.
     *
     * This class is threadsafe in read-only mode because all of its state lives in the cache of the
     * underlying storage mechanism (if any), which is threadsafe in read-only mode by definition of
     * Store.
     */
    protected class SlotlistCache {
        public void reconstructSlotslists(boolean expungeOldStyleSlotlists) {
            log.info("Reconstructing / updating slotlists");

            // It's a bit difficult to do all this on the fly because we can't go through database
            // keys in a non-arbitrary order, we don't want to be updating and deleting during
            // iteation, and we can't afford to build up all of the slotlists in memory.  So we
            // first read them all in.  Sucks to be somebody without a big enough heapsize!

            // Set of slots worth remembering
            Set<String> keySet = new TreeSet<String>();

            // List of slots to delete before we get going
            List<String> deleteList = new ArrayList<String>();

            log.info("Reading in list of all DB keys");
            Iterator<String> keyIter = slsm.keySet().iterator();
            int keyCnt = 0; 
            while (keyIter.hasNext()) {
                String key = keyIter.next();
                if (keyCnt % 1000000 == 0) { 
                    long totalMemory = Runtime.getRuntime().totalMemory() / 1048756; 
                    long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756; 
                    log.info("Read " + keyCnt + " keys (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)"); 
                } 
                keyCnt++; 

                if (key.contains("  S")) deleteList.add(key);
                else if (expungeOldStyleSlotlists && key.endsWith(" slotlist")) deleteList.add(key);
                else keySet.add(key); 
            } 
            log.info("Done reading list of all DB keys"); 

            // Now delete any existing garbage
            log.info("Deleting " + deleteList.size() + " old entries");
            for (int i = 0 ; i < deleteList.size(); i++) {
                if (i % 1000000 == 0) {
                    long totalMemory = Runtime.getRuntime().totalMemory() / 1048756; 
                    long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756; 
                    log.info("Deleted " + i + " keys (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)"); 
                }
                slsm.remove(deleteList.get(i));
            }
            deleteList.clear();
            log.info("Done deleting old entries");

            // As we go through keySet, we'll hit all of the slots for each entity grouped together.
            // That means that we can build up the set of slotlists for that entity in a hash map
            // and then write them all out.  That way, we don't have to be clever about exactly what
            // order we see the slots.
            log.info("Writing new slotlists");
            String lastEntity = "";
            Map<String, RTWListValue> listMap = new HashMap<String, RTWListValue>();
            keyCnt = 0;
            for (String key : keySet) {
                if (keyCnt % 1000000 == 0) { 
                    long totalMemory = Runtime.getRuntime().totalMemory() / 1048756; 
                    long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756; 
                    log.info("Processed " + keyCnt + " keys (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)"); 
                } 
                keyCnt++; 

                int firstSpace = key.indexOf(" ");
                if (firstSpace < 0) {
                    log.error("Deleting entry with illegal key \"" + key + "\"");
                    slsm.remove(key);
                    continue;
                }
                String entity = key.substring(0, firstSpace);
                if (!entity.equals(lastEntity)) {
                    for (String addr : listMap.keySet()) {
                        slsm.put(addr + "  S", listMap.get(addr));
                    }
                    listMap.clear();
                    lastEntity = entity;
                }

                // A valueless and nonpresent slot may contain a subslot, so we have to work our way
                // through the address and process each subsequent sublot.
                int prevSpace = firstSpace;
                String addr = entity;
                while (true) {
                    int nextSpace = key.indexOf(" ", prevSpace + 1);
                    if (nextSpace < 0) nextSpace = key.length();
                    if (nextSpace < key.length()-1 && key.charAt(nextSpace+1) == ' ')
                        throw new RuntimeException("This KB already has fancy new-style slotlists and things in it.  This is not supported.  Don't rebuild its slotlists");
                    String subslot = key.substring(prevSpace + 1, nextSpace);
                    RTWStringValue s = new RTWStringValue(subslot);
                    RTWListValue l = listMap.get(addr);
                    if (l == null) {
                        listMap.put(addr, new RTWArrayListValue(s));
                    } else {
                        if (!l.contains(s)) l.add(s);
                    }
                    if (nextSpace == key.length()) break;
                    addr = addr + " " + subslot;
                    prevSpace = nextSpace;
                }
            }
            for (String addr : listMap.keySet()) {
                slsm.put(addr + "  S", listMap.get(addr));
            }
            log.info("Done writing new slotlists");

            log.info("Done reconstructing / updating slotlists");
        }

        public void updateSlotlistsIfNecessary() {
            RTWListValue signal = slsm.get(" ");
            if (signal == null) {
                if (slsm.size() > 0) {
                    if (isReadOnly())
                        throw new RuntimeException("Database is too old and needs to be updated before use, but this cannot be done because you have opened the database read-only");
                    log.info("Database does not contain '  S' style slotlists.  Please wait while the database is updated (you may need to crank up your heap size for this)...");
                    reconstructSlotslists(true);
                }

                // Mark presence of these newer-style slotlists.  I'm planning to do more with this " "
                // key in the future in order to indicate versioning, features, etc.  But for now, we
                // only care about whether or not it exists, and we write a "0" to it to facilitate a
                // rudimentry version counter as a fallback plan.
                if (!isReadOnly()) slsm.put(" ", new RTWImmutableListValue(new RTWIntegerValue(0)));
            } else {
                if (!signal.get(0).equals(0))
                    log.debug("Signal for \" \" key is \"" + signal + "\"");
            }
        }

        // No longer auto-adds parent slots
        public RTWListValue getSubslots(String addr) {
            final String slotlistAddr = addr + "  S";
            return slsm.get(slotlistAddr);
        }

        public void addSlot(String addr, RTWStringValue subslot) {
            final String slotlistAddr = addr + "  S";
            // FODO: maybe we can use an internal version of getValue that allows direct modification
            final RTWListValue v = slsm.get(slotlistAddr);
            if (v != null && v.contains(subslot)) return;
            slsm.put(slotlistAddr, RTWImmutableListValue.append(v, subslot));
        }

        public void removeSlot(String addr, RTWStringValue subslot, boolean knownToExist) {
            try {
                final String slotlistAddr = addr + "  S";
                // FODO: maybe we can use an internal version of getValue that allows direct modification
                final RTWListValue v = slsm.get(slotlistAddr);
                if (v == null) {
                    if (knownToExist)
                        throw new RuntimeException(addr + " does not exist whereas we know subslot "
                                + subslot + " to exist");
                } else {
                    int pos = v.indexOf(subslot);
                    if (pos < 0) {
                        if (knownToExist)
                            throw new RuntimeException("Subslot list for " + slotlistAddr
                                    + " does not contain " + subslot + " whereas we know it to exist");
                    } else {
                        if (v.size() == 1) {
                            slsm.remove(slotlistAddr);
                        } else {
                            ArrayList<RTWValue> newList = new ArrayList<RTWValue>(v);
                            newList.remove(pos);
                            slsm.put(slotlistAddr, new RTWImmutableListValue(newList));
                        }
                    }
                }
            } catch (Exception e) { 
                throw new RuntimeException("removeSlot(\"" + addr + "\"," + subslot + ", "
                        + knownToExist + ")", e);
            }
        }            

        /*
         * bkisiel 2012-03-28: Removing this, which is only used in the endsInSlot case of
         * cullEmptyEntries, because it doesn't operate properly with slot names that are prefixed
         * with a space such as are used by TCHSuperStore.  Fixing this might be the faster/better
         * solution than what's there now (particularly the bit where we wind up calling
         * constructSlotAddr twice for each location, but we call that thing altogether too much
         * anyway, meaning that we need to be more clever in general), but we'll see how things go
         * as-is.
         *
        public void removeSlot(String addr, boolean knownToExist) {
            try {
                int pos = addr.lastIndexOf(" ");
                if (pos < 0)
                    throw new RuntimeException("Database key \"" + addr + "\" has no subslot");
                removeSlot(addr.substring(0, pos), new RTWStringValue(addr.substring(pos+1)), knownToExist);
            } catch (Exception e) {
                throw new RuntimeException("removeSlot(\"" + addr + "\", " + knownToExist + ")",
                        e);
            }
        }
        */
    }

    /**
     * Back-end database
     */
    protected SLSM slsm;

    /**
     * Cache used for access to list of each entity's set of slot addresses (see SlotlistCache class
     * documentation)
     */
    protected SlotlistCache slotlistCache;

    /**
     * Used by PrimitiveEntityIterator instances to ensure that only the most-recently-constructed
     * instance is usable.
     */
    protected PrimitiveEntityIterator activeIterator = null;

    /**
     * Point at which we switch from storing lists of values in slots to sets of values in slots
     */
    protected final int kbMaxListSize;

    /**
     * Flag to control our historical disallowment of entity names containing uppercase letters
     *
     * Disallowment is useful safeguard for current NELL development, since the NELL KB requires it
     * for internal consistency for backward compatability.
     */
    protected final boolean preventUppercase;

    /**
     * Translation table for generating database keys based on subslot names
     *
     * This is ued by {@link constructSlotAddr}.  The idea here is that there is likely to be a set
     * of very common subslot names (e.g. "generalizations") and so one easy thing we can do to cut
     * down on the length of database keys is to replace those with abbreviations.  For NELL KBs
     * prior to the introduction of this table, it's not unusual for the keys to consume 1/2 or more
     * of the total database size.  (Perhaps this should tell us that we should use a better system,
     * but we don't have time right now for a shiny new storage engine, and this is a quick and
     * wortwhile incremental improvement.)
     *
     * It is essential that this table not change for the life of the database because we don't have
     * any good, fast way to deal with the possibility of a database key being in use that did not
     * go through this translation table.  So our logic is to leave this table null when opening a
     * pre-existing database, and to initialize it only when creating a new database.
     * Correspondingly, we need to write the translation table to the database in a special hidden
     * key when we do use it, and load it from the same when opening a pre-existing database.
     *
     * One could imagine a way to automatically create content for this table based on heuristics
     * gathered from an existing database (maybe this could be done as part of the optimization
     * process).  This is left for future work.  We'd probably want to coordinate that process from
     * way out in MBLExecutionManager or something because it takes a while and certainly wouldn't
     * be worth doing on every single iteration.  For reference, converting the ongoing KB at
     * iteration 698 with 8.5M primitive entities took 4 hours, although it slowed way down toward
     * the end and one wonders if it would have gone faster were there the larger number of buckets
     * that there would normally be for a KB that size.
     *
     * It is also essential that the abbreviations used exist in a separate namespace distinct from
     * legitimate subslot names so that there are never collisions.
     */
    protected HashMap<String, String> subslotTranslationTable = null;

    /**
     * Reverse mapping for {@link subslotTranslationTable}
     */
    protected HashMap<String, String> subslotUntranslationTable = null;

    /**
     * Return the hash value to use in constructing DB keys for value subslot name partition slots
     *
     * This will include the prepended two spaces and "#" character.
     */
    protected String getNamePartitionHash(RTWValue value) {
        // Common case first
        if (value instanceof RTWStringValue) {
            return getNamePartitionHash(value.asString());
        }
        
        // Pointers are like lists: troublesome from a speed perspective.  At least for NELL in
        // 2012, we expect to be using mainly RTWPointerValues of size 1, so we'll use the same
        // approach that we used for lists, and leave a more sophisticated solution for a future
        // when we might look at what's actually done in practise that we might be able to optimize
        // for.
        //
        // bkdb: 2012-11-01: converting this from a handler of RTWPointerValue to a handler of
        // Entity to circumvent problems surrounding the composition vs. subclassing issue that
        // we're trying to keep at bay for now.
        else if (value instanceof Entity) {
            final RTWLocation loc = ((Entity)value).getRTWLocation();
            if (loc.size() == 0) return "  #F00";
            final String s;
            if (loc.isSlot(0)) s = loc.getAsSlot(0);
            else s = ((RTWStringValue)loc.getAsElement(0).getVal()).asString();
            return getNamePartitionHash(s);
        }

        // For real numbers, we can't use the string rendition because there is in general more than
        // one valid way to render a real number.  We'll truncate to an integer and use that
        // integer's string rendition instead, but multiply by 10 beforehand so that the expectably
        // common case of probability values between 0 and 1 will not all be hashed into the same
        // value.
        else if (value instanceof RTWDoubleValue) {
            int i = (int)(value.asDouble() * 10);
            return getNamePartitionHash(Integer.toString(i));
        }

        // Lists suck.  If this case actually gets used, we can come up with a better / faster
        // hashing function based upon that actual use case.
        else if (value instanceof RTWListValue) {
            final RTWListValue list = (RTWListValue)value;
            if (list.size() == 0) return "  #F00";
            return getNamePartitionHash(list.get(0)) + Integer.toString(list.size());
        }

        // Booleans are easy
        else if (value instanceof RTWBooleanValue) {
            if (((RTWBooleanValue)value).asBoolean())
                return "  #F02";
            else
                return "  #F01";
        }

        // As are ThisHasNoValue markers
        else if (value instanceof RTWThisHasNoValue) {
            return "  #F03";
        }

        // bkisiel 2012-05-29: The older implementation of RTWIntegerValue.asString that used
        // String.format instead of Integer.toString wound up causing the hash computations to take
        // a lot of time.  The take-home from this is that integers can come up frequently during
        // recursive KB iteration.  However, while it might be marginally valuable to make a faster
        // not-string-based hash function here for integers, I think the better solution is to cache
        // partial output of constructSlotAddr or something like that such that we avoid making so
        // many hash computations at all.  Recursive KB iteration still winds up spending a lot of
        // time hashing RTWStringValue.
        //
        // Here's an example alternative hashing function for RTWIntegerValue:
        // 
        // else if (value instanceof RTWIntegerValue) {
        //     int i = value.asInteger();
        //     char c1 = (char)((i % (126-33)) + 33);
        //     i = i / (126-33);
        //     char c2 = (char)((i % (126-33)) + 33);
        //     return "  #F" + c1 + c2;
        // }

        // Everything else can get a hash based on its string rendition
        else {
            return getNamePartitionHash(value.asString());
        }
    }

    /**
     * Version of getNamePartitionHash that takes a String directly so that we don't have to go
     * through RTWStringValue unnecessarily.
     *
     * It might be that we could return byte[] here instead to speed construction of database keys,
     * but I doubt that it would be worth the extra implementation effort and inconvenience.
     */
    protected String getNamePartitionHash(String value) {
        // The leading "F" character is a version indicator.  I kind of want to use a number as a
        // version indicator and let the number indicate use of the first N characters, but this
        // strikes me as dangrous for odd cases like a set of category instances with the same
        // prefix.  All this crap about UTF-16 and getting into and out of String makes me
        // uncomfortable, but I don't know if it might be worse to ask for its hash code and then
        // have to turn that into a String anyway.
        //
        // Note that we'll have to accomodate slots with 100s of thousands or perhaps millions of
        // values, so this hash function is in the delicate situation where it will want to have
        // enough buckets so that each bucket has "sufficiently few" values for ver large slots, but
        // we still would prefer some modest rate of collisions on slots that have a more moderate
        // number of values.  Maybe the ultimate answer here will be to use different hashing
        // functions for differently sized slots and suffering the occasional rehash.  I'm thinking
        // to get this working now and to come back to fancy hashing later on.
        //
        // If we assume uniform distributions, this produces 8.7k buckets.  The other hash function
        // is good for 93 collisions (not that we want 93 collisions) among a number of buckets that
        // might not always be so big because that hash function is prone to getting hooked on
        // orthographical-style regularities.  But still we're talking at least a few 100 thousand
        // values in a slot before our hashing system craps out.  So we'll need to upgrade at some
        // point, but hopefully we'll pick up some more insight into a more desirable system along
        // the way.

        String hash = "  #F";
        int len = value.length();
        if (len > 0) {
            char c1 = 0;
            int i = len - 10;
            if (i < 0) i = 0;
            for (; i < len; i++) c1 = (char)(c1 + value.charAt(i));
            c1 = (char)((c1 % (126-33)) + 33);

            char c2 = (char)((len % (126-33)) + 33);

            hash = hash + c1 + c2;
        }
        return hash;
    }

    /**
     * Come up with the name of a value that has a subslot on it to use in constructing its DB keys
     * when such a name does not yet exist
     *
     * As with getNamePartitionHash, this includes the prepended two spaces and "=" character.
     * 
     * This will gaurantee noncollision based on what is already in the DB.  Noncollision with
     * subslots named in sibling name partition slots is gauranteed by prepending the hash value
     * passed in as a parameter.  It is assumed that the first 3 characters of the hash are two
     * spaces and the "#" character and therefore needn't be used here.
     *
     * namePairList should be the current value of the name partition slot (so that collisions can
     * be dealt with).  null is a perfectly acceptable current value.
     */
    protected String getNewName(String keySoFar, RTWValue value, String hash, RTWListValue namePairList) {
        // Common case first
        if (value instanceof RTWStringValue) {
            return getNewName(keySoFar, value.asString(), hash, namePairList);
        }

        // Like lists, paralleling what we do in getNamePartitionHash
        //
        // bkdb: 2012-11-01: converting this from a handler of RTWPointerValue to a handler of
        // Entity to circumvent problems surrounding the composition vs. subclassing issue that
        // we're trying to keep at bay for now.
        else if (value instanceof Entity) {
            final RTWLocation loc = ((Entity)value).getRTWLocation();
            if (loc.size() == 0) return getNewName(keySoFar, "<>", hash, namePairList);
            final String s;
            if (loc.isSlot(0)) s = loc.getAsSlot(0);
            else s = ((RTWStringValue)loc.getAsElement(0).getVal()).asString();
            return getNewName(keySoFar, Integer.toString(loc.size()) + s, hash, namePairList);
        }

        // Lists are potentially very long, so we'll use the same sort of lame approach as in
        // getNamePartitionHash.  We can do better as needed.
        else if (value instanceof RTWListValue) {
            final RTWListValue list = (RTWListValue)value;
            if (list.size() == 0) return getNewName(keySoFar, "{}", hash, namePairList);
            return getNewName(keySoFar, Integer.toString(list.size()) + list.get(0), hash, namePairList);
        }

        else if (value instanceof RTWBooleanValue) {
            if (((RTWBooleanValue)value).asBoolean())
                return keySoFar + "  =H2";
            else
                return keySoFar + "  =H1";
        }

        else if (value instanceof RTWThisHasNoValue) {
            return keySoFar + "  =H3";
        }

        // The rest can go through using their string renditions
        else {
            return getNewName(keySoFar, value.asString(), hash, namePairList);
        }
    }

    /**
     * Version of getNewName that takes a String directly so that we don't have to go through
     * RTWStringValue unnecessarily.
     */
    protected String getNewName(String keySoFar, String value, String hash1, RTWListValue namePairList) {
        try {
            // We need a hashing strategy different from getNamePartitionHash.
            //
            // 2012-01-09: The original implementation of this was buggy in that one of its two
            // characters is based on string length when one of getNamePartitionHash's two
            // characters will always be the same.  This gets us into many more collisions than we
            // really want.  After noticing that this function is only invoked when a new subslot is
            // created, I'm thinking that it's pretty safe to choose something a bit more expensive
            // like adding up the values of every other character and taking that modulo our
            // alphabet size, and then doing the same for the ones we skipped.  I'm choosing this to
            // be especially robust against strings like "<tinker toy name> <arg1> <arg2>" where
            // there will be a lot of regularity at the beginnings and ends.  More generally, this
            // should do alright in the face of common prefixes like "concept:" and common words
            // like tinker toy, slot, or predicate names.
            //
            // I'm pretty sure that it will be alright to simply change this hash function and then
            // go ahead and use it on older KBs because this hash function is only used when
            // creating a new entry.
            //
            // Finally we optionally add another character to avoid a collision.  It seems to me
            // that we ought to put the burden of maintaining a "desirable" level of collisions into
            // the getNamePartitionHash hash function.  In other words, if there are many values
            // here and we face a lot of collisions from this hash function, then the right solution
            // more likely lies in reducing the number of collisions from getNamePartitionHash.
            // That's why we use only one additional character to avoid a collision.
            //
            // If we called the previous version of the the "G" hash function, I suppose we should
            // call this "H".  That will have the added benefit of avoiding any collisions at all
            // with pre-existing intances of the "G" function.

            String hash = "  =H" + hash1.substring(3);
            int len = value.length();
            char c1 = 'a';
            char c2 = 'b';
            for (int i = 0; i < len; i++) {
                if (i % 2 == 0) c1 = (char)(c1 + value.charAt(i));
                else c2 = (char)(c2 + value.charAt(i));
            }
            c1 = (char)((c1 % (126-33)) + 33);
            c2 = (char)((c2 % (126-33)) + 33);
            hash = hash + c1 + c2;

            // Now we cannot tolerate collisions here, so go on to append additional characters as
            // needed.  A single character buys us 93 collisions.  It probably wouldn't be bad to
            // upgrade to a 2nd character, which would buy us 8.7k collisions in total, but I'm a bit
            // curious to see when and how we wind up using all 93 collisions, and whether or not that's
            // more collisions than we should tolerate, so I'm not going to implement that at this time.
            if (namePairList == null) return hash;
            String goodhash = hash;
            char suffix = 32;

            // I don't know if this is the fastest way to check if goodhash is already present in
            // namePairList, but the alternatives would seem to incur construction of a new container
            // class.  If the number of elements in namePairList is large enough to make it worth the
            // cost of constructing a hash set then we should probably adjust getNameParitionHash to
            // produce fewer collisions.
            while (true) {
                boolean collision = false;
                for (RTWValue namePair : namePairList) {
                    if (((RTWListValue)namePair).get(1).equals(goodhash)) {
                        collision = true;
                        break;
                    }
                }
                if (!collision) break;
                suffix++;
                if (suffix > 126)
                    throw new RuntimeException("Aborting due to silly number of collisions.  Either there's a bug or we need a better naming scheme. (full key is \"" + keySoFar + goodhash + "\")");
                goodhash = hash + suffix;
            }
            // if (suffix > 32)
            //     log.debug("Value subslot name collision: " + keySoFar + goodhash);
            return goodhash;
        } catch (Exception e) {
            throw new RuntimeException("getNewName(\"" + keySoFar + "\", \"" + value + "\", \""
                    + hash1 + "\", " + namePairList + ")", e);
        }
    }

    /**
     * This is called by StringListStore after it has deleted a slot so that interested subclasses can
     * override this method so as to trap this occurrence for their own purposes.
     *
     * It is not sufficient to override delete in order to detect all cases of a slot being deleted.
     *
     * The slot given will have already been deleted upon invokation.
     *
     * Note that the slot given will never be a top-level entity because those cannot be used as
     * slots.  At present, StringListStore offers no way to exclusively detect the deletion of a top-level
     * entity.  But this may be done by overriding this method and checking to see whether or not
     * the parent of the given slot is a top-level entity, and whether or not it has any remaining
     * subslots (since lack of subslots on a top-level entity is equivalent to nonexistence of that
     * top-level entity).
     */
    protected void signalDeleteSlot(RTWLocation slot) {
        // No-op, as far as StringListStore is concerned.
    }

    /**
     * Primitive delete of a single element from a slot
     *
     * It is assumed that the value being deleted does actually exist.  If it does not, then the
     * resulting behavior is undefined; for efficiency purposes, this will not verify existence.
     *
     * Note that, because top-level slots cannot be used as slots, that the given location will have
     * at at least two elements.
     *
     * This is factored out of delete so that subclasses can easily overide this in order to trap
     * primitive beliefs deletes and add extra behavior (e.g. updating an index or bookkeeping).
     * Such subclasses might also be interested in trapping signalDeleteSlot.
     *
     * All deletes, including recursive deletess and deletes of entire slots use only this method to
     * do the actual value deletes.  Therefore, we're sacrificing some opportunities to speed things
     * up through batch deletes, avoiding recomputing similar key recomputations, etc., but design
     * ease is more important at the moment.  Besides, maybe we can someday update to a more clever
     * RTWLocation that would be able to cache partial computations here, or maybe use a different
     * way fo notifying subclasses about deletes, e.g. invoking a hook or callback once for each
     * element, or maybe subclasses could be forced to also do fast batch deletes.  We could
     * probably also call cullEmptyEntries less often if we got clever.
     *
     * Deleting the last remaining value in a slot is equivalent to deleting that slot, and so this
     * method may entail a slot delete.  When it does so, it will call signalDeleteSlot afterward so
     * that subclasses may trap this condition.  Trapping delete is not sufficient to catch all
     * cases of a slot being deleted.
     *
     * When this method deletes a slot, it will be responsible for deleting that slot's key from the
     * underlying DB and for invoking cullEmptyEntries in order to clean up any superslots that are
     * no longer needed as a consequence of this slot no longer existing.
     */
    protected void deleteValue(RTWLocation location, RTWValue value) {
        // log.debug("SLS deleteValue(" + location + ", " + value + ")"); //bkdb
        try {
            // Get the current value of the slot and either write a version with that value removed
            // or delete the slot value if it then becomes empty.  In niether case do we need to
            // worry about subslots on the slot, but we'll need to recurse and delete any subslots
            // attached to the value.
            //
            // This relies on culEmptyEntries to handle slotlist cleanup.  cullEmptyEntries will
            // automatically deal with deleting parent slots that become spurious as a result of
            // this delete, and will also remove entries from value sublist name partition and
            // directory entries.

            String slotAddr = constructSlotAddr(location, false);
            if (slotAddr == null)
                throw new RuntimeException("No such slot");

            // it (was) tempting to allow delete-list-all-at-once, but that would confuse with
            // deleting a list from a list.  Are we not planning to simplify everything here by
            // having only sets, and letting some higher-level layer enforce nrOfValues=1 as
            // necessary?  //bk:set

            // Recursively delete any subslots before we remove the value from the DB
            RTWLocation e = location.element(value);
            RTWListValue subslots = getSubslots(e);
            if (subslots != null) {
                for (RTWValue subslot : subslots) {
                    delete(e.subslot(subslot.asString()), false, true);
                }
            }

            // We'll want to re-fetch what we're about to delete in case our subslot deletes
            // above entailed any funny business (e.g. from a subclass)
            RTWListValue curVal = slsm.get(slotAddr);
            if (curVal == null) {
                return;  // Our work is already done
            }
            if (!curVal.contains(value)) {
                return;  // Our work is already done
            }
            if (curVal.size() == 1) {
                slsm.remove(slotAddr);
                cullEmptyEntries(location);
                signalDeleteSlot(location);
            } else {
                if (curVal instanceof RTWSetListValue) {
                    if (curVal instanceof RTWImmutableSetListValue) {
                        if (curVal.size() > kbMaxListSize) {
                            RTWSetListValue newVal = RTWSetListValue.copy(curVal);
                            newVal.remove(value);
                            slsm.put(slotAddr, newVal);
                        } else {
                            RTWArrayListValue newList = new RTWArrayListValue(curVal.size()-1);
                            for (RTWValue v : curVal)
                                if (!v.equals(value)) newList.add(v);
                            slsm.put(slotAddr, newList);
                        }
                    } else {
                        curVal.remove(value);
                        slsm.put(slotAddr, curVal);  // Notifies slsm to mark this entry dirty
                    }
                } else if (curVal instanceof RTWArrayListValue) {
                    if (curVal instanceof RTWImmutableListValue) {
                        if (curVal.size() > kbMaxListSize) {
                            RTWSetListValue newVal = RTWSetListValue.copy(curVal);
                            newVal.remove(value);

                            // 2012-10-02: Another pesky special case from older KBs is one where we
                            // a slot got set to a list of the same values repeated.  In such cases,
                            // we might wind up here with an empty set.  We call this a legitimate
                            // state of affairs if the name of the slot is source or probability
                            // because those are the slots that were "abused" in this fashion.  The
                            // code sitting on top of us (KbManipulation, SourceProb) is responsible
                            // for figuring out how to do the right thing once all the values vanish
                            // unexpectedly.  For our purposes, it is sufficient to maintain our own
                            // internal correctness by now deleting the slot rather than setting the
                            // slot to an empty set.
                            if (newVal.size() == 0) {
                                log.warn("Unexpected non-setness at " + location
                                        + ".  Slot no longer has any values");
                                slsm.remove(slotAddr);
                                cullEmptyEntries(location);
                                signalDeleteSlot(location);
                            } 

                            // Normalcy
                            else {
                                slsm.put(slotAddr, newVal);
                            }
                        } else {
                            ArrayList<RTWValue> newList = new ArrayList<RTWValue>((RTWListValue)curVal);
                            newList.remove(value);
                            RTWArrayListValue newVal = new RTWArrayListValue(newList);
                            slsm.put(slotAddr, newVal);
                        }
                    } else {
                        curVal.remove(value);
                        slsm.put(slotAddr, curVal);  // Notifies slsm to mark this entry dirty
                    }
                } else {
                    throw new RuntimeException("Unexpected slot container class type "
                            + curVal.getClass().getName());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("deleteValue(" + location + ", " + value + ")", e);
        }
    }

    /**
     * So this is basically all about recursive slotlist deletion and possibly cleaning up a subslot
     * partition and directory entry?  Yes -- it's the other "half" of having deleted a slot.  It is
     * responsible for deleting any parent slots that no longer need to exist as a result of this
     * slot no longer needing to exist.
     *
     * So, for outside calliers (i.e. deleteValue when it deletes the only remaining
     * value in a slot), location should be a slot that just became empty and whose slot address (as
     * returned by constructSlotAddr) has therefore just been removed from the underlying DB.
     *
     * This may call itself recursively with a location that ends in an element ref, which
     * potentially includes some cleanup (as detailed within the source), but which necessarily
     * terminates the recursion.
     */
    protected void cullEmptyEntries(RTWLocation location) {
        try {
            // log.debug("bkdb: cEE(" + location + ")");
            // If l ends in a subslot name, and there's nothing there and it has no subslots, then
            // we should delete the subslotlist entry for this location and then recurse into the
            // parent (if there is one).  This also works when location contains only an entity
            // name.
            if (location.endsInSlot()) {
                // FODO: this seems like a place where we're computing the slot address more often
                // than we need to needlessly (although arguably we shouldn't sweat it and just make
                // a custom RTWLocation instead).  I'm content to leave this as-is until we have a
                // reason to believe that this winds up being a problematic drag.  Deletes are not
                // our most common operation.
                String key = constructSlotAddr(location, false);
                RTWListValue v = slsm.get(key);
                if (v == null && getSubslots(location) == null) {
                    if (location.size() > 1) {
                        RTWLocation parentLocation = location.parent();
                        String parentKey = constructSlotAddr(parentLocation, false);
                        slotlistCache.removeSlot(parentKey, new RTWStringValue(location.lastAsSlot()), true);
                        cullEmptyEntries(parentLocation);
                    }
                }
            }

            // Operation here is similar, right down to finding out whether any subslots exist under
            // the indicated value.  But once we do hit this case, we stop recursing; a value does
            // not get deleted from a slot simply because it enters a state of having no subslots.
            else {
                // If subslots exist, don't blow this value away
                if (getSubslots(location) != null) return;

                // OK, blow it away.
                RTWValue value = location.lastAsValue();
                String keyPrefix = constructSlotAddr(location.parent(), false);
                String namePartitionHash = getNamePartitionHash(value);
                String namePartitionSlot = keyPrefix + namePartitionHash;
                RTWListValue namePairList = slsm.get(namePartitionSlot);
                if (namePairList == null)
                    throw new RuntimeException("\"" + namePartitionSlot + "\" doesn't exist");
                if (namePairList.size() == 1) {
                    if (!((RTWListValue)namePairList.get(0)).get(0).equals(value))
                        throw new RuntimeException("Failed to find \"" + value + "\" in \"" + namePartitionSlot + "\"");
                    slsm.remove(namePartitionSlot);

                    // In this case, because we've deleted the partition slot, we also have to
                    // remove the hash for this partition slot from the directory slot
                    String nameDirectorySlot = keyPrefix + "  D";
                    RTWListValue v = slsm.get(nameDirectorySlot);
                    if (v == null) {
                        // bkisiel 2012-12-04: I don't see as this should happen, but it did for
                        // unexpected reasons during a test on the 08m KB, so I'm relaxing this to
                        // an error for the time being to see how things play out.
                        // throw new RuntimeException("\"" + nameDirectorySlot + "\" doesn't exist");
                        log.error("\"" + nameDirectorySlot + "\" doesn't exist.  Force-deleting anyway and ignoring.");
                        slsm.remove(nameDirectorySlot);
                    } else {
                        ArrayList<RTWValue> newList = new ArrayList<RTWValue>(v);
                        if (!newList.remove(new RTWStringValue(namePartitionHash)))
                            throw new RuntimeException("\"" + namePartitionHash + "\" not found in \"" + nameDirectorySlot + "\" (" + v + ")");
                        if (newList.size() == 0)
                            slsm.remove(nameDirectorySlot);
                        else
                            slsm.put(nameDirectorySlot, new RTWImmutableListValue(newList));
                    }
                } else {
                    ArrayList<RTWValue> newList = new ArrayList<RTWValue>();
                    for (RTWValue pair : namePairList) {
                        if (!((RTWListValue)pair).get(0).equals(value))
                            newList.add(pair);
                    }
                    if (newList.size() != namePairList.size()-1)
                        throw new RuntimeException("Deleting entry for " + value + " illegally yielded " + newList + " from " + namePairList + ", found in \"" + namePartitionSlot);
                    slsm.put(namePartitionSlot, new RTWImmutableListValue(newList));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("cullEmptyEntries(" + location + ")", e);
        }
    }

    // this can return null (e.g. subslot under nonexistent value).
    //
    // FODO: we should probably rework this later on to properly adhere to all of the constraints
    // that have since come into play, e.g. RTWElementRef as second element constitues an illegal
    // location.
    protected String constructSlotAddr(final RTWLocation l, boolean createLocation) {
        // So the plan is to munge this into a regular String
        String slotAddr = null;
        try {
            for (int i = 0; i < l.size(); i++) {
                if (!l.isSlot(i)) {
                    RTWValue refVal = l.getAsElement(i).getVal();
                    if (slotAddr == null)
                        throw new RuntimeException("First element of RTWLocation must be an entity name");
                    String subslotName = null;  // Next bit to put into slotAddr
                    String namePartitionHash = getNamePartitionHash(refVal);
                    String namePartitionSlot = slotAddr + namePartitionHash;
                    RTWListValue namePairList = slsm.get(namePartitionSlot);
                    boolean alreadyHavePartitionEntry = false;
                    if (namePairList != null) {
                        for (RTWValue v : (RTWListValue)namePairList) {
                            RTWListValue pair = (RTWListValue)v;
                            if (refVal.equals(pair.get(0))) {
                                subslotName = pair.get(1).asString();
                                alreadyHavePartitionEntry = true;
                                break;
                            }
                        }
                    }
                    if (subslotName == null) {
                        // No subslots on the value indidcated in refVal.  So create the given
                        // subslot if this is a write and otherwise return a null database key to
                        // indicate the lack of a value.
                        if (!createLocation) return null;
                        if (isReadOnly())
                            throw new RuntimeException("Can't set createLocation=true when in read-only mode");

                        // We'd best make sure that the value we're supposed to be adding a subslot
                        // for actually exists.
                        RTWListValue slotVal = slsm.get(slotAddr);
                        if (slotVal == null)
                            throw new RuntimeException("Cannot create subslot on value " + refVal + " of \"" + slotAddr + "\" because that slot has no values");
                        if (!slotVal.contains(refVal))
                            throw new RuntimeException("Cannot create subslot on value " + refVal + " of \"" + slotAddr + "\" because it only contains values " + slotVal);

                        // First map the value to the new subslot name in the value subslot name
                        // partition slot
                        subslotName = getNewName(slotAddr, refVal, namePartitionHash, namePairList);
                        RTWValue pair = new RTWImmutableListValue(refVal, new RTWStringValue(subslotName));
                        if (namePairList == null) {
                            namePairList = new RTWImmutableListValue(pair);
                            slsm.put(namePartitionSlot, namePairList);
                            
                            // No pre-existing name partition slot value means that we just created
                            // a new one, and that means that we have to add it to the name
                            // directory slot (creating that if need be).
                            String nameDirectorySlot = slotAddr + "  D";
                            RTWListValue nameDirectory = slsm.get(nameDirectorySlot);
                            slsm.put(nameDirectorySlot,
                                    RTWImmutableListValue.append(nameDirectory,
                                            new RTWStringValue(namePartitionHash)));
                        } else {
                            slsm.put(namePartitionSlot, RTWImmutableListValue.append(namePairList, pair));
                        }
                    }

                    slotAddr = slotAddr + subslotName;
                }
                
                // Handle a subslot
                else {
                    String regularString = l.getAsSlot(i);
                    if (regularString.contains("\""))
                        throw new RuntimeException("Slot address contains a double-quote, indicating an error");
                    if (regularString.length() == 0)
                        throw new RuntimeException("Illegal empty string in element " + i + " of RTWLocation");
                    if (regularString.charAt(0) == '=')
                        throw new RuntimeException("The \"=\" prefix to denote an RTWElementRef is no longer supported in the way that it used to be.");
                    if (slotAddr == null) {
                        if (preventUppercase) {
                            // This is a faster way to check for lowercasing than toLowerCase.  This
                            // does in fact make a difference e.g. during relation kN checking.  In
                            // fact, that whole place might be a good case study for how to spend
                            // less time converting a slot list to a DB location (short of caching
                            // results, which I'm not sure would be really that easy to do
                            // effectively).
                            int len = regularString.length();
                            for (int j = 0; j < len; j++) {
                                char c = regularString.charAt(j);
                                if (c >= 'A' && c <= 'Z')
                                    throw new RuntimeException("bkdb: lowercasing error at " + regularString);
                            }
                        }

                        // Run the subslot through subslotTranslationTable if we have one.  Note
                        // that we do this after the above lowercasing check.  We also have this
                        // sleazy translation of the "concept:" prefix because there's not all that
                        // much of a reason to give up this easy way to save space.
                        if (subslotTranslationTable != null) {
                            String abbreviation = subslotTranslationTable.get(regularString);
                            if (abbreviation != null) {
                                regularString = abbreviation;
                            } else {
                                if (regularString.length() > 8 && regularString.substring(0, 8).equals("concept:"))
                                    regularString = " C" + regularString.substring(8);
                            }
                        } 

                        slotAddr = regularString;
                    } else {
                        if (createLocation)
                            slotlistCache.addSlot(slotAddr, new RTWStringValue(regularString)); // FODO: we might be able to reuse an RTWStringValue from above, but not worth trying to work that out until we have bk:entityref and RTWLocationRef sorted

                        // Run the subslot through subslotTranslationTable if we have one.  Note
                        // that we do this after manipulating the slotlist; the slotlist does not
                        // contain translated names.  Not sure that it would be a valuable
                        // time/space tradeoff -- we'd probably have to put the translation inside
                        // the slotlist class itself and cache the translations for speed, which is
                        // probably not worth the effort.  We also have this sleazy translation of
                        // the "concept:" prefix because there's not all that much of a reason to
                        // give up this easy way to save space.
                        if (subslotTranslationTable != null) {
                            String abbreviation = subslotTranslationTable.get(regularString);
                            if (abbreviation != null) {
                                regularString = abbreviation;
                            } else {
                                if (regularString.length() > 8 && regularString.substring(0, 8).equals("concept:"))
                                    regularString = " C" + regularString.substring(8);
                            }
                        }

                        // FODO: This turns out to be a little bit of a hotspot.  Maybe it could
                        // help if we could optimize the case of successive subslots or whatever it
                        // would take to do this with a single StringBuilder rather than doing it
                        // once per iteration?
                        slotAddr = slotAddr + " " + regularString;
                    }
                }
            }
            return slotAddr;
        } catch (Exception e) {
            if (slotAddr == null) slotAddr = "";
            throw new RuntimeException("While resolving RTWLocation " + l + " after \""
                    + slotAddr + "\"", e);
        }
    }

    /**
     * Close DB when something aborts.
     *
     * We expect that the SLSM will flush as appropriate on its way to closure.
     */
    @Override protected void finalize() throws Throwable {
        if (isOpen()) close();
    }

    /**
     * Returns null on not found, not a null RTWValue
     *
     * This is and can no longer be used on an element reference.
     */
    protected RTWListValue get(RTWLocation location) {
        try {
            if (location.endsInSlot()) {
                String key = constructSlotAddr(location, false);
                if (key == null) return null;
                RTWListValue list = slsm.get(key);
                return list;
            } else {
                throw new RuntimeException("What are you doing?");
            }
        } catch (Exception e) {
            throw new RuntimeException("get(" + location + ")", e);
        }
    }

    /**
     * Helper bulk-add for {@link highLevelCopy}
     *
     * Way dangerous.  Don't use.
     */
    protected void put(RTWLocation location, RTWListValue values) {
        String key = constructSlotAddr(location, true);
        slsm.put(key, values);
    }

    /**
     * Helper copier for {@link highLevelCopy}
     */
    /*
     * bkdb TODO: move this to a tool or whatever as needed (probably after automating the substring table)
     *
    protected void highLevelCopyLocation(StringListStore dstStore, RTWLocation location) {
        try {
            // Copy slot contents if this location ends in a slot and is not a primitive entity
            if (location.endsInSlot() && location.size() > 1) {

                // Screw piecemeal copy of all values in location!  We have a need for speed.
                RTWListValue values = get(location);
                if (values != null) {
                    dstStore.put(location, values);
                
                    // We do, however, need a piecemeal recursion into each element in order to
                    // recursively copy any slots hanging off of each one.
                    for (RTWValue value : values)
                        highLevelCopyLocation(dstStore, location.element(value));
                }
            }

            // Whether this location is a primitive entity, a slot, or a value, recurse into all of
            // its subslots
            RTWListValue subslots = getSubslots(location);
            if (subslots != null) {
                for (RTWValue subslot : getSubslots(location))
                    highLevelCopyLocation(dstStore, location.subslot(subslot.asString()));
            }
        } catch (Exception e) {
            throw new RuntimeException("highLevelCopyLocation(<dst>, " + location + ")", e);
        }
    }
    */

    /**
     * High-level copy (i.e. copying at the Store level rather than the StrinListStoreMap level.
     *
     * The main impetus for this is to add/change/remove the subslotTranslationTable in an existing
     * Store.
     *
     * FODO: It'd be cool to have some kind tools/KBTool sort of program to be able to do this sort
     * of thing more generally.  That'd be a nicer option than DumpKB -w for forcing format updates
     * as well.
     */
    /*
     * bkdb TODO: move this to a tool or whatever as needed (probably after automating the substring table)
     *
    protected void highLevelCopy(String filename) {
        try {
            log.info("Performing high level copy into \"" + filename + "\"...");
            File dstFile = new File(filename);
            dstFile.delete();
            
            // FODO: this is ghetto; we're not supposed to know to use TCHStoreMap here.  Similarly
            // for KbManipulation.  Better to clean this up post-Theo2012.
            //
            // FODO: set the number of buckets like optimize would do.  Not setting it is good
            // enough for a single-shot conversion, though; this would be important if we were to
            // reconvert regularly, e.g. if we wanted to recompute an optimal
            // subslotTranslationTable every so often.
            //
            // Also, turn off caching because we'll just be writing each entry once.
            TCHStoreMap sm = new TCHStoreMap();
            sm.setCacheSize(0);
            StringListStore dstStore = new StringListStore(sm);
            dstStore.open(filename, false);

            Iterator<String> it = getPrimitiveEntityIterator();
            int cnt = 0;
            while (it.hasNext()) {
                cnt++;
                if (cnt % 100000 == 0) {
                    log.info("Copied " + (cnt/1000) + "k primitives...");
                }
                String primitive = it.next();
                highLevelCopyLocation(dstStore, getLoc(primitive));
            }
            log.info("Finished copying " + cnt + " primitives");
            dstStore.close();
        } catch (Exception e) {
            throw new RuntimeException("highLevelCopy(\"" + filename + "\")", e);
        }
    }
    */

    public StringListStore(SLSM slsm) {
        this.slsm = slsm;
        Properties properties = TheoFactory.getProperties();
        kbMaxListSize = properties.getPropertyIntegerValue("kbMaxListSize", 100);
        preventUppercase = properties.getPropertyBooleanValue("preventUppercase", false);  // bkdb: be sure to coordinate agreement on this default between InMind and NELL during the wedge merge
    }
    
    @Override public RTWLocation getLoc(RTWLocation l) {
        // FODO: this is lame.  cf. other places that would prefer to use the Object-returning
        // RTWLocation.get that is currently protected and unique to RTWLocationBase.  But this
        // ought to be highly uncommon, and I'd rather push Theo2012 forward now and then look back
        // later to decide RTWLocation's fate.
        Object[] addr = new Object[l.size()];
        for (int i = 0; i < l.size(); i++) {
            if (l.isSlot(i)) addr[i] = l.getAsSlot(i);
            else addr[i] = l.getAsElement(i);
        }
        return new SLSRTWLocation(addr);
    }
    
    public RTWLocation getLoc(RTWLocationBase l) {
        // Common case and faster
        return new SLSRTWLocation(l.addr);
    }

    @Override public RTWLocation getLoc(Object... list) {
        return new SLSRTWLocation(list);
    }

    @Override public RTWLocation getLoc(List<String> list) {
        return new SLSRTWLocation(list);
    }

    @Override public RTWLocation getLoc(String[] list) {
        return new SLSRTWLocation(list);
    }

    @Override public void open(String filename, boolean openInReadOnlyMode) {
        try {
            slsm.open(filename, openInReadOnlyMode);
            boolean wasEmpty = (slsm.size() == 0);

            // N.B. We have to update the slotlist before doing subslotTranslationTable stuff
            // because otherwise the subslotTranslationTable entry will be given a slotlist.
            slotlistCache = new SlotlistCache();
            slotlistCache.updateSlotlistsIfNecessary();

            // If this is a fresh database, then use a translation table
            subslotTranslationTable = null;
            if (wasEmpty && !openInReadOnlyMode) {
                log.debug("Empty store detected.  Initializing default subslotTranslationTable");
                
                // Hardcoding is good enough for now.  We'll adopt the standard of using a " T"
                // prefix for translations, and try to keep them down to three characters apiece.
                // This meshes with our use of things like " D" and " S".
                subslotTranslationTable = new HashMap<String, String>();
                subslotTranslationTable.put("candidate:generalizations", " Tc");
                subslotTranslationTable.put("mutexexceptions", " Te");
                subslotTranslationTable.put("generalizations", " Tg");
                subslotTranslationTable.put("justification", " Tj");
                subslotTranslationTable.put("knownnegatives", " Tk");
                subslotTranslationTable.put("literalstring", " Tl");
                subslotTranslationTable.put("mutexpredicates", " Tm");
                subslotTranslationTable.put("pramodel", " Tp");
                subslotTranslationTable.put("referstoconcept", " Tr");
                subslotTranslationTable.put("sourceprob", " Ts");
                subslotTranslationTable.put("referredtobytoken", " Tt");
                subslotTranslationTable.put("extractionpatterns", " Tx");

                // We'll just hardcode a few very common kinds here and hope to pick up a decent
                // chunk of savings.  Some but not all of these were actually chosen based on
                // tallying up the number of bytes they actually consumed in a real NELL KB.
                subslotTranslationTable.put("candidate:concept:academicfieldsuchasacademicfield", " TA");
                subslotTranslationTable.put("candidate:concept:academicprogramatuniversity", " TB");
                subslotTranslationTable.put("candidate:concept:agentbelongstoorganization", " TC");
                subslotTranslationTable.put("candidate:concept:agentcollaborateswithagent", " TD");
                subslotTranslationTable.put("candidate:concept:agentcompeteswithagent", " TE");
                subslotTranslationTable.put("candidate:concept:agentcontrols", " TF");
                subslotTranslationTable.put("candidate:concept:agentcreated", " TG");
                subslotTranslationTable.put("candidate:concept:agentinvolvedwithitem", " TH");
                subslotTranslationTable.put("candidate:concept:agentparticipatedinevent", " TI");
                subslotTranslationTable.put("candidate:concept:athleteinjuredhisbodypart", " TJ");
                subslotTranslationTable.put("candidate:concept:athleteplayssportsteamposition", " TK");
                subslotTranslationTable.put("candidate:concept:animalpreyson", " TL");
                subslotTranslationTable.put("candidate:concept:atlocation", " TM");
                subslotTranslationTable.put("candidate:concept:awardtrophytournamentisthechampionshipgameofthenationalsport", " TN");
                subslotTranslationTable.put("candidate:concept:bodypartcontainsbodypart", " TO");
                subslotTranslationTable.put("candidate:concept:competeswith", " TP");
                subslotTranslationTable.put("candidate:concept:haswikipediaurl", " TQ");
                subslotTranslationTable.put("candidate:concept:haswife", " TR");
                subslotTranslationTable.put("candidate:concept:hasspouse", " TS");
                subslotTranslationTable.put("candidate:concept:journalistwritesforpublication", " TT");
                subslotTranslationTable.put("candidate:concept:locationlocatedwithinlocation", " TU");
                subslotTranslationTable.put("candidate:concept:mutualproxyfor", " TV");
                subslotTranslationTable.put("candidate:concept:personleadsorganization", " TW");
                subslotTranslationTable.put("candidate:concept:personborninlocation", " TX");
                subslotTranslationTable.put("candidate:concept:personbelongstoorganization", " TY");
                subslotTranslationTable.put("candidate:concept:proxyfor", " TZ");
                subslotTranslationTable.put("candidate:concept:teamalsoknownas", " T0");
                subslotTranslationTable.put("candidate:concept:visualartistartform", " T1");
                subslotTranslationTable.put("candidate:concept:worksfor", " T2");
                subslotTranslationTable.put("candidate:concept:sportusesequipment", " T3");
                subslotTranslationTable.put("candidate:concept:sporthassportsteamposition", " T4");
                subslotTranslationTable.put("candidate:concept:specializationof", " T5");
                subslotTranslationTable.put("candidate:concept:subpartoforganization", " T6");
                subslotTranslationTable.put("candidate:concept:subpartof", " T7");
                subslotTranslationTable.put("candidate:concept:professionusestool", " T8");
                subslotTranslationTable.put("candidate:concept:professionistypeofprofession", " T9");
                subslotTranslationTable.put("candidate:concept:politicianusendorsedbypoliticianus", " T!");
                subslotTranslationTable.put("candidate:concept:politicianrepresentslocation", " T@");
                subslotTranslationTable.put("candidate:concept:plantrepresentemotion", " T#");
                subslotTranslationTable.put("candidate:concept:organizationacronymhasname", " T$");
                subslotTranslationTable.put("candidate:concept:musicianplaysinstrument", " T%");
                subslotTranslationTable.put("candidate:concept:musicartistgenre", " T^");
                subslotTranslationTable.put("candidate:concept:istallerthan", " T&");
                subslotTranslationTable.put("candidate:concept:furniturefoundinroom", " T*");
                subslotTranslationTable.put("candidate:concept:emotionassociatedwithdisease", " T(");
                subslotTranslationTable.put("candidate:concept:competeswith", " T)");
                subslotTranslationTable.put("candidate:concept:countryalsoknownas", " T`");
                subslotTranslationTable.put("candidate:concept:clothingmadefromplant", " T~");
                subslotTranslationTable.put("candidate:concept:bookwriter", " T-");
                subslotTranslationTable.put("candidate:concept:bakedgoodservedwithbeverage", " T_");
                subslotTranslationTable.put("candidate:concept:agentactsinlocation", " T=");
                subslotTranslationTable.put("candidate:concept:latitudelongitude", " T+");
                subslotTranslationTable.put("candidate:concept:agriculturalproductincludingagriculturalproduct", " T[");
                subslotTranslationTable.put("candidate:concept:chemicalistypeofchemical", " T]");
                subslotTranslationTable.put("candidate:concept:agriculturalproductcutintogeometricshape", " T{");
                subslotTranslationTable.put("candidate:concept:companyeconomicsector", " T}");
                subslotTranslationTable.put("concept:haswikipediaurl", " T\\");
                subslotTranslationTable.put("candidate:concept:arterycalledartery", " T|");
                subslotTranslationTable.put("candidate:concept:persongraduatedfromuniversity", " T;");
                subslotTranslationTable.put("candidate:concept:invertebratefeedonfood", " T:");
                subslotTranslationTable.put("candidate:concept:animalistypeofanimal", " T'");
                subslotTranslationTable.put("candidate:concept:politicianusholdsoffice", " T\"");
                subslotTranslationTable.put("candidate:concept:organizationhiredperson", " T,");
                subslotTranslationTable.put("candidate:concept:synonymfor", " T.");
                subslotTranslationTable.put("candidate:concept:hasofficeincountry", " T<");
                subslotTranslationTable.put("candidate:concept:agriculturalproductgrowninlandscapefeatures", " T>");
                subslotTranslationTable.put("candidate:concept:politicianholdsoffice", " T/");
                subslotTranslationTable.put("candidate:concept:agriculturalproductcookedwithagriculturalproduct", " T?");

                // Eh, as long as we're at it, why not expand into four-character sequences to catch
                // some more stuff, eh.  This list starts off by replacings lot names that each
                // consume in the neighborhood of 400MB in the ongoing run.  But it turns out they
                // don't amount to the kind of savings we would expect.
                //
                // FODO: Having now been through this exercise, I think it would be better to
                // summarily and automatically convert all slot names to abbreviations, adding to
                // the translation table as we encounter new ones.  Then at least we have as much
                // saving as possible with constant minimum effort.  Also, while wall time does not
                // seem to have been affected in any material way by the introduction of this table,
                // it's not entirely clear that we aren't facing greater overhead seen in user time
                // from the likes of object creation and garbage collection -- there's just too much
                // of that overall to gauge the difference without stopping to do a controlled
                // comparison.
                subslotTranslationTable.put("candidate:concept:citylocatedincountry", " TAa");
                subslotTranslationTable.put("candidate:concept:beveragecontainsprotein", " TAb");
                subslotTranslationTable.put("candidate:concept:itemfoundinroom", " TAc");
                subslotTranslationTable.put("candidate:concept:drugpossiblytreatsphysiologicalcondition", " TAd");
                subslotTranslationTable.put("candidate:concept:clothingtogowithclothing", " TAe");
                subslotTranslationTable.put("candidate:concept:teamplaysagainstteam", " TAf");
                subslotTranslationTable.put("candidate:concept:hasofficeincity", " TAg");
                subslotTranslationTable.put("candidate:concept:buildingfeaturemadefrombuildingmaterial", " TAh");
                subslotTranslationTable.put("candidate:concept:drugworkedonbyagent", " TAi");
                subslotTranslationTable.put("candidate:concept:teamplayssport", " TAj");
                subslotTranslationTable.put("candidate:concept:citylocatedinstate", " TAk");
                subslotTranslationTable.put("candidate:concept:athleteplayssport", " TAl");
                subslotTranslationTable.put("candidate:concept:personhasjobposition", " TAm");
                subslotTranslationTable.put("candidate:concept:plantgrowinginplant", " TAn");
                subslotTranslationTable.put("candidate:concept:attractionmadeofbuildingmaterial", " TAo");
                subslotTranslationTable.put("candidate:concept:stadiumlocatedincity", " TAp");
                subslotTranslationTable.put("candidate:concept:musicianinmusicartist", " TAq");
                subslotTranslationTable.put("candidate:concept:actorstarredinmovie", " TAr");
                subslotTranslationTable.put("candidate:concept:buildinglocatedincity", " TAs");
                subslotTranslationTable.put("candidate:concept:newspaperincity", " TAt");
                subslotTranslationTable.put("candidate:concept:countrylocatedingeopoliticallocation", " TAu");
                subslotTranslationTable.put("candidate:concept:drughassideeffect", " TAv");
                subslotTranslationTable.put("candidate:concept:agriculturalproductcomingfromvertebrate", " TAw");
                subslotTranslationTable.put("candidate:concept:producesproduct", " TAx");
                subslotTranslationTable.put("candidate:concept:persongraduatedschool", " TAy");
                subslotTranslationTable.put("candidate:concept:museumincity", " TAz");
                subslotTranslationTable.put("candidate:concept:cityliesonriver", " TAA");
                subslotTranslationTable.put("candidate:concept:attractionofcity", " TAB");
                subslotTranslationTable.put("candidate:concept:statelocatedincountry", " TAC");
                subslotTranslationTable.put("candidate:concept:arthropodandotherarthropod", " TAD");
                subslotTranslationTable.put("candidate:concept:companyalsoknownas", " TAE");
                subslotTranslationTable.put("candidate:concept:televisionstationincity", " TAF");
                subslotTranslationTable.put("candidate:concept:agentcontributedtocreativework", " TAG");
                subslotTranslationTable.put("candidate:concept:plantincludeplant", " TAH");
                subslotTranslationTable.put("candidate:concept:writerwasbornincity", " TAI");
                subslotTranslationTable.put("candidate:concept:athleteplaysforteam", " TAJ");
                subslotTranslationTable.put("candidate:concept:headquarteredin", " TAK");
                subslotTranslationTable.put("candidate:concept:sportsgameteam", " TAL");
                subslotTranslationTable.put("candidate:concept:countrycurrency", " TAM");
                subslotTranslationTable.put("candidate:concept:personhascitizenship", " TAN");
                subslotTranslationTable.put("candidate:concept:ismultipleof", " TAO");
                subslotTranslationTable.put("candidate:concept:hotelincity", " TAP");
                subslotTranslationTable.put("candidate:concept:bacteriaisthecausativeagentofphysiologicalcondition", " TAQ");
                subslotTranslationTable.put("candidate:concept:arthropodcalledarthropod", " TAR");
                subslotTranslationTable.put("candidate:concept:fooddecreasestheriskofdisease", " TAS");
                subslotTranslationTable.put("candidate:concept:agriculturalproductgrowinginstateorprovince", " TAT");
                subslotTranslationTable.put("candidate:concept:statehascapital", " TAU");
                subslotTranslationTable.put("candidate:concept:agriculturalproductcontainchemical", " TAV");
                subslotTranslationTable.put("candidate:concept:teammate", " TAW");
                subslotTranslationTable.put("candidate:concept:malemovedtostateorprovince", " TAX");
                subslotTranslationTable.put("candidate:concept:agriculturalproducttoattractinsect", " TAY");
                subslotTranslationTable.put("candidate:concept:personattendsschool", " TAZ");
                subslotTranslationTable.put("candidate:concept:personwasborninstateorprovince", " TA0");
                subslotTranslationTable.put("candidate:concept:foodcancausedisease", " TA1");
                subslotTranslationTable.put("candidate:concept:animaldevelopdisease", " TA2");
                subslotTranslationTable.put("candidate:concept:personhasresidenceingeopoliticallocation", " TA3");
                subslotTranslationTable.put("candidate:concept:teamplaysincity", " TA4");
                subslotTranslationTable.put("candidate:concept:personbornincity", " TA5");
                subslotTranslationTable.put("candidate:concept:directordirectedmovie", " TA6");
                subslotTranslationTable.put("candidate:latitudelongitude", " TA7");

                // And store this translation table.  We'll just use a straight list where each pair
                // of items is a (key, value) pair, except the first element of the list will be a
                // version indicator so as to provide room for future improvements.
                RTWListValue list = new RTWArrayListValue();
                list.add(new RTWIntegerValue(0));
                for (String key : subslotTranslationTable.keySet()) {
                    list.add(new RTWStringValue(key));
                    list.add(new RTWStringValue(subslotTranslationTable.get(key)));
                }

                // FODO: we need a proper doctrine for storing out-of-band information so that
                // nothing else feeling clever would ever use this slot name.
                slsm.put(" subslotTranslationTable", list);
            }

            // Else load the existing subslotTranslationTable, if any
            else {
                RTWListValue list = slsm.get(" subslotTranslationTable");
                if (list != null) {
                    int version = list.get(0).asInteger();
                    if (version > 0)
                        throw new RuntimeException("subslotTranslationTable version " + version
                                + " is too new.  Looks like you'll need to find a more recent version of this class.");
                    subslotTranslationTable = new HashMap<String, String>();
                    for (int i = 1; i < list.size(); i+= 2) {
                        RTWStringValue key = (RTWStringValue)list.get(i);
                        RTWStringValue value = (RTWStringValue)list.get(i+1);
                        subslotTranslationTable.put(key.asString(), value.asString());
                    }
                    log.debug("Loaded subslotTranslationTable with "
                            + subslotTranslationTable.size() + " entries");
                    if (subslotTranslationTable.size() == 0) subslotTranslationTable = null;
                }
            }

            // And if we have a subslotTranslationTable at this point, then generate
            // subslotUntranslationTable from it.
            if (subslotTranslationTable != null) {
                subslotUntranslationTable = new HashMap<String, String>();
                for (Map.Entry<String, String> entry : subslotTranslationTable.entrySet())
                    subslotUntranslationTable.put(entry.getValue(), entry.getKey());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("open(\"" + filename + "\", " + openInReadOnlyMode + ")", e);
        }
    }

    @Override public boolean isOpen() {
        return slsm.getLocation() != null;
    }

    @Override public boolean isReadOnly() {
        return slsm.isReadOnly();
    }

    /**
     * Switch the DB into or out of read-only mode
     */
    @Override public void setReadOnly(boolean makeReadOnly) {
        if (makeReadOnly) {
            if (isReadOnly()) return;
            String currentLocation = slsm.getLocation();
            slsm.close();
            slsm.open(currentLocation, true);
        } else {
            if (!isReadOnly()) return;
            String currentLocation = slsm.getLocation();
            slsm.close();
            slsm.open(currentLocation, false);
        }
    }

    /**
     * Return the file that is currently open, or null if none is
     */
    @Override public String getFilename() {
        return slsm.getLocation();
    }

    @Override public void flush(boolean sync) {
        slsm.flush(sync);
    }

    /**
     * Close the Store.  This should always be called when done using the KB.
     */
    @Override public void close() {
        slsm.close();
        slotlistCache = null;
    }

    @Override public void copy(String filename) {
        slsm.copy(filename);
    }

    @Override public void logStats() {
        slsm.logStats();
    }

    @Override public RTWListValue getSubslots(RTWLocation location) {
        try {
            String key = constructSlotAddr(location, false);
            if (key == null) return null;
            return slotlistCache.getSubslots(key);
        } catch (Exception e) {
            throw new RuntimeException("getSubslots(" + location + ")", e);
        }
    }

    @Override public int getNumValues(RTWLocation location) {
        // We can make this more efficient once we have natively-stored sets
        RTWValue v = get(location);
        if (v == null) return 0;
        if (location.endsInSlot()) return ((RTWListValue)v).size();
        else return 1;
    }

    @Override public boolean add(RTWLocation location, RTWValue value) {
        try {
            if (isReadOnly())
                throw new RuntimeException("Cannot add when in read-only mode");
            if (!location.endsInSlot())
                throw new RuntimeException("Cannot add a value to an element reference");
            if (value == null)
                throw new RuntimeException("Adding a null value is nonsensical");
            if (location.size() == 1)
                throw new RuntimeException("A top-level entity may not be used as a slot");

            // 2012-09-11: Here we auto-convert the RTWListValue object for this slot into an
            // RTWSetListValue if the number of values exceeds kbMaxListSize.  This makes it
            // possible to efficiently manipulate slots that contain too many elements without
            // having to change the underlying DB to store sets or refactor StringListStoreMap to
            // store something other than an RTWListValue.  We persue this stopgap for now in order
            // to push forward on the effort to get everything using Theo2012.  But it will be
            // interesting to see how far this gets us.  It even has the potential advantage of
            // being faster than storing sets in the DB by reducing the number of reads and writes,
            // and of reducing the pressure on the DB cache.
            //
            // We might could do this conversion earlier (like even down in the UTF8-demarshalling
            // in KbUtility), but we'll hold off on extra complexity until we see that it is
            // necessary.

            String key = constructSlotAddr(location, true);
            RTWListValue curVal = slsm.get(key);
            if (curVal == null) {
                // FODO: maybe we should have an RTWValueFactory sort of thing, or maybe a method on
                // RTWValue that can gaurantee immutability for us?
                if (value instanceof RTWListValue && !(value instanceof RTWImmutableListValue))
                    value = RTWImmutableListValue.copy((RTWListValue)value);

                slsm.put(key, new RTWArrayListValue(value));
                return true;
            } else {
                if (curVal.contains(value)) return false;  // Enforce setness
                // FODO: maybe we should have an RTWValueFactory sort of thing, or maybe a method on
                // RTWValue that can gaurantee immutability for us?
                if (value instanceof RTWListValue && !(value instanceof RTWImmutableListValue))
                    value = RTWImmutableListValue.copy((RTWListValue)value);

                if (curVal instanceof RTWArrayListValue) {
                    if (!(curVal instanceof RTWImmutableListValue) && curVal.size() <= kbMaxListSize) {
                        curVal.add(value);
                        slsm.put(key, curVal);  // Notifies slsm that this key is dirty
                    } else if (curVal.size() > kbMaxListSize) {
                        curVal = RTWSetListValue.append(curVal, value);
                        slsm.put(key, curVal);
                    } else {
                        curVal = RTWArrayListValue.append(curVal, value);
                        slsm.put(key, curVal);
                    }
                } else if (curVal instanceof RTWSetListValue) {
                    if (!(curVal instanceof RTWImmutableSetListValue) && curVal.size() >= kbMaxListSize) {
                        curVal.add(value);
                        slsm.put(key, curVal);  // Notifies slsm that this key is dirty
                    } else if (curVal.size() >= kbMaxListSize) {
                        curVal = RTWSetListValue.append(curVal, value);
                        slsm.put(key, curVal);
                    } else {
                        curVal = RTWArrayListValue.append(curVal, value);
                        slsm.put(key, curVal);
                    }
                } else {
                    throw new RuntimeException("Unexpected slot container class type "
                            + curVal.getClass().getName());
                }
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException("add(" + location + ", " + value + ")", e);
        }
    }

    @Override public boolean delete(RTWLocation location, boolean errIfMissing, boolean recursive) {
        // log.debug("SLS: delete(" + location + ", " + errIfMissing + ", " + recursive + ")"); //bkdb
        try {
            if (isReadOnly())
                throw new RuntimeException("Cannot delete when in read-only mode");

            // The location ends in a subslot, meaning that this operation amounts to deleting all
            // values in that slot.  This case could be put into its own method for cleanliness, but
            // we leave it here to avoid confusion with signalDeleteSlot; only by overriding
            // signalDeleteSlot can a subclass trap all cases of a slot being deleted.
            if (location.endsInSlot()) {
                String key = constructSlotAddr(location, false);
                if (key == null) {
                    if (errIfMissing) throw new RuntimeException("No such slot");
                    return false;
                }
                RTWListValue curVal = slsm.get(key);
                if (curVal == null && !recursive) {
                    if (errIfMissing) throw new RuntimeException("Slot has no value");
                    return false;
                }

                // Use this iterative fetch-and-delete to reduce the possiblity of some subclass
                // overriding deleteValue in such a way that the slot we're operating on changes
                // in ways other than we might expect from the primitive deletes we're
                // performing here.
                boolean deletedSomething = false;
                while (true) {
                    curVal = slsm.get(key);
                    if (curVal == null) break;

                    // Shouldn't have zero-length lists stored, but this is not irrecoverable, and
                    // it does occurr in older KBs, so we'll autofix it and complain loudly to the
                    // logs.
                    if (curVal.size() == 0) {
                        log.error("Zero-length list detected at " + location
                                + "!  Treating as empty slot.");
                        slsm.remove(key);
                        cullEmptyEntries(location);
                        signalDeleteSlot(location);
                        deletedSomething = true;
                        break;
                    }
                    deleteValue(location, curVal.get(0));
                    deletedSomething = true;
                }

                // bkdb 2014-11-15: Because of the current setup, TCHStoreMap can decide to delete a
                // key, and that might cause the subslot list to have extraneous entries.  This kind
                // of extra check probably belongs in a separate fsck-type thing, and refactoring
                // the current logic to have TCHStoreMap not do such things is probably a good idea
                // anyway, but this should be expedient for now.
                //
                // So, if we didn't delete anything, see if the subslot list things the slot we were
                // just supposed to have deleted still exists, and if it does, then do all the usual
                // bookkeeping to remove the subslotlist entry, signal the deletion of a slot, etc.
                if (!deletedSomething) {
                    RTWListValue subslots = getSubslots(location.parent());
                    if (subslots != null && subslots.contains(new RTWStringValue(location.lastAsSlot()))) {
                        log.error(location + " found not to exist during delete despite being present in the subslot list!  Treating as empty slot.");
                        cullEmptyEntries(location);
                        signalDeleteSlot(location);
                        deletedSomething = true;
                    }
                }

                // If a recursive delete, then delete all subslots as well.  Again use
                // fetch-and-delete loop.
                if (recursive) {
                    while (true) {
                        RTWListValue subslots = getSubslots(location);
                        if (subslots == null) break;

                        // bkisiel 2013-02-18: We used to have errIfMissing=false here, but I don't
                        // see why we couldn't make it true; it seems to me that we'd have an
                        // infinite loop or a bug in calculating the return value if
                        // errIfMissing=true were ever to cause a problem.
                        delete(location.subslot(subslots.get(0).asString()), true, true);
                        deletedSomething = true;
                    }
                }

                // No need for subslotlist management: it happened when we deleted the last element
                // from the slot; no elements cause automatic deletion.
                if (errIfMissing && !deletedSomething)
                    throw new RuntimeException("Expected to delete something but nothing was deleted");
                return deletedSomething;
            }
            
            // This location ends in an elementRef, meaning that we just fall directly through to
            // deleteValue.  Except that deleteValue requires that we do the existence check first,
            // so do that first.
            else {
                RTWLocation slot = location.parent();
                String key = constructSlotAddr(slot, false);
                if (key == null) {
                    if (errIfMissing) throw new RuntimeException("No such slot");
                    return false;
                }

                RTWValue value = location.lastAsValue();
                RTWListValue curVal = slsm.get(key);
                if (curVal == null) {
                    if (errIfMissing) throw new RuntimeException("Slot has no value");
                    return false;
                }

                if (!curVal.contains(value)) {
                    if (errIfMissing) 
                        throw new RuntimeException(value + " not found in \""
                                + key + "\" (" + curVal + ")");
                    return false;
                }

                deleteValue(slot, value);
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException("delete(" + location + ", " + errIfMissing
                    + ", " + recursive + ")", e);
        }
    }

    @Override public void giveLargeAccessHint() {
        slsm.giveLargeAccessHint();
    }

    @Override public void optimize() {
        slsm.optimize();
    }

    @Override public Iterator<String> getPrimitiveEntityIterator() {
        return new PrimitiveEntityIterator();
    }

    // bkisiel 2012-05-29: Now that we're going to be using more than one backend storage engine
    // (i.e. more than just Tokyo Cabinet), we need a way to copy a KB from one format to the other.
    // Obvious candidate for a KBTool or something.  The thorn in our side, here, though, is that we
    // don't currently have KBs that are "correct" in the Theo2012 sense of being able to reach
    // every primitive entity via the generalizations hierarchy, meaning that we can't quite cleanly
    // iterate through an entire KB using the kind of higher-level code we'd want to in a fully
    // general KBTool kind of thing.  So, rather than spend a lot of time on an approximation
    // (e.g. defining an iterator at the Store level over all primitive entities such as we might
    // want to have anyway as a step toward stochastic training example iteration), we can define a
    // full copy down here at the StringListStore level because we know that our underlying
    // StringListStoreMap offers an iterator over every entry in the KB, and we can use that to
    // visit all primitive entities exhaustively (and then use the Store-level API to perform a
    // "copy this whole entity" operation).
    //
    // 2012-10-09: Store now has a primitive entity iterator, and modern KBs require that all
    // primitive entities participate in the generalizations hierarchy, so we no longer need this
    // Store-specific recursive copy.  But we'll keep around the implementation a little while
    // longer in case we need to resurect it.  Ideally, we'll blow it away after everything is
    // sitting on top of Theo2012.

    public static void main(String args[]) throws Exception {
        try {

            /* TODO: delete me after Theo2012 subsumes this functionality
            // Copy a TCH-based store to a HashMapStoreMap-based store
            String cmd = args[0];

            if (cmd.equals("tch2hm")) {
                String srcloc = args[1];
                String dstloc = args[2];

                TCHStoreMap tsm = new TCHStoreMap();
                StringListStore srcStore = new StringListSuperStore(tsm);
                srcStore.open(srcloc, true);
                StringListStore dstStore = new StringListSuperStore(new HashMapStoreMap());
                dstStore.open(dstloc, false);

                Set<String> srcKeySet = tsm.keySet();
                int numKeys = srcKeySet.size();
                log.info(numKeys + " keys to process...");
                int keyCnt = 0;
                int percent = 0;
                for (String key : srcKeySet) {
                    keyCnt++;
                    int newPercent = (int)(((double)keyCnt / (double)numKeys) * 100.0);
                    if (newPercent != percent) {
                        long totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
                        long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
                        log.info(newPercent + "% done..."
                                + " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)");
                        percent = newPercent;
                    }

                    // Every primitive entity that exists must have a subslot list, so look for keys
                    // of that form.
                    int firstSpace = key.indexOf(" ");
                    if (firstSpace <= 0) {
                        // This happens for the " " key used for internal metadata
                        continue;
                    }
                    if (key.charAt(firstSpace + 1) == ' ' && key.charAt(firstSpace + 2) == 'S') {
                        String entity = key.substring(0, firstSpace);
                        copyRecursively(srcStore, dstStore, new SLSRTWLocation(entity));
                    }
                }
                log.info("Done copying");
                dstStore.close();
                srcStore.close();
            }

            // Copy HM to TCH (we can ditch this after we get automatic format detection and
            // requestion)
            else if (cmd.equals("hm2tch")) {
                String srcloc = args[1];
                String dstloc = args[2];

                HashMapStoreMap hmsm = new HashMapStoreMap();
                StringListStore srcStore = new StringListSuperStore(hmsm);
                srcStore.open(srcloc, true);
                StringListStore dstStore = new StringListSuperStore(new TCHStoreMap());
                dstStore.open(dstloc, false);

                Set<String> srcKeySet = hmsm.keySet();
                int numKeys = srcKeySet.size();
                log.info(numKeys + " keys to process...");
                int keyCnt = 0;
                int percent = 0;
                for (String key : srcKeySet) {
                    keyCnt++;
                    int newPercent = (int)(((double)keyCnt / (double)numKeys) * 100.0);
                    if (newPercent != percent) {
                        long totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
                        long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
                        log.info(newPercent + "% done..."
                                + " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)");
                        percent = newPercent;
                    }

                    // Every primitive entity that exists must have a subslot list, so look for keys
                    // of that form.
                    int firstSpace = key.indexOf(" ");
                    if (firstSpace <= 0) {
                        // This happens for the " " key used for internal metadata
                        continue;
                    }
                    if (key.charAt(firstSpace + 1) == ' ' && key.charAt(firstSpace + 2) == 'S') {
                        String entity = key.substring(0, firstSpace);
                        copyRecursively(srcStore, dstStore, new SLSRTWLocation(entity));
                    }
                }
                log.info("Done copying");
                dstStore.close();
                srcStore.close();
            }
            */

            /*
            // This here is used to take an existing Store and convert it to one that uses a
            // subslotTranslationTable.
            String srcloc = args[0];
            String dstloc = args[1];
            StringListStore srcStore = new StringListStore(new TCHStoreMap());
            srcStore.open(srcloc, true);
            srcStore.highLevelCopy(dstloc);
            srcStore.close();
            */
            
        } catch (Exception e) {
            log.fatal("Uncaught exception", e);
            System.exit(2);
        }
    }        
}