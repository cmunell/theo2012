package edu.cmu.ml.rtw.theo2012.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

/**
 * Abstract base class for {@link RTWLocation} implementations that provides boilerplate
 * implementation for the storage and manipulation of the location that an RTWLocation represents.
 *
 * Notably absent from this base class is the implementation of the methods that are inhereted from
 * {@link RTWBag}.  This is fully desirable considering that we'll typically want to share the
 * boilerplate present in this class across different RTWBag implementations.
 *
 * Java's lack of support for multiple inheritance makes it difficult to combine multiple sources of
 * boilerplate.  The class structure we have for RTWBag and RTWLocation may not be the best
 * solution, but it works for our immediate purposes, and this is sufficient given that it's not yet
 * clear what will become of RTWLocation in the future.
 *
 * See {@link SimpleLocation} for an easy way to come up with a full RTWLocation solution based on
 * using an RTWBag implementation that derives from {@link SimpleBag}.
 */
public abstract class RTWLocationBase implements RTWLocation {
    private final static Logger log = LogFactory.getLogger();
    
    /**
     * The location that we represent.
     *
     * Elements will either be Strings or RTWElementRefs.
     *
     * Maybe we could pick up a tiny bit of speed using an array and managing it ourselves, but this
     * is a cleaner and easier way to start.
     */
    protected Object[] addr;

    /**
     * Subclasses should override this in order that new RTWLocation instances created by the
     * .subslot method and similar are of the most-derived-type, and are attached to the same Store
     * object
     *
     * The alternative is to override .subslot and the rest, which some subclasses might want to do
     * anyway for reasons of their own.
     */
    protected abstract RTWLocationBase newRTWLocation(Object[] list);

    /**
     * Return element i of this address
     *
     * bkdb: Among the problems here is that we can't adequately gaurd against the return value
     * being mutated.  And that would be the case even for presenting e.g. List<Object> as an
     * intereface.  Relying on the immutability of the set of possible element types isn't very
     * good, e.g. an RTWArrayListValue inside an RTWElementRef or something like that.  I guess the
     * right thing to do is to wait until the KB code is a bit more layered and then find the right
     * way to slice things in half so that KB code can efficiently interpret the entityrefs and
     * ideally still leave things like interpretation of Strings starting with "=" and handling of
     * the different kinds of legitimate objects be left in here.
     *
     * But, for now, it looks like we can just leave it protected.  Store implementations will
     * probably want to add a method like this to their RTWLocation subclasses so that they can get
     * fast direct access as well.  They should know well enough to not mutate anything.
     */
    protected Object get(int i) {
        return addr[i];
    }

    /**
     * Return the last value in the address
     *
     * Returns null if this is a zero-length location
     */
    protected Object last() {
        if (addr.length == 0) return null;
        return addr[addr.length - 1];
    }

    /**
     * Construct a new location
     *
     * For example:
     *
     * new RTWLocation("cake", "generalizations");
     */
    public RTWLocationBase(Object... list) {
        // The KB retrieval code will be the ultimate arbiter of what elements are valid.  We might
        // find it useful to add additional validation here to help catch bugs early on, especially
        // if we can easily turn it on/off for speed.
        addr = list;

        /* bkdb: this appeared while re-merging into kb4 in Fall 2014.  I'm not sure whether or not
         * it's needed.  If so, uncomment it.  If not, it should be deleted, say, three months after
         * NELL is running on T2012.

         for (int i = 0; i < addr.length; i++) { 
             if (addr[i] instanceof RTWStringValue) { 
                 addr[i] = ((RTWStringValue)addr[i]).asString(); 
             } else if (addr[i] instanceof PrimitiveEntity) { 
                 // This case is for KbM-era compatability.  We probably should not do nor allow this 
                 // for Theo2012 purposes.  [This is a pre Fall 2014 comment, btw, and might no
                 // longer apply]
                 addr[i] = ((PrimitiveEntity)addr[i]).getName(); 
             } 
         } 
        */
    }

    /**
     * Construct a new location from a List<String>
     *
     * Format should be of the form:
     *
     * Lists.newArrayList("cake", "generalizations", "candidateValues");
     */
    public RTWLocationBase(List<String> list) {
        // The KB retrieval code will be the ultimate arbiter of what elements are valid.  We might
        // find it useful to add additional validation here to help catch bugs early on, especially
        // if we can easily turn it on/off for speed.
        addr = new Object[list.size()];
        for (int i = 0; i < list.size(); i++) {
            // bkdb: pre-wedge version: addr[i] = list.get(i).toLowerCase();
            addr[i] = list.get(i);
        }
    } 

    /**
     * Construct a new location from a String[]
     *
     * Semantics are the same as for List<String>
     */
    public RTWLocationBase(String[] list) {
        // The KB retrieval code will be the ultimate arbiter of what elements are valid.  We might
        // find it useful to add additional validation here to help catch bugs early on, especially
        // if we can easily turn it on/off for speed.

        // I wonder if we can do arraycopy here.  Not clear that this will be used anywhere very
        // important in the long run.
        addr = new Object[list.length];
        for (int i = 0; i < list.length; i++)
            addr[i] = list[i];
    }

