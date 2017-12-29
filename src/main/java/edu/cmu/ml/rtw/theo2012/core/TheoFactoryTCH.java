package edu.cmu.ml.rtw.theo2012.core;

import java.io.File;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

import edu.cmu.ml.rtw.theo2012.tch.TCHStoreMap;

/**
 * Gimpy bridge to the theo2012-tch github repository
 *
 * In the future we should have some fancy reflection-based or CLASSPATH-based system or whatever to
 * automatically find implementations for additional formats that live in external repositories so
 * that unnecessary dependencies aren't automatically pulled in.
 */
public class TheoFactoryTCH {
    /**
     * Our log
     */
    private final static Logger log = LogFactory.getLogger();

    /**
     * Special-purpose way to open a Theo Layer 1 KB.<p>
     *
     * In general, it should not be useful to use this, and it could potentially result in some
     * manner of KB corruption if not used correctly.  It is offered simply as a way to return a
     * {@link Theo1} object should some low-level utility or application that specifically wants to
     * eschew the added functionalities of {@link Theo2}.
     */
    public static Theo1 openTheo1(String name, boolean readOnly, boolean create) {
        File file = new File(name);
        if (!create) {
            if (!file.exists())
                throw new RuntimeException(file + " does not exist");
            if (!file.isFile())
                throw new RuntimeException(file + " is not the wrong format");
            // else our Store will automatically create on open
        }

        TCHStoreMap storeMap = new TCHStoreMap();
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
        return pITheo1;
    }
}
