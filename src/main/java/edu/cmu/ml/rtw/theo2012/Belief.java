package edu.cmu.ml.rtw.theo2012;

/**
 * An {@link Entity} that represents a Theo Belief in particular<p>
 *
 * A Theo Belief has the form (Q = V), where Q is any Query, and V is an RTWValue, which may itself
 * be a Theo Entity.  Expanding on this, because a Theo Query has the form (E S), where E is an
 * Entity and S is a Slot, a Belief can equivalently be thought of as having the form ((E S) =
 * V).<p>
 *
 * Assuming the above hold true, the real use of a Belief object is for breaking down Theo
 * expressions or building up new ones that take the form of Beliefs about Beliefs -- that is, a ((E
 * S) = V) expression where V itself is a Belief, or where E is itself a Belief.<p>
 */
public interface Belief extends Entity {

    /**
     * Considering this Belief as "(Q = V)", return the "Q" part.
     */
    public Query getBeliefQuery();

    /**
     * Considering this Belief as "(Q = V)", return the "V" part.
     */
    public RTWValue getBeliefValue();

    /**
     * Considering this Belief as "((E S) = V", return the "E" part.
     */
    public Entity getQueryEntity();

    /**
     * Considering this Query as "((E S) = V", return the "S" part.
     */
    public Slot getQuerySlot();
    
    /**
     * Considering this Belief as "((E S) = V)", return the Belief ((V S*) = E) 
     * where S* is the inverse of S.  If no such mirror image belief exists (because
     * V is not an Entity or because the slot S* does not exist, a RuntimeException
     * is thrown.
     */
    public Belief getMirrorImage();
    
    /**
     * Returns false if getMirrorImage() will raise an exception.
     */
    public boolean hasMirrorImage();
    
}