    public RTWLocationBase append(Object... list) {
        Object[] newaddr = new Object[addr.length + list.length];
        System.arraycopy(addr, 0, newaddr, 0, addr.length);
        System.arraycopy(list, 0, newaddr, addr.length, list.length);
        return newRTWLocation(newaddr);
    }

    public RTWLocationBase subslot(String slot) {
        Object[] newaddr = new Object[addr.length + 1];
        System.arraycopy(addr, 0, newaddr, 0, addr.length);
        //bkdb: pre-wedge version: newaddr[addr.length] = slot.toLowerCase();
        newaddr[addr.length] = slot;
        return newRTWLocation(newaddr);
    }

    public RTWLocationBase element(RTWElementRef e) {
        if (addr.length < 1 || 
                (!(addr[addr.length - 1] instanceof String)
                        && !(addr[addr.length - 1] instanceof RTWStringValue)
                        && !(addr[addr.length - 1] instanceof PrimitiveEntity)))
            throw new RuntimeException("Cannot append an RTWElementRef because this RTWLocation ("
                    + toString() + ") does not end in a slot name");
        Object[] newaddr = new Object[addr.length + 1];
        System.arraycopy(addr, 0, newaddr, 0, addr.length);
        newaddr[addr.length] = e;
        return newRTWLocation(newaddr);
    }

    public RTWLocationBase element(RTWValue v) {
        return element(new RTWElementRef(v));
    }

    public RTWLocationBase parent() {
        if (addr.length == 0)
            throw new RuntimeException("Cannot call parent on a zero-length location");
        Object[] newaddr = new Object[addr.length - 1];
        System.arraycopy(addr, 0, newaddr, 0, addr.length - 1);
        return newRTWLocation(newaddr);
    }

    public RTWLocationBase firstN(int n) {
        if (n > addr.length)
            throw new RuntimeException(n + " elements requested, but we have only " + addr.length);
        Object[] newaddr = new Object[n];
        System.arraycopy(addr, 0, newaddr, 0, n);
        return newRTWLocation(newaddr);
    }

    public RTWLocationBase siblingSlot(String slot) {
        if (addr.length < 1
                || (!(addr[addr.length - 1] instanceof String)
                        && !(addr[addr.length - 1] instanceof RTWStringValue)
                        && !(addr[addr.length - 1] instanceof PrimitiveEntity)))
            throw new RuntimeException("siblingSlot cannot be used on " + toString()
                    + " because it doesn't end in a slot name");
        Object[] newaddr = new Object[addr.length];
        System.arraycopy(addr, 0, newaddr, 0, addr.length-1);
        newaddr[addr.length-1] = slot;
        return newRTWLocation(newaddr);
    }

    public String lastAsSlot() {
        Object o = last();
        if (o == null) return null;
        if (!(o instanceof String) && !(o instanceof RTWStringValue) && !(o instanceof PrimitiveEntity))  // bk: entityref
            throw new RuntimeException("RTWLocation must end in a slot name, not " + o);
        if (o instanceof RTWStringValue) return ((RTWStringValue)o).asString();
        if (o instanceof PrimitiveEntity) return ((PrimitiveEntity)o).getName();
        return (String)o;
    }

    public RTWElementRef lastAsElement() {
        Object o = last();
        if (o == null) return null;
        if (!(o instanceof RTWElementRef)) {
            // bkdb: do we need to allow RTWPointerValue here as well?  Or is that going to go away anyhow?
            throw new RuntimeException("RTWLocation must end in an element reference, not " + o);
        }
        return (RTWElementRef)o;
    }

    public RTWValue lastAsValue() {
        RTWElementRef e = lastAsElement();
        if (e == null) return null;
        return e.getVal();
    }

    public boolean endsInSlot() {
        return isSlot(size()-1);
    }

    public String getPrimitiveEntity() {   // bk: entityref I guess we'll have to change this to RTWValue at some point so that it can return an entity ref
        if (addr.length == 0) return null;
        if (addr[0] instanceof String) return (String)addr[0];
        if (addr[0] instanceof RTWStringValue) return ((RTWStringValue)addr[0]).asString();  // bk: entityref
        if (addr[0] instanceof PrimitiveEntity) return ((PrimitiveEntity)addr[0]).getName();
        else throw new RuntimeException("First element of location (" + toString()
                + ") must be a string designating a top-level entity, not " + addr[0]
                + ", which is a " + addr[0].getClass().getName());
    }

    public int size() {
        return addr.length;
    }

    public boolean isSlot(int i) {
        Object o = get(i);
        return ((o instanceof String) || (o instanceof RTWStringValue) || (o instanceof PrimitiveEntity));  // bk: entityref.
    }

