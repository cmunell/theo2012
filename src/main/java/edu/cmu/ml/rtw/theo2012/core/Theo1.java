package edu.cmu.ml.rtw.theo2012.core;

/**
 * Interface to which the lowest layers of Theo conform<p>
 *
 * There is no difference in method signature vs {@link Theo0}, but the semantics are slightly
 * different.  In particular a Theo0 implementation may return null rather than an {@link Entity}
 * (or subclass thereof) when such a thing does not actually exist in the KB.<p>
 *
 * A Theo1 implementation will never return null, meaning that is is permissible to construct Entity
 * objects (or subclasses thereof) that do not exist in the KB.  In other words, for Theo1, an
 * Entity object (or a subclass thereof) is effectively just a descriptor for something that could
 * possibly exist in the KB.
 */
public interface Theo1 extends Theo0 {
}
