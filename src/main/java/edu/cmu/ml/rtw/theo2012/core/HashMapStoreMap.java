package edu.cmu.ml.rtw.theo2012.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Timer;

/**
 * {@link StoreMap} implementation based on java.util.HashMap providing a wholly-in-RAM storage
 * facility along with a way to save and load the content to a file.
 *
 * This provides a smaller and faster alternative to coupling a disk-backed Store with a RAM drive,
 * as is often highly-desirable to do for speed purposes.  It may also be handy for quick small
 * chores, and, to that end, features a mode geared toward using this as a "temporary" store that
 * neither loads nor saves any of the content to a file.  To engage this mode, specify "/" as the
 * location of the KB (this was chosen instead of something like null or an empty string so as not
 * accidently set off any surrounding error-checking code that might be looking for what it
 * considers to be a "legitimate" String.)
 *
 * Because there is no caching, this ought to be threadsafe in read-only mode without us having to
 * do anything in particular.
 *
 * Greater use of this class ought to tell us more about the limitations in StringListStoreBase's
 * implementation as well as inherent list-based design.  Having the map be keyed on RTWLocation
 * would be an obvious alternative for consideration.  But doing this was implementationally
 * expedient given certain immediate needs.
 *
 * FODO: Use FileOutputStream etc. or similar to apply read and write locks to the file in order to
 * reduce the potential for concurrent HashMapStoreMap instances / processes to step on each-other's
 * toes.
 */
public class HashMapStoreMap extends HashMap<String, RTWListValue> implements StringListStoreMap {
    private final static Logger log = LogFactory.getLogger();

    /**
     * Magic bytes used to identify our on-disk representation
     *
     * To form the file header, this sequence of bytes is followed by:
     *
     * A byte integer indicating the format number (starting from 0).
     *
     * A byte integer indicating the revision number of that format number (starting from 0).
     *
     * 25 bytes of format-dependent meaning (which also serves to start the real content on
     * something vaugely sensible like a 64-byte boundary unless a particular format defines some
     * other starting point).
     */
    protected final static byte[] magicBytes = {'e', 'd', 'u', '.', 'c', 'm', 'u', '.',
                                                'm', 'l', '.', 'r', 't', 'w', '.', 'm',
                                                'a', 'g', 'i', 'c', '.', 'H', 'a', 's',
                                                'h', 'M', 'a', 'p', 'S', 't', 'o', 'r',
                                                'e', 'M', 'a', 'p', 0};

    /**
     * Our location
     */
    protected String location = null;

    /**
     * Our read-only-ness
     */
    protected boolean readOnly = false;

    /**
     * Wrapper around KbUtility.RTWValueToUTF8 that prepends the length in bytes of what
     * RTWValueToUTF8 writes.
     *
     * Length is written using 1 or more bytes as if each byte were one digit in a base-128 number,
     * starting with the least-significant byte and proceeding to the most-significant (i.e. little
     * endian).  If the most-significant bit is set, then that signals that there is another byte
     * yet to be read as a part of this length indicator.  Naturally, the MSB does not count toward
     * the value of that 7-bit value.
     *
     * In this way, the fairly common case of a value requiring fewer than 128 bytes to encode
     * requires only one byte as-is to describe its length.
     *
     * But, if, for example, the length of the value is 1000, then the first byte will be 232 (1000
     * % 128 + 128), the second byte will be 7, and there will be no 3rd byte because the second
     * byte is under 128.  A total of 1002 bytes will be written.
     */
    protected void writeUTF8WithLen(OutputStream out, RTWValue value) {
        try {
            byte[] utf8 = value.toUTF8();

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
            throw new RuntimeException("writeUTF8WithLen(<out>, " + value + ")", e);
        }
    }

