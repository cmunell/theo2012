package edu.cmu.ml.rtw.theo2012.core;

/**
 * Special case of {@link StoreMap} that is easier to implement and while still providing the
 * capabilities needed by {@link StringListStore}
 *
 * Implementations of interest will typically be disk-backed, or at least be able save and load to
 * and from disk.  As such, they are faced with the problem of how to turn their arbitrary key and
 * value types into sensible on-disk representations.  By fixing the types to things that we know
 * we'll need, implementations can concentrate on using representations that work well for those
 * types that we'll actually be using, and to set aside the more general question that does not need
 * answering.
 */
public interface StringListStoreMap extends StoreMap<String, RTWListValue> {
}
