package edu.cmu.ml.rtw.theo2012.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Properties;

/**
 * Factory for obtaining instances of {@link Theo0}, and probably more as time goes on<p>
 *
 * When it comes to Theo, we face the reality that there are multiple layers to choose from
 * (potentially mixing and matching them in the future), different options for underlying storage
 * format, and other such options.  Some applications will have specific requirements, or may want
 * to adhere to user specification, and others may want to open an existing Theo KB without caring
 * about anything beyond matching the storage format and semantics that it has adhered to.<p>
 *
 * At present, the world of Theo and the ability to make things as easy as would be ideal is still
 * nacent.  Theo Layer 2 is de facto the most basic general-purpose layer, so we offer that as the
 * default simple option for general-purpose applications of today with the expectation that it will
 * continue to "just work" in the future as we refine and implement more complex systems for a more
 * mature world.<p>
 */
public class TheoFactory {
    /**
     * Our log
     */
    private final static Logger log = LogFactory.getLogger();

    /**
     * Properties from the most recent attempt to load them, or null
     */
    protected static Properties properties;

    /**
     * Suggested general-purpose way to open a KB meant to continue to "just work" in the future.<p>
     *
     * If create is set, then, if no KB by that name exists, this will attempt to create a new empty
     * KB with the given name.  A default storage format and Theo2 implementation will be used.
     * Note that the File object specified might be a file or a directory.<p>
     *
     * In the future, this should be expected to be able to auto-detect the format and necessary
     * layers of Theo to use when opening an existing KB.  This is not fully implemented at present,
     * but applications that only use this method should "just work" anyway.<p>
     *
     * This will throw an exception if the operation cannot be completed.
     */
    public static Theo2 open(File file, boolean readOnly, boolean create) {
        // For now we have general-purpose applications always use a MapDB-based Theo2 because it
        // seems so far to be a good general purpose, well-tested all-Java option.
        List<String> options = new ArrayList<String>();
        options.add("--kbformat=mdb");
        return open(file.toString(), readOnly, create, options);
    }

    /**
     * More flexible general-purpose way to open a KB.<p>
     *
     * Use of this method is not recommended in most cases, as its specification may change in the
     * future as Theo2012 matures.<p>
     *
     * @param name for now must name a file or directory, but in the future will probably be a URI
     * or some such thing.  Use of file names that look like URIs may produce broken or unexpected
     * behavior in the future<p>
     *
     * @param options is meant to accept an optional list of Strings to control particular features
     * like choice of storage format or layer options such as could be read directly off of a
     * command line.  This provides a way for commandline-oriented tools to offer an easy way to
     * support future such options without having to know how to parse them as the set of options is
     * expanded and changed.  It can be expected that in the future the URI, a Properties object,
     * and automatic use of one or more configuration files may augment this system.<p>
     *
     * This will throw an exception if the operation cannot be completed.
     */
    public static Theo2 open(String name, boolean readOnly, boolean create, List<String> options) {
        // At present, everything goes through BasicTheo2 and there's no automatic level detection
        // or anything, so openTheo1 serves as the meat of this implementation.
        return new BasicTheo2(openTheo1(name, readOnly, create, options));
    }

    /**
     * Special-purpose way to open a Theo Layer 1 KB.<p>
     *
     * In general, it should not be useful to use this, and it could potentially result in some
     * manner of KB corruption if not used correctly.  It is offered simply as a way to return a
     * {@link Theo1} object should some low-level utility or application that specifically wants to
     * eschew the added functionalities of {@link Theo2}.
     */
    public static Theo1 openTheo1(String name, boolean readOnly, boolean create, List<String> options) {
        properties = getProperties(options);
        Theo1 theo1 = null;
        File file = new File(name);

        // No auto-detection yet.  If this manages to still be unset, continue doctrine of
        // defaulting to mdb and call it the fault of the application or end user if he decided to
        // do something to violate the current faux-automatic stuff.
        String format = properties.getProperty("defaultKBFormat", "mdb");

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

    /**
     * Standardized way to obtain a Properties object containing the correct set of properties given
     * whatever combination of ways to specify them is used, reloading them and replacing the
     * properties on record.
     *
     * This is our current attempt to hide the transitional uncertainty of the situation, which
     * includes a lot of transitional ad hoc patches in order to ease simultaneous development of
     * Theo2012 and other projects (including but not limited to NELL) until maturity and unity
     * emerges.
     */
    protected static Properties getProperties(List<String> options) {
        // For transitional ease, start with the current standard NELL properties (if available).
        Properties properties =
                Properties.loadFromClassName("edu.cmu.ml.rtw.mbl.MBLExecutionManager", null, false);

        // Set any hardcoded defaults.
        // There are none

        // Parse the optional options parameter in, thereby treating them as a final override
        properties.load(toProperties(options));

        return properties;
    }

    /**
     * Return currently-loaded properties or supply some default properties if none have yet been
     * loaded
     *
     * This is made available for use by all classes in the package so that there is a single code
     * path for obtaining properties until such time as we might decide to do something more
     * sophisticated.
     */
    protected static Properties getProperties() {
        if (properties == null) properties = getProperties(Collections.EMPTY_LIST);
        return properties;
    }

    /**
     * Standard way to convert one of the List<String> options parameters into a Properties object
     *
     * So in other words this is basically a quick and dirty command line parser so that we needn't
     * add an additional dependency on a 3rd-party command line parsing library.
     *
     * This will throw an exception if it encounters something it doesn't recognize
     */
    protected static Properties toProperties(List<String> options) {
        Properties properties = new Properties();
        if (options == null) return properties;
        for (String s : options) {
            if (s.startsWith("--kbformat=")) {
                properties.setProperty("defaultKBFormat", s.substring(11));
            } else {
                throw new RuntimeException("Unrecognized option \"" + s + "\"");
            }
        }
        return properties;
    }
}
    