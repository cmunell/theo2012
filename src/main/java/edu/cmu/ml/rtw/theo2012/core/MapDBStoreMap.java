package edu.cmu.ml.rtw.theo2012.core;

// bkdb: OK, now what are those other two files?  Do we have to go back to a directory being a KB here?

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Properties;
import edu.cmu.ml.rtw.util.Timer;

/**
 * {@link StoreMap} implementation using MapDB as the underlying storage engine.
 *
 * The impetus for this class is to provide an alternative to {@link TCHStoreMap} that avoids the
 * stereotypically problematic dependence on installing the Java bindings for Tokyo Cabinet.
 *
 * {@link HashMapStoreMap} is not an attractive alternative because the speed and size
 * charactaristics turn out to be disappointing, and the need to always load the entire KB into RAM
 * tends to make minor operations on large KBs impractical.
 *
 * In order to ensure that modifications directly to the values in the map (i.e. without using
 * something like {@link put}) are not lost, this class must flush all cache entries to disk upon
 * being closed.  This may result in many spurious writes.  This behavior may be defeated through
 * the use of {@link setForceAlwaysDirty} when the caller is willing to gauarantee that
 * modifications will never be made except via {@link put}.
 *
 * FODO: Note that, as of this writing, the MapDB javadoc for its HTreeMap class, which is what
 * {@link MapDBStoreMap} uses, indicates that performance will degrade past about 1 billion records.
 * That's just above the size of the ongoing NELL KB at iteration 880, so we would want to explore
 * getting clever, for instance maybe by federating into multiple HTreeMaps, if this gets used for
 * NELL at scale.
 *
 * FODO: It's not clear how best to store out-of-band information, like an identifier that would
 * tell us whether or not an arbitrary file is MapDBStoreMap file, or a version number that would
 * tell us which MapDB options to set.  Perhaps we could rely on a system of catching and inspecting
 * exceptions thrown by MapDB when it tries to open something in the wrong format, and then use a
 * secondary map in the file to record any additional identifying information necessary?
 */
public class MapDBStoreMap implements StringListStoreMap {
    private final static Logger log = LogFactory.getLogger();

    /**
     * Our cache on top of MapDB
     *
     * MapDB requires that we only store immutable values in it, but StringListStore sometimes
     * mutates stored values for speed purposes.  Placing this cache between us and MapDB allows us
     * to maintain a cache of potentially-mutable/-mutated values for the benefit of
     * StringListStore, so that we make them immutable only upon furnishing them to MapDB.
     *
     * Whether or not it makes sense to use MapDB's cache along wtih this is as yet an open
     * question.  It could potentially save time by reducing the number of times we marshall values
     * out to UTF-8, and/or as an effective way to hold more the KB in RAM.
     */
    protected class RTWValCache extends StringListStoreMapCache {
        public RTWValCache(int cacheSize, boolean writeBack, boolean readOnly,
                boolean forceAlwaysDirty) {
            super(cacheSize, writeBack, readOnly, forceAlwaysDirty);
        }

        @Override protected RTWListValue get(String key) {
            return map.get(key);
        }

        @Override protected void put(String key, RTWListValue value, boolean mightMutate) {
            if (!(value instanceof RTWImmutableListValue))
                value = RTWImmutableListValue.copy((Collection<RTWValue>)value);
            map.put(key, value);
        }

        @Override protected void remove(String key) {
            map.remove(key);
        }
    }

