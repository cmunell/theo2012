package edu.cmu.ml.rtw.theo2012;

/**
 * A {@link Entity} that is known to be and that can be used as a Theo Slot<p>
 *
 * A Theo Slot is a primitive entity that descends from the "slot" entity through the
 * generalizations hierarchy that Theo maintains among primitive entities.<p>
 *
 * Being a Slot does not offer any additional API beyond being a {@link PrimitiveEntity}; the
 * purpose of having Slot as a sub-interface is to be able to use Java's typing mechanism to
 * positively identify those Entities that are known to be slots.<p>
 *
 * bkdb: relationship with String, also Entity.getName<p>
 *
 * bkdb: TODO: do we require that slots be equal strictly on primitive entity name?  If not, then it might be useful to recommend that implementations warn or throw an exception when slots from different layers or KBs are compared because it can be a difficult problem to track down.  Or maybe recommend a final static boolean for strict checking and warning for debugging purposes.<p>
 */
public interface Slot extends PrimitiveEntity {
}
