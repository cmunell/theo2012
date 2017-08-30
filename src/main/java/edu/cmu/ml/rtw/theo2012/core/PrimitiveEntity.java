package edu.cmu.ml.rtw.theo2012.core;

/**
 * A Theo Entity thatprimtive entity<p>
 *
 * bkdb TODO: define equals for String (esp. in re Slot), or would that be too condusive to type
 * errors?<p>
 */
public interface PrimitiveEntity extends Entity {
    /**
     * Return the String name of this primitive entity
     */
    public String getName();
}