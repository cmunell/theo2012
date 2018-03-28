package edu.cmu.ml.rtw.theo2012.core;

import java.util.Collection;
import java.util.ArrayList;

import edu.cmu.ml.rtw.util.TypeChangingFilter;

/**
 * This is a Theo1 implementation that adds only the minimum allowed functionality to a Theo0 
 * implementation.  This involves avoiding returning null, and not much else.  MinimalTheo1
 * provides a wrapped Theo0 returning wrapped entities.
 * <p>
 * Issues:
 * <ul>
 * <li>Can an Entity escape from being wrapped by disguising itself as an RTWLocation?
 * <li>We are not consistently unwrapping RTWValues, or Objects
 * </ul>
 */
public class MinimalTheo1 extends Theo1Base implements Theo1 {
	Theo0 theo0 = null;
	
	protected final TypeChangingFilter<Belief,Belief> beliefWrappingFilter = 
		new TypeChangingFilter<Belief, Belief>() {
		public boolean passes(Belief v) { return true; }
		public Belief convert(Belief v) { return new WBelief(v); }
	};		
	protected final TypeChangingFilter<Slot,Slot> slotWrappingFilter = 
		new TypeChangingFilter<Slot, Slot>() {
		public boolean passes(Slot v) { return true; }
		public Slot convert(Slot v) { return new WSlot(v); }
	};		

	/**
	 * A class to conveniently add RTWBag behavior to a collection of
	 * RTWValues 
	 * 
	 * @author welling
	 *
	 */
	public class BagWrapper extends SimpleBag implements RTWBag {
		Collection<RTWValue> col = null;
		
		BagWrapper(Collection<RTWValue> col_in) {
			col = col_in;
		}
		
		public Iterable<RTWValue> getValues() { return col; }
		
		public int getNumValues() { return col.size(); }
	}
	
	/**
	 * Unfortunately RTWValues may contain Entities, so they must get wrapped if they do
	 * (unless the entity is already wrapped).
	 * 
	 * @param v an RTWValue, possibly actually an Entity
	 * @return an RTWValue, possibly actually a wrapped Entity
	 */
	protected RTWValue maybeWrap(RTWValue v) {
		if (v instanceof Entity) {
			if (v instanceof WBelief) return v;
			else if (v instanceof WQuery) return v;
			else if (v instanceof WSlot) return v;
			else if (v instanceof WPrimEntity) return v;
			else if (v instanceof Belief) return new WBelief((Belief)v);
			else if (v instanceof Query) return new WQuery((Query)v);
			else if (v instanceof Slot) return new WSlot((Slot)v);
			else if (v instanceof PrimitiveEntity) return new WPrimEntity((PrimitiveEntity)v);
			else throw new RuntimeException("Don't know how to wrap "+v);
		}
		else return v;
	}
	
	/**
	 * Check the type of an entity and wrap it appropriately
	 * 
	 * @param e An Entity, wrapped or otherwise
	 * @return an appropriately wrapped version of e
	 */
	protected Entity wrapEntity(Entity e) {
		if (e instanceof WBelief) return e;
		else if (e instanceof WQuery) return e;
		else if (e instanceof WSlot) return e;
		else if (e instanceof WPrimEntity) return e;
		else if (e instanceof Belief) return new WBelief((Belief)e);
		else if (e instanceof Query) return new WQuery((Query)e);
		else if (e instanceof Slot) return new WSlot((Slot)e);
		else if (e instanceof PrimitiveEntity) return new WPrimEntity((PrimitiveEntity)e);
		else 
			throw new RuntimeException("Tried to wrap unsuitable entity "+e.getClass().getName());	
	}
	
	protected Slot unwrapSlot(Slot slot) {
		if (slot instanceof WSlot) return ((WSlot)slot).wrappedT;
		else return slot;
	}
	
	protected Entity unwrapEntity(Entity e) {
		if (e instanceof WBelief) return ((WBelief)e).wrappedT;
		else if (e instanceof WQuery) return ((WQuery)e).wrappedT;
		else if (e instanceof WSlot) return ((WSlot)e).wrappedT;
		else if (e instanceof WPrimEntity) return ((WPrimEntity)e).wrappedT;
		else return e;
	}
	
