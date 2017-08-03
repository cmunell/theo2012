package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;

/**
 * This class is intended as a minimal base for things which must implement RTWBag
 *
 * This is based around the idea that the implementation need only furnish an Iterable<T> via the
 * abstract getValues method, as well as a getNumValues method.  We ought not to assume that it is
 * tractable to hold all of the values in an RTWBag in memory at once, but it bears noticing that
 * Collection<T> is an Iterable<T>, and that it therefore provides an easy basis for a SimpleBag
 * subclass that does not attempt to have ideal scaling properties.
 *
 * Implementations may wish to selectively override methods for speed.  The contains method in
 * particular asks for something more efficient.
 *
 * This calss used to be templatized <T extends RTWValue>, which is a cool idea, but it turns out
 * that we cannot in this case return getValues() directly in the implementation of iter(), and I'm
 * going to take an educated guess that speed here is more valuable than the extra generality of <T
 * extends RTWValue> that it's not clear we'll actually need.
 *
 * @author welling
 */
public abstract class SimpleBag extends RTWBagBase implements RTWBag {
	protected abstract Iterable<RTWValue> getValues();

    /**
     * This is an implementation of dump for use by classes that implement RTWBag by composing an
     * RTWBag.
     *
     * Such classes might not want to simply forward the dump method because there might be
     * behaviors of the composed RTWBag that are being modified.  A good example of this is Theo2012
     * Query objects; they will typically return Entity values found in the back as instances of
     * their own Entity objects.  These sort of behaviors become automatic when using this version
     * of dump and passing in "this" as the parameter.
     */
    public static String valueDump(RTWBag bag) {
        if (bag.getNumValues()==0) return "{}";
        else {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (RTWValue v : bag.iter()) {
                sb.append(v.toString());
                sb.append(", ");
            }
            sb.deleteCharAt(sb.length()-1);
            sb.deleteCharAt(sb.length()-1);
            sb.append("}");
            return sb.toString();
        }
    }
    

        @Override
        public abstract int getNumValues();

	@Override
	public Iterable<RTWValue> iter() {
            return getValues();
	}

	@Override
	public Iterable<RTWBooleanValue> booleanIter() {
            return new ConversionIterator<RTWBooleanValue>(iter().iterator(), RTWBooleanValue.class);
	}

	@Override
	public Iterable<RTWDoubleValue> doubleIter() {
            return new ConversionIterator<RTWDoubleValue>(iter().iterator(), RTWDoubleValue.class);
	}

	@Override
	public Iterable<RTWIntegerValue> integerIter() {
            return new ConversionIterator<RTWIntegerValue>(iter().iterator(), RTWIntegerValue.class);
	}

	@Override
	public Iterable<RTWStringValue> stringIter() {
            return new ConversionIterator<RTWStringValue>(iter().iterator(), RTWStringValue.class);
	}

	@Override
	public Iterable<Entity> entityIter() {
            return new ConversionIterator<Entity>(iter().iterator(), Entity.class);
	}

	public boolean containsValue(RTWValue v) {
                // Not very fast, but subclasses can override
		for (RTWValue v2: iter()) {
			if (v2.equals(v)) return true;
		}
		return false;
	}

	@Override
	public String valueDump() {
            return valueDump(this);
	}

	@Override
	public Boolean into1Boolean() {
		RTWValue v = into1Value();
                if (v == null) return null;
		if (v instanceof RTWBooleanValue) return v.asBoolean();
		else throw new RuntimeException("Not a boolean");
	}

	@Override
	public Double into1Double() {
		RTWValue v = into1Value();
                if (v == null) return null;
		if (v instanceof RTWDoubleValue) return v.asDouble();
		else throw new RuntimeException("Not a double");
	}

	@Override
	public Integer into1Integer() {
		RTWValue v = into1Value();
                if (v == null) return null;
		if (v instanceof RTWIntegerValue) return v.asInteger();
		else throw new RuntimeException("Not an integer");
	}

	@Override
	public String into1String() {
		RTWValue v = into1Value();
                if (v == null) return null;
		if (v instanceof RTWStringValue) return v.asString();
		else throw new RuntimeException("Not a string");
	}

	@Override
	public Entity into1Entity() {
		RTWValue v = into1Value();
                if (v == null) return null;
		if (v instanceof Entity) return (Entity)v;
		else throw new RuntimeException("Not an Entity");
	}

	@Override
	public RTWValue into1Value() {
		if (getNumValues() == 1) return iter().iterator().next();
		else if (getNumValues() == 0) return null;
		else throw new RuntimeException("Multiple values found where scalar expected");
	}

	@Override
	public boolean has1Boolean() {
                if (getNumValues() > 1) return false;
		return (into1Value() instanceof RTWBooleanValue);
	}

	@Override
	public boolean has1Double() {
                if (getNumValues() > 1) return false;
		return (into1Value() instanceof RTWDoubleValue);
	}

	@Override
	public boolean has1Integer() {
                if (getNumValues() > 1) return false;
		return (into1Value() instanceof RTWIntegerValue);
	}

	@Override
	public boolean has1String() {
                if (getNumValues() > 1) return false;
		return (into1Value() instanceof RTWStringValue);
	}

	@Override
	public boolean has1Entity() {
                if (getNumValues() > 1) return false;
		return (into1Value() instanceof Entity);
	}

	@Override
	public boolean has1Value() {
		return (getNumValues()==1);
	}

	@Override
	public boolean need1Boolean() {
            Boolean x = into1Boolean();
            if (x == null) throw new RuntimeException("Slot is empty where scalar boolean expected");
            return x;
	}

	@Override
	public double need1Double() {
            Double x = into1Double();
            if (x == null) throw new RuntimeException("Slot is empty where scalar double expected");
            return x;
	}

	@Override
	public int need1Integer() {
            Integer x = into1Integer();
            if (x == null) throw new RuntimeException("Slot is empty where scalar integer expected");
            return x;
	}

	@Override
	public String need1String() {
            String x = into1String();
            if (x == null) throw new RuntimeException("Slot is empty where scalar string expected");
            return x;
	}

	@Override
	public Entity need1Entity() {
            Entity x = into1Entity();
            if (x == null) throw new RuntimeException("Slot is empty where scalar Entity expected");
            return x;
	}

	@Override
	public RTWValue need1Value() {
            RTWValue x = into1Value();
            if (x == null) throw new RuntimeException("Slot is empty where scalar value expected");
            return x;
	}

	@Override
	public boolean isEmpty() {
		return (getNumValues()==0);
	}
}
