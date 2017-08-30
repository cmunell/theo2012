package edu.cmu.ml.rtw.theo2012.core;

/**
 * Interface for a Theo implementation featuring the usual basic Theo facilities generally assumed
 * to be desirable and non-optional except in special cases<p>
 *
 * There is no difference in method signature vs {@link Theo0}, but the semantics are slightly
 * different.  In particular a Theo2 implementation introduces facilities like slot metadata such as
 * domain and range, and other such things as the designers might come to regard as being
 * non-optional for any interesting use of Theo.  Thorough explanations are found at this time in
 * the Theo2012 white paper.<p>
 */
public interface Theo2 extends Theo1 {
}