	protected RTWValue unwrapValue(RTWValue v) {
		if (v instanceof Entity) return unwrapEntity((Entity)v);
		else if (v instanceof RTWListValue) {
			RTWListValue al = new RTWArrayListValue();
			for (RTWValue sv : (RTWListValue)v) al.add(unwrapValue(sv));
			return al;
		}
		else return v;
	}
	/**
	 * The input Theo0 instance is presumed to be open already.
	 * 
	 * @param theo0_in The Theo0 instance to be wrapped
	 */
	public class Wrapper<T extends Entity> extends Entity0Base implements Entity {
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((wrappedT == null) ? 0 : wrappedT.hashCode());
			return result;
		}

		@Override
		public long getId() {
			return wrappedT.getId();
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof Wrapper))
				return false;
			Wrapper other = (Wrapper) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (wrappedT == null) {
				if (other.wrappedT != null)
					return false;
			} else if (!wrappedT.equals(other.wrappedT))
				return false;
			return true;
		}

            @Override protected Slot toSlot(String name) {
                throw new RuntimeException("bkdb: what to do with this, if anything?");
            }

            @Override protected Entity wrapEntity(Entity entity) {
                throw new RuntimeException("bkdb: implement me and promulgate wrapping and unwrapping if we don't wind up merging this class with something else first.");
            }

		final T wrappedT;
		public Wrapper(T t_in) { 
			if (t_in==null) throw new RuntimeException("No such entity");
			wrappedT = t_in; 
			}

        @Override public Object clone() throws CloneNotSupportedException {
            throw new RuntimeException("bkdb: return hypothetical parlance here?");
        }

        @Override
        public String toString() {
        	return wrappedT.toString();
        }
        
		@Override
		public boolean addValue(Slot slot, RTWValue value) {
			return wrappedT.addValue(unwrapSlot(slot), unwrapValue(value));
		}

		@Override
		public boolean addValue(Object slot, Object value) {
			if (slot instanceof Slot) slot = unwrapSlot((Slot)slot);
			if (value instanceof RTWValue) value = unwrapValue((RTWValue)value);
			return wrappedT.addValue(slot,value);
		}

		@Override
		public Belief addValueAndGetBelief(Slot slot, RTWValue value) {
			return new WBelief(wrappedT.addValueAndGetBelief(unwrapSlot(slot), unwrapValue(value)));
		}

		@Override
		public Belief addValueAndGetBelief(Object slot, Object value) {
			if (slot instanceof Entity) slot = unwrapEntity((Entity)slot);
			if (value instanceof RTWValue) value = unwrapValue((RTWValue)value);
			return new WBelief(wrappedT.addValueAndGetBelief(slot, value));
		}

		@Override
		public boolean deleteAllValues(Slot slot) {
			return wrappedT.deleteAllValues(unwrapSlot(slot));
		}

		@Override
		public boolean deleteAllValues(String slot) {
			return wrappedT.deleteAllValues(slot);
		}

		@Override
		public boolean deleteValue(Slot slot, RTWValue value) {
			return wrappedT.deleteValue(unwrapSlot(slot), unwrapValue(value));
		}

		@Override
		public boolean deleteValue(Object slot, Object value) {
			if (slot instanceof Entity) slot = unwrapEntity((Entity)slot);
			if (value instanceof RTWValue) value = unwrapValue((RTWValue)value);
			return wrappedT.deleteValue(slot, value);
		}

		@Override
		public Belief getBelief(Slot slot, RTWValue value) {
			Belief b = wrappedT.getBelief(unwrapEntity(slot), unwrapValue(value));
			if (b==null) {
				throw new RuntimeException("No such belief ( "+wrappedT+" "+unwrapEntity(slot)+" "+unwrapValue(value)+" )");				
			}
			else return new WBelief(b);
		}

		@Override
		public Belief getBelief(Object slot, Object value) {
			if (slot instanceof Entity) slot = unwrapEntity((Entity)slot);
			if (value instanceof RTWValue) value = unwrapValue((RTWValue)value);
			Belief b = wrappedT.getBelief(slot, value);
			if (b==null) {
				throw new RuntimeException("No such belief ( "+this+" "+slot+" "+value+" )");				
			}
			else return new WBelief(b);
		}

		@Override
		public Iterable<Belief> getBeliefs(Slot slot) {
			return beliefWrappingFilter.filter(wrappedT.getBeliefs(unwrapSlot(slot)));
		}

		@Override
		public Iterable<Belief> getBeliefs(String slot) {
			return beliefWrappingFilter.filter(wrappedT.getBeliefs(slot));
		}

		@Override
		public int getNumReferringValues(Slot slot) {
			return wrappedT.getNumReferringValues(unwrapSlot(slot));
		}

		@Override
		public int getNumReferringValues(String slot) {
			return wrappedT.getNumReferringValues(slot);
		}

		@Override
		public int getNumValues(Slot slot) {
			return wrappedT.getNumValues(unwrapSlot(slot));
		}

		@Override
		public int getNumValues(String slot) {
			return wrappedT.getNumValues(slot);
		}

		@Override
		public Query getQuery(Slot slot) {
			return new WQuery(wrappedT.getQuery(unwrapSlot(slot)));
		}

		@Override
		public Query getQuery(String slot) {
			return new WQuery(wrappedT.getQuery(slot));
		}

		@Override
		public RTWLocation getRTWLocation() {
			return wrappedT.getRTWLocation();
		}

		@Override
		public Iterable<Belief> getReferringBeliefs(Slot slot) {
			return beliefWrappingFilter.filter(wrappedT.getReferringBeliefs(unwrapSlot(slot)));
		}

		@Override
		public Iterable<Belief> getReferringBeliefs(String slot) {
			return beliefWrappingFilter.filter(wrappedT.getReferringBeliefs(slot));
		}

		@Override
		public Collection<Slot> getReferringSlots() {
			ArrayList<Slot> al = new ArrayList<Slot>();
			for (Slot s: slotWrappingFilter.filter(wrappedT.getReferringSlots())) al.add(s);
			return al;
		}

		@Override
		public RTWBag getReferringValues(Slot slot) {
			ArrayList<RTWValue> al = new ArrayList<RTWValue>();
			for (RTWValue v: wrappedT.getReferringValues(unwrapSlot(slot)).iter()) {
				al.add(maybeWrap(v));
			}
			return new BagWrapper(al);
		}

		@Override
		public RTWBag getReferringValues(String slot) {
			ArrayList<RTWValue> al = new ArrayList<RTWValue>();
			for (RTWValue v: wrappedT.getReferringValues(slot).iter()) {
				al.add(maybeWrap(v));
			}
			return new BagWrapper(al);
		}

		@Override
		public Collection<Slot> getSlots() {
			ArrayList<Slot> al = new ArrayList<Slot>();
			for (Slot s: slotWrappingFilter.filter(wrappedT.getSlots())) al.add(s);
			return al;
		}

		@Override
		public boolean isBelief() {
			return wrappedT.isBelief();
		}

		@Override
		public boolean entityExists(Slot slot) {
			return wrappedT.entityExists(unwrapSlot(slot));
		}

		@Override
		public boolean entityExists(String slot) {
			return wrappedT.entityExists(slot);
		}

		@Override
		public boolean entityExists() {
			return wrappedT.entityExists();
		}

		@Override
		public boolean isQuery() {
			return wrappedT.isQuery();
		}

                @Override
                public boolean isPrimitiveEntity() {
                        return wrappedT.isPrimitiveEntity();
                }

		@Override
		public boolean isSlot() {
			return wrappedT.isSlot();
		}

		@Override
		public Belief toBelief() {
			return new WBelief(wrappedT.toBelief());
		}

		@Override
		public Query toQuery() {
			return new WQuery(wrappedT.toQuery());
		}

                @Override
                public PrimitiveEntity toPrimitiveEntity() {
                        return new WPrimEntity(wrappedT.toPrimitiveEntity());
                }

		@Override
		public Slot toSlot() {
			return new WSlot(wrappedT.toSlot());
		}

		@Override
		public boolean asBoolean() {
			return wrappedT.asBoolean();
		}

		@Override
		public double asDouble() {
			return wrappedT.asDouble();
		}

		@Override
		public int asInteger() {
			return wrappedT.asInteger();
		}

		@Override
		public String asString() {
			return wrappedT.asString();
		}

		private MinimalTheo1 getOuterType() {
			return MinimalTheo1.this;
		}
	}
	

	public class WSlot extends Wrapper<Slot> implements Slot {

		public WSlot(Slot tIn) {
			super(tIn);
		}
		
		@Override
		public String getName() {
			return wrappedT.getName();
		}
		
	}
	
	public class WPrimEntity extends Wrapper<PrimitiveEntity> implements PrimitiveEntity {
		public WPrimEntity(PrimitiveEntity tIn) {
			super(tIn);
		}
		
		@Override
		public String getName() {
			return wrappedT.getName();
		}
	}
	
	public class WQuery extends Wrapper<Query> implements Query {
		class InnerBag extends SimpleBag implements RTWBag {
			public Iterable<RTWValue> getValues() {
				return WQuery.this.iter();
			}
			public int getNumValues() { return wrappedT.getNumValues(); }
		}
		InnerBag iBag;
		public WQuery(Query tIn) {
			super(tIn);
			iBag = new InnerBag();
		}

		@Override
		public boolean addValue(RTWValue value) {
			return wrappedT.addValue(unwrapValue(value));
		}

		@Override
		public boolean addValue(Object value) {
			if (value instanceof RTWValue) value = unwrapValue((RTWValue)value);
			return wrappedT.addValue(value);
		}

		@Override
		public Belief addValueAndGetBelief(RTWValue value) {
			return new WBelief(wrappedT.addValueAndGetBelief(unwrapValue(value)));
		}

		@Override
		public Belief addValueAndGetBelief(Object value) {
			if (value instanceof RTWValue) value = unwrapValue((RTWValue)value);
			return new WBelief(wrappedT.addValueAndGetBelief(value));
		}

		@Override
		public boolean deleteValue(RTWValue value) {
			return wrappedT.deleteValue(value);
		}

		@Override
		public boolean deleteValue(Object value) {
			return wrappedT.deleteValue(value);
		}

            @Override public boolean deleteAllValues() {
                try {
                    // Use iterative fetch-next-and-delete approach so that we don't invite the risks of
                    // iterating over the things we're modifying.  Deleting a value from a Query object does
                    // not invalidate that Query object, so we don't need to reconstruct it.
                    int numDeleted = 0;
                    boolean deletedSomething;
                    do {
                        deletedSomething = false;
                        for (RTWValue v : iter()) {
                            deleteValue(v);
                            deletedSomething = true;
                            numDeleted++;
                            break;
                        }
                    } while (deletedSomething);
                    return numDeleted > 0;
                } catch (Exception e) {
                    throw new RuntimeException("deleteAllValues()", e);
                }
            }

		@Override
		public Belief getBelief(RTWValue value) {
			Belief b = wrappedT.getBelief(value);
			if (b==null) {
				throw new RuntimeException("no such belief ( "+this+" "+value+" )");
			}
			else return new WBelief(b);
		}

		@Override
		public Belief getBelief(Object value) {
			Belief b = wrappedT.getBelief(value);
			if (b==null) {
				throw new RuntimeException("no such belief ( "+this+" "+value+" )");
			}
			else return new WBelief(b);
		}

		@Override
		public Iterable<Belief> getBeliefs() {
			return beliefWrappingFilter.filter(wrappedT.getBeliefs());
		}

		@Override
		public Entity getQueryEntity() {
			return wrapEntity(wrappedT.getQueryEntity());
		}

		@Override
		public Slot getQuerySlot() {
			return new WSlot(wrappedT.getQuerySlot());
		}

		@Override
		public Iterable<RTWBooleanValue> booleanIter() {
			return iBag.booleanIter();
		}

		@Override
		public boolean containsValue(RTWValue v) {
			return iBag.containsValue(v);
		}

		@Override
		public Iterable<RTWDoubleValue> doubleIter() {
			return iBag.doubleIter();
		}

		@Override
		public String valueDump() {
			return iBag.valueDump();
		}

		@Override
		public Iterable<Entity> entityIter() {
			return iBag.entityIter();
		}

		@Override
		public boolean equals(Object obj) {
			return iBag.equals(obj);
		}

		@Override
		public int getNumValues() {
			return wrappedT.getNumValues();
		}

		@Override
		public int hashCode() {
			return iBag.hashCode();
		}

		@Override
		public Iterable<RTWIntegerValue> integerIter() {
			return iBag.integerIter();
		}

		@Override
		public Boolean into1Boolean() {
			return iBag.into1Boolean();
		}

		@Override
		public Double into1Double() {
			return iBag.into1Double();
		}

		@Override
		public Entity into1Entity() {
			return iBag.into1Entity();
		}

		@Override
		public Integer into1Integer() {
			return iBag.into1Integer();
		}

		@Override
		public RTWValue into1Value() {
			return iBag.into1Value();
		}

		@Override
		public String into1String() {
			return iBag.into1String();
		}

		@Override
		public boolean has1Boolean() {
			return iBag.has1Boolean();
		}

		@Override
		public boolean has1Double() {
			return iBag.has1Double();
		}

		@Override
		public boolean isEmpty() {
			return iBag.isEmpty();
		}

		@Override
		public boolean has1Entity() {
			return iBag.has1Entity();
		}

		@Override
		public boolean has1Integer() {
			return iBag.has1Integer();
		}

		@Override
		public boolean has1Value() {
			return iBag.has1Value();
		}

		@Override
		public boolean has1String() {
			return iBag.has1String();
		}

		@Override
		public Iterable<RTWValue> iter() {
			return iBag.iter();
		}

		@Override
		public boolean need1Boolean() {
			return iBag.need1Boolean();
		}

		@Override
		public double need1Double() {
			return iBag.need1Double();
		}

		@Override
		public Entity need1Entity() {
			return iBag.need1Entity();
		}

		@Override
		public int need1Integer() {
			return iBag.need1Integer();
		}

		@Override
		public RTWValue need1Value() {
			return iBag.need1Value();
		}

		@Override
		public String need1String() {
			return iBag.need1String();
		}

		@Override
		public Iterable<RTWStringValue> stringIter() {
			return iBag.stringIter();
		}

	}
	
	public class WBelief extends Wrapper<Belief> implements Belief {
		public WBelief(Belief tIn) {
			super(tIn);
		}

		@Override
		public Query getBeliefQuery() {
			return new WQuery(wrappedT.getBeliefQuery());
		}

		@Override
		public RTWValue getBeliefValue() {
			return maybeWrap(wrappedT.getBeliefValue());
		}

		@Override
		public Belief getMirrorImage() {
			return new WBelief(wrappedT.getMirrorImage());
		}

		@Override
		public Entity getQueryEntity() {
			return wrapEntity(wrappedT.getQueryEntity());
		}

		@Override
		public Slot getQuerySlot() {
			return new WSlot(wrappedT.getQuerySlot());
		}

		@Override
		public boolean hasMirrorImage() {
			return wrappedT.hasMirrorImage();
		}

	}
	
	public MinimalTheo1(Theo0 theo0_in) {
		theo0 = theo0_in;
	}
	@Override
	public PrimitiveEntity createPrimitiveEntity(String name,
			Entity generalization) {
		return new WPrimEntity(theo0.createPrimitiveEntity(name, unwrapEntity(generalization)));
	}
	@Override
	public Slot createSlot(String master, Slot generalization,
			String inverseSlave) {
		return new WSlot(theo0.createSlot(master,unwrapSlot(generalization), inverseSlave));
	}
	@Override
	public boolean deleteEntity(Entity entity) {
		return theo0.deleteEntity(unwrapEntity(entity));
	}
	@Override
	public PrimitiveEntity get(String primitiveEntity) {
		PrimitiveEntity e = theo0.get(primitiveEntity);
		if (e==null) throw new RuntimeException("No such entity "+primitiveEntity);
		else return new WPrimEntity(e);
	}
	@Override
	public Entity get(RTWLocation location) {
		return wrapEntity(theo0.get(location));
	}
	@Override
	public PrimitiveEntity getPrimitiveEntity(String primNameString) {
		PrimitiveEntity e = (PrimitiveEntity)theo0.getPrimitiveEntity(primNameString);
		if (e==null) throw new RuntimeException("No such primitive entity "+primNameString);
		else if (e instanceof Slot) return new WSlot((Slot)e);
		else return new WPrimEntity(e);
	}
	@Override
	public Slot getSlot(String slotName) {
		Slot s = theo0.getSlot(slotName);
		if (s==null) throw new RuntimeException("No such slot "+s);
		else return new WSlot(s);
	}
        @Override
        public void close() {
                theo0.close();
        }
	@Override
	public boolean isOpen() {
		return theo0.isOpen();
	}
	@Override
	public boolean isReadOnly() {
		return theo0.isReadOnly();
	}
	@Override
	public void setReadOnly(boolean makeReadOnly) {
		theo0.setReadOnly(makeReadOnly);
	}

    @Override public RTWValue ioctl(String syscall, RTWValue params) {
        try {
            return theo0.ioctl(syscall, params);
        } catch (Exception e) {
            throw new RuntimeException("ioctl(\"" + syscall + "\", " + params + ")", e);
        }
    }

	public void writeDot(String fname, String title, Entity startEntity) {
		theo0.writeDot(fname, title, unwrapEntity(startEntity));
	}
}
