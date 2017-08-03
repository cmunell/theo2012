package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

/**
 * Single abstract base class for {@link RTWLocation} implementations to use, assuming that they
 * wish to implement their {@link RTWBag} methods with a {@link SimpleBag}
 *
 * In a nutshell, this class is a way to skirt the limitation imposed by Java of extending at most
 * one class.  The dichotomy we face is that an RTWLocation is fundamentally the merger of two
 * ideas: a location in the KB (i.e. {@link RTWLocation}, and a proxy for the values at that
 * location (i.e. {@link RTWBag}).  This leads to a situation where we commonly would want to reuse
 * some boilerplate for the location-ness, and other boilerplate for the bag-ness, which
 * single-inheritence makes impossible (or at best so laborious that one almost may as well just
 * copy and paste the boilerplate).
 *
 * To build an RTWLocation implementation based on this class, one need only implement the getValues
 * and getNumValues methods -- the same two that SimpleBag requires -- and the result will combine
 * the location-ness boilerplate from {@link RTWLocationBase} with the bag-ness boilerplate from
 * {@link SimpleBag}.
 *
 * This class also provides the convenience of wrapping the RTWBag methods in additional exception
 * handling so as to attach the location to the error messages.  This is critically useful for
 * debugging things via the stack traces in exception messages.
 */
public abstract class SimpleLocation extends RTWLocationBase implements RTWLocation {
    private final static Logger log = LogFactory.getLogger();

    /**
     * RTWBag implementation that we use to implement our RTWBag methods.
     *
     * This pulls in the boilerplate from SimpleBag.  SimpleBag requires that only two methods be
     * implemented: getValues and getNumValues.  We simply forward these to our abstract
     * SimpleLocation.getValues and SimpleLocation.getNumValues methods that are left for subclasses
     * to implement.
     */
    protected class OurBag extends SimpleBag implements RTWBag {
        @Override protected Iterable<RTWValue> getValues() {
            return SimpleLocation.this.getValues();
        }

        @Override public int getNumValues() {
            return SimpleLocation.this.getNumValues();
        }
    }

    protected OurBag ourBag = new OurBag();

    /**
     * Subclasses should override this
     *
     * Semantics are the same as {@link SimpleBag.getValues}
     */
    protected abstract Iterable<RTWValue> getValues();

    /**
     * Subclasses should override this
     *
     * Semantics are the same as {@link SimpleBag.getNumValues}
     */
    @Override public abstract int getNumValues();
     
    // Pull in standard RTWLocation constructors
    public SimpleLocation(Object... list) {
        super(list);
    }

    // Pull in standard RTWLocation constructors
    public SimpleLocation(List<String> list) {
        super(list);
    }

    // Pull in standard RTWLocation constructors
    public SimpleLocation(String[] list) {
        super(list);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Now we can provide concrete implementations of the RTWBag methods by forwarding them to our
    // ourBag member
    ////////////////////////////////////////////////////////////////////////////

    @Override public String valueDump() {
        try {
            return ourBag.valueDump();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean isEmpty() {
        try {
            return ourBag.isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean has1Value() {
        try {
            return ourBag.has1Value();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean has1String() {
        try {
            return ourBag.has1String();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean has1Integer() {
        try {
            return ourBag.has1Integer();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean has1Double() {
        try {
            return ourBag.has1Double();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean has1Boolean() {
        try {
            return ourBag.has1Boolean();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean has1Entity() {
        try {
            return ourBag.has1Entity();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Iterable<RTWValue> iter() {
        try {
            return ourBag.iter();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Iterable<RTWBooleanValue> booleanIter() {
        try {
            return ourBag.booleanIter();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Iterable<RTWDoubleValue> doubleIter() {
        try {
            return ourBag.doubleIter();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Iterable<RTWIntegerValue> integerIter() {
        try {
            return ourBag.integerIter();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Iterable<RTWStringValue> stringIter() {
        try {
            return ourBag.stringIter();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Iterable<Entity> entityIter() {
        try {
            return ourBag.entityIter();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public RTWValue into1Value() {
        try {
            return ourBag.into1Value();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public String into1String() {
        try {
            return ourBag.into1String();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Integer into1Integer() {
        try {
            return ourBag.into1Integer();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Double into1Double() {
        try {
            return ourBag.into1Double();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Boolean into1Boolean() {
        try {
            return ourBag.into1Boolean();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Entity into1Entity() {
        try {
            return ourBag.into1Entity();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public RTWValue need1Value() {
        try {
            return ourBag.need1Value();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean need1Boolean() {
        try {
            return ourBag.need1Boolean();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public double need1Double() {
        try {
            return ourBag.need1Double();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public int need1Integer() {
        try {
            return ourBag.need1Integer();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public String need1String() {
        try {
            return ourBag.need1String();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public Entity need1Entity() {
        try {
            return ourBag.need1Entity();
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }

    @Override public boolean containsValue(RTWValue v) {
        try {
            return ourBag.containsValue(v);
        } catch (Exception e) {
            throw new RuntimeException("At location " + this, e);
        }
    }
}
