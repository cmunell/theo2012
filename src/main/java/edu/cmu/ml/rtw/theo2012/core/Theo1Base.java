package edu.cmu.ml.rtw.theo2012.core;

import java.util.Collection;
import java.util.Iterator;

/**
 * Abstract base class for {@link Theo1} implementations that implements all of the convenience and
 * library-style methods in terms of a small set of primitives.<p>
 *
 * See discussion in Theo1.java's class-level comments for more.<p>
 *
 * Note that the implementations we inherit from {@link Theo0Base} are inherently suitable for Theo1
 * as well because the only material difference is that they must account for the possibility of
 * Theo0 returning null where Theo1 is barred from doing so.<p>
 */
public abstract class Theo1Base extends Theo0Base implements Theo1 {

    // bk:api: want to make this just create so as to keep it shorter and more consistent with get?
    //
    // Not necessarily the most efficient, but a workable default
    @Override public PrimitiveEntity createPrimitiveEntity(String name, Entity generalization) { 
        try { 
            PrimitiveEntity e = get(name); 
            e.addValue("generalizations", generalization); 
            return e; 
        } catch (Exception e) { 
            throw new RuntimeException("createPrimitiveEntity(\"" + name + "\", " + generalization 
                    + ")", e); 
        } 
    } 
}