    /**
     * Custom serializer for RTWListValue.  Important for speed and size.
     *
     * N.B. This has to be a static class because otherwise it will maintain a reference to the
     * MapDBStoreMap instance whence it was created, and then MapDB will attempt to serialize that
     * as well.
     */
    static class RTWListValueSerializer implements Serializer<RTWListValue>, Serializable {
        /**
         * Wrapper around KbUtility.RTWValueToUTF8 that prepends the length in bytes of what
         * RTWValueToUTF8 writes.
         *
         * Length is written using 1 or more bytes as if each byte were one digit in a base-128
         * number, starting with the least-significant byte and proceeding to the most-significant
         * (i.e. little endian).  If the most-significant bit is set, then that signals that there
         * is another byte yet to be read as a part of this length indicator.  Naturally, the MSB
         * does not count toward the value of that 7-bit value.
         *
         * In this way, the fairly common case of a value requiring fewer than 128 bytes to encode
         * requires only one byte as-is to describe its length.
         *
         * But, if, for example, the length of the value is 1000, then the first byte will be 232
         * (1000 % 128 + 128), the second byte will be 7, and there will be no 3rd byte because the
         * second byte is under 128.  A total of 1002 bytes will be written.
         */
        @Override public void serialize(DataOutput2 out, RTWListValue value) {
            try {
                final byte[] utf8 = value.toUTF8();

                int len = utf8.length;
                byte lsd;
                do {
                    lsd = (byte)(len % 128);
                    len = len / 128;
                    if (len > 0) lsd += 128;
                    out.write(lsd);
                } while (lsd < 0);   // Apparently, bytes are signed in Java....

                out.write(utf8);
            } catch (Exception e) {
                throw new RuntimeException("serialize(<out>, " + value + ")", e);
            }
        }
        
        @Override public RTWListValue deserialize(DataInput2 in, int available) {
            try {
                int len = 0;
                int lsd;
                int multiplier = 1;
                boolean more;

                // For whateve reason, in.read() was giving me -1 for bytes with the most significant
                // bit set.  And, for whatever reason, doing it this way works.
                final byte[] dumbbuffer = new byte[1];
                do {
                    try {
                        in.readFully(dumbbuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Error in the middle of a size indicator", e);
                    }
                    lsd = dumbbuffer[0];   // nb. values over 127 will be negative
                    if (lsd < 0) {
                        more = true;
                        lsd += 128;
                    } else {
                        more = false;
                    }
                    len = len + (lsd * multiplier);
                    multiplier = multiplier * 128;
                    if (multiplier == 0)
                        throw new RuntimeException("Multiplier overflow.  Improperly formated length indicator.");
                } while (more);

                final byte buffer[] = new byte[len];
                try {
                    in.readFully(buffer);
                } catch (Exception e) {
                    throw new RuntimeException("Error in the middle of reading of RTWValue", e);
                }
                RTWValue v = RTWValue.fromUTF8(buffer, 0, len);
                if (!(v instanceof RTWListValue))
                    throw new RuntimeException("Non-RTWListValue value " + v);
                return (RTWListValue)v;
            } catch (Exception e) {
                throw new RuntimeException("deserialize(<in>, " + available + ")", e);
            }
        }

        @Override public int fixedSize() {
            // i.e. serialized RTWListValues are not of a fixed size.
            return -1;
        }
    }
    
    /**
     * Our read-only-ness
     */
    protected boolean readOnly = false;

    /**
     * Our location
     */
    protected String location = null;

    /**
     * The MapDB database to which we're attached.
     */
    protected DB db;

    /**
     * The Map within {@link db} acting as our storage mechanism
     */
    protected HTreeMap<String, RTWListValue> map;

    /**
     * Cache on top of {@link map}
     */
    protected RTWValCache mapCache;

    // Parameter values for the construction of new RTWValCache instances
    int cacheSize;
    boolean writeBack;
    boolean forceAlwaysDirty;

    /**
     * Constructor
     */
    public MapDBStoreMap() {
        Properties properties = TheoFactory.getProperties();

        /**
         * We used to be write-through in order to make things easier in terms of coherency with
         * slotlistCache.  May as well keep write-through mode around in case we need it for
         * debugging or some unexpected need.
         */
        writeBack = properties.getPropertyBooleanValue("mapCacheWriteBack", true);

        /*
         * bkisiel 2012-02-27: Still haven't retuned (or explored a dedicated slotlist cache) the in
         * the wake of all the different kinds of changes we've had to this format since its
         * inception.  But some recent speed investigations revealed a failure to be able to cache
         * conditionedKnownNegatives while doing relation instance iteration on the ongoing run's
         * KB.  While it's questionable to want to do that vs. having a dedicated cKN cache, it
         * seems generally prudent for the time being to be able to cover that kind of a case.
         * Kicking the size up to 100k took care of it.  What I haven't checked is if this incurs
         * too much CPU overhead on smaller KBs; it does not on this one I suppose we could try to
         * set this based on the KB size; for the record, this testing is being done on a KB with
         * 205B records (and with perhaps far too few buckets at 131M!)
         */
        cacheSize = properties.getPropertyIntegerValue("mapCacheSize", 100000);

        forceAlwaysDirty = true;
    }