    public String getAsSlot(int i) {
        Object o = get(i);
        if (o instanceof String) return (String)o;
        if (o instanceof RTWStringValue) return ((RTWStringValue)o).asString();  // Is this the right way to go?  bk:entityref  I guess it has to be if e.g. TheoStore are using String throughout. 
        if (o instanceof PrimitiveEntity) return ((PrimitiveEntity)o).getName();
        throw new RuntimeException("Element " + i + " (" + o + ") is not a slot name");
    }

    public RTWElementRef getAsElement(int i) {
        Object o = get(i);
        if (o instanceof RTWElementRef) return (RTWElementRef)o;  // bkdb: or would we need to support RTWPointer here as well?  Or is RTWPointerValue going away anyhow?

        // bkdb 2014-09-24: It seems awfully easy to put an Entity into an RTWLocation instead of an
        // RTWElementRef, particularly an RTWPointer, which, at present, is a subclass of Entity.
        // There doesn't seem to be a lot of vision on record about what to do here, whether we
        // should admit an Entity, ditch RTWEelementRef, etc.  And it looks like I'm not going to
        // remember what was intended or see what would make the most sense until we're well into
        // the Wedge.  So if we believe that people shouldn't be using RTWLocation, and that it
        // should not be central, then that means legacy code should operate through things like
        // KbM.getValue instead of building RTWLocations on their own (such places that need to do
        // that should obviously be using Theo Parlance, no?).  So I'm going to try leaving this
        // exception in place and changing KbM to have special cases as necessary to support legacy
        // code and/or replacing legacy code with new Theo2012-centric code.
        //
        // 2014-10-09: I'm going to guess that all Entities must be regarded as not being
        // ElementRefs, since a Slot, or even a PrimitiveEntity could get in as a slot.  And if we
        // admit any kind of Entity as a self-identified slot, then we cannot allow any of them to
        // be an ElementRef because that would result in an ambiguity.
        //
        // Standing FODO here would be to relax all uses of PrimitiveEntity here into Entity.  Or
        // perhaps the more correct thing to do would be to always check whether or not something is
        // an RTWElementRef.  (Or to just toss this stuff out in favor of something
        // better-designed).
        throw new RuntimeException("Element " + i + " (" + o
                + ") is not an element reference but rather a " + o.getClass().getName());
    }

    public RTWValue getAsValue(int i) {
        return getAsElement(i).getVal();
    }

    @Override public int hashCode() {
        // We could probably go faster and/or cache the hash code if it becomes an issue.
        int code = 4;
        for (int i = 0; i < size(); i++)
            code += get(i).hashCode();
        return code;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof RTWLocation)) return false;
        if (obj instanceof RTWLocationBase) {
            // Slightly faster because we can use our protected get
            RTWLocationBase o = (RTWLocationBase)obj;
            // Store-sameness check goes here
            if (size() != o.size()) return false;
            for (int i = 0; i < size(); i++)
                if (!get(i).equals(o.get(i))) return false;
        } else {
            // Have to condition each element inspection on whether or not it's a slot
            RTWLocation o = (RTWLocation)obj;
            // Store-sameness check goes here
            if (size() != o.size()) return false;
            for (int i = 0; i < size(); i++) {
                if (o.isSlot(i))
                    if (!get(i).equals(o.getAsSlot(i))) return false;
                else
                    if (!get(i).equals(o.getAsElement(i))) return false;
            }
        }
        return true;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        boolean comma = false;
        for (Object o : addr) {
            try {
                if (comma)  sb.append(", ");
                else comma = true;
                sb.append(o.toString());
            } catch (Exception e) {
                throw new RuntimeException("Processing Object " + o + " after having built \""
                        + sb.toString() + "\"", e);
            }
        }
        sb.append(">");
        return sb.toString();
    }

    ////////////////////////////////////////////////////////////////////////////
    // The rest of these come from RTWBag and are left for subclasses to implement according to
    // their own particular methods of KB storage
    ////////////////////////////////////////////////////////////////////////////

    @Override public abstract int getNumValues();

    @Override public abstract String valueDump();

    @Override public abstract boolean isEmpty();

    @Override public abstract boolean has1Value();

    @Override public abstract boolean has1String();

    @Override public abstract boolean has1Integer();

    @Override public abstract boolean has1Double();

    @Override public abstract boolean has1Boolean();

    @Override public abstract Iterable<RTWValue> iter();

    @Override public abstract Iterable<RTWBooleanValue> booleanIter();

    @Override public abstract Iterable<RTWDoubleValue> doubleIter();

    @Override public abstract Iterable<RTWIntegerValue> integerIter();

    @Override public abstract Iterable<RTWStringValue> stringIter();

    @Override public abstract RTWValue into1Value();

    @Override public abstract String into1String();

    @Override public abstract Integer into1Integer();

    @Override public abstract Double into1Double();

    @Override public abstract Boolean into1Boolean();

    @Override public abstract RTWValue need1Value();

    @Override public abstract boolean need1Boolean();

    @Override public abstract double need1Double();

    @Override public abstract int need1Integer();

    @Override public abstract String need1String();

    @Override public abstract boolean containsValue(RTWValue v);
}