    /**
     * Opposite of writeUTF8WithLen
     */
    protected RTWValue readUTF8WithLen(InputStream in) {
        try {
            int len = 0;
            int lsd;
            int multiplier = 1;
            boolean more;

            // For whateve reason, in.read() was giving me -1 for bytes with the most significant
            // bit set.  And, for whatever reason, doing it this way works.
            byte[] dumbbuffer = new byte[1];
            do {
                int foo = in.read(dumbbuffer);
                if (foo < 1)
                    throw new RuntimeException("EOF in the middle of a size indicator");
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

            byte buffer[] = new byte[len];
            int read = in.read(buffer, 0, len);
            if (read < len)
                throw new RuntimeException("Too few bytes read in middle of RTWValue");
            return RTWValue.fromUTF8(buffer, 0, len);
        } catch (Exception e) {
            throw new RuntimeException("readUTF8WithLen(<in>)", e);
        }
    }

    /**
     * Write our entire content to a file in format "0"
     *
     * Format "0" is a quick & dirty format where we use KbUtility's UTF-8 methods' to just spit
     * every (key, value) pair out to a big sequential file.  Notably, there is no ordering or
     * seekability in this format.  We forego the opportunity to use compression (e.g. gzip) at
     * this time.
     *
     * For ease, everything we write is done through an RTWValue so that we can use
     * KbUtility.RTWValueToUTF8.  But because RTWValueToUTF8 does not encode the length of the UTF8
     * being written, we forward all of these writes through writeUTF8WithLen, which prepends the
     * length of each RTWValue written.  This simplifies reading.
     *
     * The very first bit of data is a n RTWIntegerValue encoded using KbUtility.RTWValueToUTF8
     * indicating the total number of entries.  This gives us a way to be able to know when we're
     * done reading it back in without requiring that we hit EOF at the end of our content, which
     * can be useful if our content happens to be sitting inside of somebody else's content.
     *
     * After that, we alternate between an RTWStringValue key and its RTWListValue value.
     */
    protected void writeFormat0(OutputStream out) {
        try {
            log.debug("Saving HashMapStoreMap...");

            // Magic bytes
            out.write(magicBytes);

            // Major and minor format numbers
            out.write(0);
            out.write(0);

            // 25 bytes of nothingness
            for (int i = 0; i < 25; i++) out.write(0);

            // Total number of entries
            int numEntries = size();
            writeUTF8WithLen(out, new RTWIntegerValue(numEntries));

            // The entries
            Timer t = new Timer();
            t.start();
            boolean didLogInfo = false;
            int savedEntries = 0;
            for (Entry<String, RTWListValue> entry : entrySet()) {
                writeUTF8WithLen(out, new RTWStringValue(entry.getKey()));
                writeUTF8WithLen(out, entry.getValue());
                savedEntries++;
                if (t.getElapsedSeconds() > 10) {
                    long totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
                    long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
                    int percent = (int)(((double)savedEntries / (double)numEntries) * 100.0);
                    log.info("Saved " + percent + "%..."
                            + " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)");
                    didLogInfo = true;
                    t.start();
                }
            }
            if (didLogInfo) log.info("Finished saving.");
            else log.debug("Finished saving.");
        } catch (Exception e) {
            throw new RuntimeException("writeFormat0(<out>) with location " + location, e);
        }
    }

    /**
     * Read a file in format "0" and put all entries into ourself, regardless of any pre-existing
     * content
     *
     * For now, this handles validation of magic bytes etc.  Later, that will go out into a
     * format-identification method.
     */
    protected void readFormat0(InputStream in) {
        try {
            byte magic[] = new byte[magicBytes.length];
            int read = in.read(magic, 0, magic.length);
            if (read != magic.length)
                throw new RuntimeException("Incorrect format (read only " + read + " of "
                        + magic.length + " magic bytes");
            if (!Arrays.equals(magic, magicBytes))
                throw new RuntimeException("Incorrect format (wrong magic bytes)");

            int majorFormat = in.read();
            int minorFormat = in.read();
            if (majorFormat < 0 || minorFormat < 0)
                throw new RuntimeException("Incorrect format (header missing format numbers");
            if (majorFormat != 0)
                throw new RuntimeException("This method can only read format 0; the data is in format " + majorFormat);
            if (minorFormat > 0)
                throw new RuntimeException("Format too new");

            for (int i = 0; i < 25; i++)
                if (in.read() < 0)
                    throw new RuntimeException("Premature EOF inside header");
            
            RTWValue v;
            v = readUTF8WithLen(in);
            if (!(v instanceof RTWIntegerValue))
                throw new RuntimeException("Incorrect format (entry count is " + v
                        + " instead of an integer");
            int numEntries = v.asInteger();

            // The entries
            Timer t = new Timer();
            t.start();
            boolean didLogInfo = false;
            for (int i = 0; i < numEntries; i++) {
                try {
                    v = readUTF8WithLen(in);
                    if (!(v instanceof RTWStringValue))
                        throw new RuntimeException("Incorrect format (non-string key " + v + ")");
                    String key = v.asString();
                    v = readUTF8WithLen(in);
                    if (!(v instanceof RTWListValue))
                        throw new RuntimeException("Incorrect format (non-list value " + v + ")");

                    // Bypass our own put so that we don't need to play games with read-only etc.
                    super.put(key, (RTWListValue)v);

                    if (t.getElapsedSeconds() > 10) {
                        long totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
                        long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
                        int percent = (int)(((double)i / (double)numEntries) * 100.0);
                        log.info("Loaded " + percent + "%... (" + i + " of " + numEntries
                            + " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)");
                        didLogInfo = true;
                        t.start();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("For entry " + i, e);
                }
            }
            if (didLogInfo) log.info("Finished loading.");
            else log.debug("Finished loading.");
            long totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
            long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
            log.info("Before GC"
                    + " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)");
            System.gc();
            totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
            usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
            log.info("After GC"
                    + " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory + "MB)");
        } catch (Exception e) {
            throw new RuntimeException("readFormat0(<in>) with location " + location, e);
        }
    }

    /**
     * Save our content to the given location, or our location if it is null
     */
    protected void save(String location, boolean sync) {
        try {
            if (location == null) location = this.location;

            // Skip this when RAM-only
            if (location.equals("/")) return;
        
            // bkdb: may as well just hold this open normally?
            // bkdb: sync?
            writeFormat0(new FileOutputStream(location));
        } catch (Exception e) {
            throw new RuntimeException("save(" + location + ", " + sync + ")", e);
        }
    }

    /**
     * Opposite of save
     */
    protected void load(String location) {
        try {
            readFormat0(new BufferedInputStream(new FileInputStream(location)));
        } catch (Exception e) {
            throw new RuntimeException("load(" + location + ")", e);
        }
    }

    @Override public void open(String location, boolean openInReadOnlyMode) {
        if (getLocation() != null)
            throw new RuntimeException("StoreMap is already open");

        this.readOnly = openInReadOnlyMode;
        this.location = location;

        // Special case for not-backed-by-a-file mode
        if (location.equals("/")) {
            // Nothing we need to do, I guess.
        }

        // Read in content from filename to pre-populate db
        else {
            // FODO: auto-forward things through async gz (can we do gzip file format detection
            // easily, rather than use .gz?)
            File f = new File(location);
            if (f.exists()) {
                load(location);
            }
        }
    }

    @Override public void close() {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is already closed");
        flush(false);
        location = null;
        readOnly = false;
        super.clear();  // our clear enforces openness, read-writeness, etc.
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
        if (!isReadOnly())
            save(null, sync);
    }

    @Override public void copy(String location) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        save(location, false);
    }

    @Override public void giveLargeAccessHint() {
        // Nothing to do here
    }

    @Override public void logStats() {
        // Add something useful here as needed
    }

    @Override public void optimize() {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");

        // I guess...  Could also be a no-op.
        save(null, false);
    }

    @Override public void clear() {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        super.clear();
    }

    @Override public RTWListValue put(String key, RTWListValue value) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        return super.put(key, value);
    }

    @Override public RTWListValue remove(Object key) {
        if (getLocation() == null)
            throw new RuntimeException("StoreMap is not open");
        if (isReadOnly())
            throw new RuntimeException("StoreMap is open read-only");
        return super.remove(key);
    }

    // We could override the rest and add additional checks, but this is probably good enough.
}