    @Override public void open(String location, boolean openInReadOnlyMode) {
        try {
            if (getLocation() != null)
                throw new RuntimeException("StoreMap is already open");
            this.readOnly = openInReadOnlyMode;
            this.location = location;

            // A location for a MapDBStoreMap is a directory because MapDB will store multiple files
            // based on the filename we give to it.
            File dir = new File(location);
            if (!dir.exists()) {
                dir.mkdirs();
            } else if (!dir.isDirectory()) {
                throw new RuntimeException("Existing KB at \"" + location
                        + "\" is not in MapDB format");
            }

            File f = new File(location + "/MapDBStoreMap");

            // At present these settings are a guess at would should work well.  Feel free to explore
            // alternatives as long as backward compatability is maintained.  We'll try relying on
            // MapDB's own internal cache.  It appears to have a number of potentialy interested
            // settings and modes.
            //
            // When not in write-back mode, we opt for a configuration that makes debugging easier.
            DBMaker.Maker maker = null;
            if (writeBack) {
                maker = DBMaker.fileDB(f).executorEnable().fileMmapEnable();
            } else {
                maker = DBMaker.fileDB(f).fileMmapEnable();
            }
            if (openInReadOnlyMode) maker = maker.readOnly();

            // If we want to wait for lock release in read/write mode, we can add
            // maker.fileLockWait().  I forget what TCH does here, but if we find defined behavior
            // here to be desirable, then we should probably add another flag or a timeout or
            // something.

            db = maker.make();

            // Here we could read configuration options or verify that this is a MapDBStoreMap file,
            // etc.  Left for future work, for now.
            if (db.exists("MapDBStoreMap")) {
                map = db.hashMap("MapDBStoreMap", Serializer.STRING, new RTWListValueSerializer()).open();
            } else {
                map = db.hashMap("MapDBStoreMap", Serializer.STRING, new RTWListValueSerializer()).create();
            }

            mapCache = new RTWValCache(cacheSize, writeBack, readOnly, forceAlwaysDirty);
        } catch (Exception e) {
            throw new RuntimeException("open(\"" + location + "\", " + openInReadOnlyMode + ")", e);
        }
    }

    @Override public void close() {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is already closed");
        flush(false);
        db.close();

        // If we were open read/write update the modification time on the disk location.  As of this
        // writing MapDB uses a directory, and happens to not update the modification time on the
        // directory, which confuses our current simple approaches to tracking checkpoints and all
        // that.  Even if/when we offer a proper API for that sort of thing, it's still convenient
        // to have the on-disk storage make sense at a casual glance.
        File f = new File(location);
        f.setLastModified(System.currentTimeMillis());

        // And reset internal state
        location = null;
        map = null;
        db = null;
        mapCache = null;
        readOnly = false;
    }

    @Override public String getLocation() {
        return location;
    }

    @Override public boolean isReadOnly() {
        return readOnly;
    }

    @Override public void flush(boolean sync) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (!readOnly) log.info("Flushing MapDB cache...");
        mapCache.commitAllDirty(true);
        db.commit();
        if (!readOnly) log.info("Done flushing.");
    }

    @Override public synchronized void copy(String newLocation) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");

        // Doesn't look like MapDB offers a way to do this.  I'm going to take the really lazy way
        // out here.
        File f = new File(newLocation);
        if (f.exists()) f.delete();
        f = null;
        mapCache.commitAllDirty(true);
        MapDBStoreMap dst = new MapDBStoreMap();
        dst.open(newLocation, false);
        dst.setCacheSize(0);

