package edu.cmu.ml.rtw.theo2012.core;

import java.io.File;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Properties;

/**
 * Factory for obtaining instances of {@link Theo0}, and probably more as time goes on
 *
 * The ability to obtain a Theo0 instance from this factory is our first step toward being able to
 * deal with the realities of having multiple "layers" of Theo, multiple implementations of those
 * layers, multiple implementations of the underlying storage mechanisms used by those KBs, and
 * different and non-uniform ways to name the physical storage of those KBs (e.g. in a file, in a
 * directory, via a service).
 */
public class TheoFactory {
    /**
     * Our log
     */
    private final static Logger log = LogFactory.getLogger();

    /**
     * Given the name a KB, automatically identify the appropriate format being used, and return an
     * appropriate Theo0 implementation that may be used to access it.
     *
     * If create is set, then, if no KB by that name exists, this will attempt to create a new empty
     * KB with the given name.  A default storage format and Theo0 implementation will be used.  If
     * the defaultFomrat parameter is non-null, then this will be used to override the default
     * default.
     *
     * Obviously, we'll have to get cleverer about how to choose "appropriate" and "default" things
     * in the future.  This exists mainly to save callers from having to construct File objects and
     * to insulate them a possible switch to URI objects or something like that.  This String-based
     * method exists to centralize our standard behavior of how to interpret plain old Strings such
     * as are likely to be found on commandlines and in configuration files.
     *
     * FODO: this suggests the need for a "delete everything and go back to an empty KB" method.
     */
    public static Theo1 openTheo1(String filename, boolean readOnly, boolean create, String defaultFormat) {
        // For now, KB names are always filenames
        return openTheo1(new File(filename), readOnly, create, defaultFormat);
    }

    /**
     * Version of openTheo1 that uses a File to designate the KB
     *
     * We need support for both TCH and HM, but don't quite have time to do proper format detection
     * etc.  So our Q&D solution is to look for a defaultKBFormat parameter in the good ol' MBLEM
     * parameters and do whatever that says.  If the defaultFormat parameter is non-null, then it
     * will be used to override the default default.
     */
    public static Theo1 openTheo1(File file, boolean readOnly, boolean create, String defaultFormat) {
        // For now, we only use TCHSuperStore as our storage format, and only use
        // StoreInverselessTheo0 / PointerInversingTheo0 as our Theo0 implementation.  Later on,
        // we'll get clever and do automatic file format recognition and suchlike.
        
        String format = defaultFormat;
        if (format == null) {
            Properties properties = Properties.loadFromClassName("edu.cmu.ml.rtw.mbl.MBLExecutionManager");
            format = properties.getProperty("defaultKBFormat", "tch");
        }
        Theo1 theo1 = null;

        if (format.equals("tch")) {
            if (!create) {
                if (!file.exists())
                    throw new RuntimeException(file + " does not exist");
                if (!file.isFile())
                    throw new RuntimeException(file + " is not the wrong format");
                // else our Store will automatically create on open
            }

            /*  bkdb: reenable wwhen TCH support is added
            TCHStoreMap storeMap = TCHStoreMap();
            storeMap.setForceAlwaysDirty(false);
            SuperStore store = new StringListSuperStore<StringListStoreMap>(storeMap);
            
            // bkdb: do we want to automatically invoke Theo2012Converter here?  We should probably
            // at least detect a non-Theo KB here by finding a string generalization from slot to
            // entity or something like that.  We don't want to do that inside of PIT1 beacuse
            // Theo2012Converter applies PIT1 to a not-yet-Theo2012 KB.  Although, I suppose PIT1
            // could have a special open that Theo2012Converter uses that overlooks what it might
            // normally complain about.
            //
            // OTOH, this is really the locus for format detection, and it does make sense to leave
            // things like PIT1 more appliance-like and NELL-agnostic.
            
            PointerInversingTheo1 pITheo1 = new PointerInversingTheo1(new StoreInverselessTheo1(store));
            pITheo1.open(file.toString(), readOnly);
            theo1 = pITheo1;
            */
        } else if (format.equals("hm")) {
        	if (!file.exists() || !file.isFile()) {
        		if (!create) {
        			throw new RuntimeException(file + " does not exist");
        		}
        		// else our Store will automatically create on open
        	}

        	SuperStore store = new StringListSuperStore<StringListStoreMap>(new HashMapStoreMap());
        	PointerInversingTheo1 pITheo1 = new PointerInversingTheo1(new StoreInverselessTheo1(store));
            pITheo1.open(file.toString(), readOnly);
            theo1 = pITheo1;
        } else if (format.equals("n4j")) {
        	try {
        		if (!file.exists() || !file.getCanonicalFile().isDirectory()) {
        			if (!create) {
        				throw new RuntimeException(file + " does not exist");
        			}
        			// else our Store will automatically create on open
        		}
        	}
        	catch (java.io.IOException e) {
        		throw new RuntimeException(file + " cannot be converted to a directory for a Neo4J database");
        	}
                /* bkdb: reenable with N4J support is added 
        	N4JTheo0 theo0 = new N4JTheo0(file.toString(), readOnly, create=(!readOnly));
                theo1 = new MinimalTheo1(theo0);
                */
        } else if (format.equals("mdb")) {
            if (!file.exists() || !file.isDirectory()) {
                if (!create) {
                    throw new RuntimeException(file + " does not exist");
                }
                // else our Store will automatically create on open
            }

            MapDBStoreMap storeMap = new MapDBStoreMap();
            storeMap.setForceAlwaysDirty(false);
            SuperStore store = new StringListSuperStore<StringListStoreMap>(storeMap);
            
            // bkdb: do we want to automatically invoke Theo2012Converter here?  We should probably
            // at least detect a non-Theo KB here by finding a string generalization from slot to
            // entity or something like that.  We don't want to do that inside of PIT1 beacuse
            // Theo2012Converter applies PIT1 to a not-yet-Theo2012 KB.  Although, I suppose PIT1
            // could have a special open that Theo2012Converter uses that overlooks what it might
            // normally complain about.
            //
            // OTOH, this is really the locus for format detection, and it does make sense to leave
            // things like PIT1 more appliance-like and NELL-agnostic.
            
            PointerInversingTheo1 pITheo1 = new PointerInversingTheo1(new StoreInverselessTheo1(store));
            pITheo1.open(file.toString(), readOnly);
            theo1 = pITheo1;
        } else {
        	throw new RuntimeException("Unrecognized defaultKBFormat value " + format);
        }
        return theo1;
    }

}
    