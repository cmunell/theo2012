package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base class providing some facilities that RTWBag implementations may find useful
 *
 * This also provides a singleton RTWBag instance that represents an empty RTWBag.
 */
public class RTWBagBase {
    protected static class EmptyBag implements RTWBag {
        @Override public int hashCode() {
            return 6;
        }

        @Override public String toString() {
            return valueDump();
        }

        @Override public int getNumValues() {
            return 0;
        }

        @Override public String valueDump() {
            return "{}";
        }

        @Override public boolean isEmpty() {
            return true;
        }

        @Override public boolean has1Value() {
            return false;
        }

        @Override public boolean has1String() {
            return false;
        }

        @Override public boolean has1Integer() {
            return false;
        }

        @Override public boolean has1Double() {
            return false;
        }

        @Override public boolean has1Boolean() {
            return false;
        }

        @Override public boolean has1Entity() {
            return false;
        }

        @Override public Iterable<RTWValue> iter() {
            return new ScalarIterator<RTWValue>(null);
        }

        @Override public Iterable<RTWBooleanValue> booleanIter() {
            return new ScalarIterator<RTWBooleanValue>(null);
        }

        @Override public Iterable<RTWDoubleValue> doubleIter() {
            return new ScalarIterator<RTWDoubleValue>(null);
        }

        @Override public Iterable<RTWIntegerValue> integerIter() {
            return new ScalarIterator<RTWIntegerValue>(null);
        }

        @Override public Iterable<RTWStringValue> stringIter() {
            return new ScalarIterator<RTWStringValue>(null);
        }

        @Override public Iterable<Entity> entityIter() {
            return new ScalarIterator<Entity>(null);
        }

        @Override public RTWValue into1Value() {
            return null;
        }

        @Override public String into1String() {
            return null;
        }

        @Override public Integer into1Integer() {
            return null;
        }

        @Override public Double into1Double() {
            return null;
        } 

        @Override public Boolean into1Boolean() {
            return null;
        }

        @Override public Entity into1Entity() {
            return null;
        }

        @Override public RTWValue need1Value() {
            throw new RuntimeException("No values");
        }

        @Override public boolean need1Boolean() {
            throw new RuntimeException("No values");
        }

        @Override public double need1Double() {
            throw new RuntimeException("No values");
        }
        
        @Override public int need1Integer() {
            throw new RuntimeException("No values");
        }

        @Override public String need1String() {
            throw new RuntimeException("No values");
        }

        @Override public Entity need1Entity() {
            throw new RuntimeException("No values");
        }

        @Override public boolean containsValue(RTWValue v) {
            return false;
        }
    }

    /**
     * Abstract parent class for our two kinds of Iterators that implements Iterable for them
     *
     * The decisionmaking process for providing Iterator vs. Iterable comes down to avoiding
     * intermediate Iterable object.  We could alternatively make an RTWBag subclass that
     * itself is the Iterable, and have our KB getValue return that, but this is unable to escape an
     * extra intermediate object if the argument to getValue is already an RTWBag.  So this is
     * a little bit sketchy but it gives us something that's easy to use that you'd have to go out
     * of your way to misuse.
     */
    protected static abstract class IterableIterator<T> implements Iterator<T>, Iterable<T> {
        boolean iteratorReturned = false;

        public Iterator<T> iterator() {
            if (iteratorReturned)
                throw new RuntimeException("This object is actually an Iterator, not an Iterable; you're using it wrong");
            iteratorReturned = true;
            return this;
        }
    }

    /**
     * Iterator wrapper we use when returning typed iterators that catches type conversion errors
     * and reports the location, not just the value.
     *
     * We need to do this thing with dummyInstance and getAroundTypeErasure in order for a casting
     * exception to be caught here (and, importantly, rethrown with the bag name (e.g. KB location)
     * attached.  If we just do a blind cast like (T)v, then the casting exception will not get
     * caught here at all and will appear in the backtrace to have originated wherever next() was
     * invoked -- doubly bad for the confusion that inspires!
     */
    protected static class ConversionIterator<T> extends IterableIterator<T> {
        final Iterator<RTWValue> wrappedIterator;
        final Class<T> getAroundTypeErasure;

        protected ConversionIterator(final Iterator<RTWValue> it, final Class<T> dummyInstance) {
            wrappedIterator = it;
            getAroundTypeErasure = dummyInstance;
        }

        public boolean hasNext() {
            return wrappedIterator.hasNext();
        }

        public T next() {
            RTWValue v;
            try {
                v = wrappedIterator.next();
            } catch (Exception e) {
                throw new RuntimeException("From " + toString(), e);
            }
            try {
                return getAroundTypeErasure.cast(v);
            } catch (java.lang.ClassCastException e) {
                throw new RuntimeException("Element " + v + " in " + toString()
                        + " is expected to be " + getAroundTypeErasure.getSimpleName(), e);
            } catch (Exception e) {
                throw new RuntimeException("From element " + v + " in " + toString(), e);
            }            
        }

        public void remove() {
            wrappedIterator.remove();
        }
    }

    /**
     * Iterator we can return when we want to simulate iterating over a scalar or lack of a value
     */
    protected static class ScalarIterator<T> extends IterableIterator<T> {
        T value;

        /**
         * We accept RTWValue rather than T so that a null RTWValue can be passed in when there is a
         * lack of value.  This reduces complexity of the calling code.  A straight null may also be
         * used.
         */
        protected ScalarIterator(final RTWValue value) {
            if (value == null) this.value = null;
            else this.value = (T)value;
        }

        public boolean hasNext() {
            return value != null;
        }

        public T next() {
            try {
                if (value == null) throw new java.util.NoSuchElementException();
                T tmp = value;
                value = null;
                return tmp;
            } catch (Exception e) {
                throw new RuntimeException("From KB " + toString(),e);
            }            
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Public singleton empty RTWBag instance for all and sundry to enjoy
     *
     * One deficiency is that those methods that throw exceptions about the bag having no value have
     * no way to report where the bag came from in order to generate a more meaningful error
     * message.  We can imagine ways to extend this class in such a way as to allow users to regain
     * the full usefulness of those error messages.
     */
    public final static EmptyBag empty = new EmptyBag();
}