        map.forEach(new BiConsumer<String, RTWListValue>() {
                    public void accept(String k, RTWListValue v) {
                        dst.put(k, v);
                    }
                });
        dst.close();
    }

    @Override public void giveLargeAccessHint() {
        // FODO: Doesn't look like MapDB offers somethihng here, but we could try just reading
        // through the file or something.
    }

    @Override public void logStats() {
        // Add something useful here as needed
    }

    @Override public synchronized void optimize() {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        mapCache.commitAllDirty(true);
        // TODO: 3.0.4 doesn't support this yet: db.compact();
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int size) {
        cacheSize = size;
        if (mapCache != null) mapCache.resize(size);
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
    public void setForceAlwaysDirty(boolean forceAlwaysDirty) {
        this.forceAlwaysDirty = forceAlwaysDirty;
        if (mapCache != null) mapCache.setForceAlwaysDirty(forceAlwaysDirty);
    }

    ////////////////////////////////////////////////////////////////////////////
    // And then we can almost uniformly just forward the methods for java.util.Map to our map
    // variable.
    //
    // FODO: Some of these could be made more efficient by making StringListStoreMapCache a full
    // java.util.Map object (or giving it methods to support the same).  That is left as future work
    // because StringListStore doesn't need these.
    ///////////////////////////////////////////////////////////////////////////

    @Override public void clear() {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        mapCache.clear();
        map.clear();
    }

    @Override public synchronized boolean containsKey(Object key) {
        mapCache.commitAllDirty(true);
        return map.containsKey(key);
    }

    @Override public synchronized boolean containsValue(Object value) {
        mapCache.commitAllDirty(true);
        return map.containsValue(value);
    }

    @Override public Set<Map.Entry<String, RTWListValue>> entrySet() {
        // Not needed for StringListStore, and not worth the effort to enforce read-only-ness.
        throw new RuntimeException("Not implemented");
    }

    @Override public RTWListValue get(Object key) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (key instanceof String) return mapCache.getValue((String)key);
        return null;
    }

    @Override public synchronized boolean isEmpty() {
        mapCache.commitAllDirty(true);
        return map.isEmpty();
    }

    @Override public synchronized Set<String> keySet() {
        mapCache.commitAllDirty(true);
        return map.keySet();
    }

    @Override public void putAll(Map<? extends String, ? extends RTWListValue> m) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        for (String key : m.keySet())
            put(key, m.get(key));
    }

    @Override public synchronized int size() {
        mapCache.commitAllDirty(true);
        return map.size();
    }

    @Override public Collection<RTWListValue> values() {
        // Not needed for StringListStore, and not worth the effort to enforce read-only-ness.
        throw new RuntimeException("Not implemented");
    }

    @Override public RTWListValue put(String key, RTWListValue value) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        return mapCache.putValue(key, value);
    }

    @Override public RTWListValue remove(Object key) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        if (key instanceof String) return mapCache.removeValue((String)key);
        else return null;
    }

    /**
     * Main method provides some very primitive commandline access for debugging
     *
     * Something like this could / should live in some kind of StoreMap base class or something like
     * that, but, as usual, we need the quick and dirty option at the moment.
     */
    public static void main(String args[]) {
        try {
            String location = args[0];
            String cmd = args[1];
        
            MapDBStoreMap store = new MapDBStoreMap();
            store.open(location, true);

            if (cmd.equals("list")) {
                store.setCacheSize(0);
                for (String key : store.keySet()) {
                    System.out.println(key);
                }
            } else if (cmd.equals("listpv")) {
                store.setCacheSize(0);
                for (String key : store.keySet()) {
                    System.out.println(key + "\t" + store.get(key));
                }
            } else {
                throw new RuntimeException("Unrecognized command \"" + cmd + "\"");
            }

            store.close();
        } catch (Exception e) {
            log.fatal("Uncaught exception", e);
            System.exit(2);
        }
    }
}
