package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

/**
 * Transitional {@link RTWLocation} implementation that exists as part of the transition of NELL to
 * Theo2012
 *
 * Don't use this class.
 *
 * bkdb FODO: For expediency, Theo2012 code for now will use this class (directly, rather than
 * through KbManipulation.getLoc) as a standin for a truely "whiteboard" RTWLocation that has no
 * connection to any underlying KB.  This is largely safe for the purposes of Theo2012 code because
 * there will be no static KB opened via KbManipulation, meaning that any attempt to use a
 * KbMLocation as other than an abstract representation of a location will result in an exception
 * being thrown from KbManipultion.  We'll come back to this issue later on when we're at a point to
 * decide the ultiamte fate of RTWLocation, and what role, if any, it will play in Theo code of the
 * future.
 */
public class AbstractRTWLocation extends RTWLocationBase implements RTWLocation {
    private final static Logger log = LogFactory.getLogger();

    protected AbstractRTWLocation newRTWLocation(Object[] list) {
        return new AbstractRTWLocation(list);
    }

    // Pull in standard RTWLocation constructors
    public AbstractRTWLocation(Object... list) {
        super(list);
    }

    // Pull in standard RTWLocation constructors
    public AbstractRTWLocation(List<String> list) {
        super(list);
    }

    // Pull in standard RTWLocation constructors
    public AbstractRTWLocation(String[] list) {
        super(list);
    }
    
    /* bkdb: need this?  Seemingly not.  Delete if nothing goes awry during the work we'll do on kb3 before moving back to kb4.
    ////////////////////////////////////////////////////////////////////////////
    // Override a few RTWLocation methods to return AbstractRTWLocation for the sake of legacy code that
    // expects this.  The casting is nasty, but so are the alternatives: either merge this class
    // with RTWLocationBase or change all the legacy code such that RTWLocation is the interface and
    // it constructs RTWLegacyLocation objects or something like that.  The casting should always
    // work because new RTWLocationImplementatio instances created by RTWLocationBase are
    // constructed through our newRTWLocation member, which always returns RWTLocations.
    ////////////////////////////////////////////////////////////////////////////

    @Override public AbstractRTWLocation append(Object... list) {
        return (RTWLocation)super.append(list);
    }

    @Override public RTWLocation subslot(String slot) {
        return (RTWLocation)super.subslot(slot);
    }

    @Override public RTWLocation element(RTWElementRef e) {
        return (RTWLocation)super.element(e);
    }

    @Override public RTWLocation element(RTWValue v) {
        return (RTWLocation)super.element(v);
    }

    @Override public RTWLocation parent() {
        return (RTWLocation)super.parent();
    }

    @Override public RTWLocation firstN(int n) {
        return (RTWLocation)super.firstN(n);
    }

    @Override public RTWLocation siblingSlot(String slot) {
        return (RTWLocation)super.siblingSlot(slot);
    }
    */

    ////////////////////////////////////////////////////////////////////////////
    // Have RTWBag throw exceptions, since this location is not attached to a KB
    ////////////////////////////////////////////////////////////////////////////

    @Override public int getNumValues() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public String valueDump() {
        return SimpleBag.valueDump(this);
    }

    @Override public boolean isEmpty() {
        if (endsInSlot()) { 
            return getNumValues() == 0; 
        } else { 
            // We've wanted to get rid of this since 2013-02-13
            if (true) 
                throw new RuntimeException("Can we not get rid of this?"); 

            // It's a little bit of a bummer to have to invoke parent()
            return !parent().containsValue(lastAsValue()); 
        } 
    }

    @Override public boolean has1Value() {
        if (endsInSlot()) { 
            return getNumValues() == 1; 
        } else { 
            // We've wanted to get rid of this since 2013-02-13
            throw new RuntimeException("Can we not get rid of this?"); 
        } 
    }

    @Override public boolean has1String() {
        if (getNumValues() > 1) return false;
        return (into1Value() instanceof RTWStringValue);
    }

    @Override public boolean has1Integer() {
        if (getNumValues() > 1) return false;
        return (into1Value() instanceof RTWIntegerValue);
    }

    @Override public boolean has1Double() {
        if (getNumValues() > 1) return false;
        return (into1Value() instanceof RTWDoubleValue);
    }

    @Override public boolean has1Boolean() {
        if (getNumValues() > 1) return false;
        return (into1Value() instanceof RTWBooleanValue);
    }

    @Override public boolean has1Entity() {
        if (getNumValues() > 1) return false;
        return (into1Value() instanceof Entity);
    }

    @Override public Iterable<RTWValue> iter() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public Iterable<RTWBooleanValue> booleanIter() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public Iterable<RTWDoubleValue> doubleIter() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public Iterable<RTWIntegerValue> integerIter() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public Iterable<RTWStringValue> stringIter() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public Iterable<Entity> entityIter() {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }

    @Override public RTWValue into1Value() {
        if (getNumValues() == 1) return iter().iterator().next();
        else if (getNumValues() == 0) return null;
        else throw new RuntimeException("Multiple values found where scalar expected");
    }

    @Override public String into1String() {
        RTWValue v = into1Value();
        if (v == null) return null;
        if (v instanceof RTWStringValue) return v.asString();
        else throw new RuntimeException("Not a string");
    }

    @Override public Integer into1Integer() {
        RTWValue v = into1Value();
        if (v == null) return null;
        if (v instanceof RTWIntegerValue) return v.asInteger();
        else throw new RuntimeException("Not an integer");
    }

    @Override public Double into1Double() {
        RTWValue v = into1Value();
        if (v == null) return null;
        if (v instanceof RTWDoubleValue) return v.asDouble();
        else throw new RuntimeException("Not a double");
    }

    @Override public Boolean into1Boolean() {
        RTWValue v = into1Value();
        if (v == null) return null;
        if (v instanceof RTWBooleanValue) return v.asBoolean();
        else throw new RuntimeException("Not a boolean");
    }

    @Override public Entity into1Entity() {
        RTWValue v = into1Value();
        if (v == null) return null;
        if (v instanceof Entity) return (Entity)v;
        else throw new RuntimeException("Not an Entity");
    }

    @Override public RTWValue need1Value() {
        RTWValue x = into1Value();
        if (x == null) throw new RuntimeException("Slot is empty where scalar value expected");
        return x;
    }

    @Override public boolean need1Boolean() {
        Boolean x = into1Boolean();
        if (x == null) throw new RuntimeException("Slot is empty where scalar boolean expected");
        return x;
    }

    @Override public double need1Double() {
        Double x = into1Double();
        if (x == null) throw new RuntimeException("Slot is empty where scalar double expected");
        return x;
    }

    @Override public int need1Integer() {
        Integer x = into1Integer();
        if (x == null) throw new RuntimeException("Slot is empty where scalar integer expected");
        return x;
    }

    @Override public String need1String() {
        String x = into1String();
        if (x == null) throw new RuntimeException("Slot is empty where scalar string expected");
        return x;
    }

    @Override public Entity need1Entity() {
        Entity x = into1Entity();
        if (x == null) throw new RuntimeException("Slot is empty where scalar Entity expected");
        return x;
    }

    @Override public boolean containsValue(RTWValue v) {
        throw new RuntimeException("This RTWLocation is not attached to a KB and cannot be dereferenced");
    }
}
