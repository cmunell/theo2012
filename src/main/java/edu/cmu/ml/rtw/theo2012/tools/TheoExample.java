package edu.cmu.ml.rtw.theo2012.tools;

import java.io.File;
import java.io.PrintStream;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;

import edu.cmu.ml.rtw.theo2012.core.*;

/**
 * Brief demonstration of the use of Theo2012<p>
 *
 * This demonstrates Theo Layer 2, which is the most basic usual form of Theo2012.<p>
 *
 * Historical note: This originally rughly mirrored src/matlab/Theo/code/Theo2012/example_Theo3.m
 * from the old OntologyLearner svn repository, which is a MATLAB wrapper around what was then
 * called TomTheoLayer2 to serve as a demo and proof-of-concept for MATLAB use of the then-new Java
 * Theo2012 implementation.<p>
 *
 * bk:api: see comments below, which are not individually tagged bk:api<p>
 */
public class TheoExample {
    private static Logger log;

    protected Theo2 kb;

    /**
     * The real main (as a nonstatic member)
     * 
     * @param args
     *            The commandline arguments as-is
     */
    protected void run(String[] args) throws Exception {
        // Open a KB.  false means we're opening it read/write.  If it does not exist, it will be
        // created and prepopulated with the minimum content (e.g. everything, generalizations,
        // slot).
        //
        // We want to start from an empty KB so that we don't get tripped up on any pre-existing
        // content, so we'll delete it first.
        File kbName = new File("TheoExample.mdb");
        deleteDirectory(kbName);
        kb = TheoFactory.open(kbName, false, true); 

        // Create some some person entities.  Entities must exist before than can be used or otherwise
        // referred to.
        Entity tom = kb.get("tom");
        System.out.println(tom.entityExists());
        Entity person = kb.createPrimitiveEntity("person", kb.get("everything"));
        kb.createPrimitiveEntity("tom", person);
        Entity joan = kb.createPrimitiveEntity("joan", person);
        System.out.println(tom.entityExists());

        // Creating a new slot can be as simple as this.  It's imaginable that we might later add
        // methods like getSlot and creatSlot that would return Slot objects.  For now, we use
        // toSlot to turn the returned PrimitiveEntity object into the more specialized Slot object.
        Slot slot = kb.get("slot").toSlot();
        Slot wife = kb.createPrimitiveEntity("wife", slot).toSlot();

        // But if we want to set up an inverse slot and and give it constraints to be enforced, then
        // that needs to be set up as well.
        //
        // Our standing policy, at least for now, is that changing these kind of settings
        // retrospectively is not gauranteed to delete assertions from the KB that violate them.  We
        // envision that there will be in the future some sort of fsck-style operation responsible
        // for crawling through the KB and detecting / resolving violations.  Such a thing could be
        // set up to be triggered automatically, but this needs to be done carefully because it is
        // the kind of operation that could be expensive depending on the underlying implementation
        // and KB size.
        Slot haswife = kb.createPrimitiveEntity("haswife", slot).toSlot();

        // Note that we need to distinguish between a primitive string and an entity in the KB that
        // we are identifying by a string that contains its name.  That's why we saved Entity
        // objects above, and why we're passing them in here instead of passing in Strings.
        //
        // Many methods have convenience versions that will accept, for instance, slot names as
        // Strings.  This is done only when the given parameter must name an entity in the KB and
        // cannot be accidently interpreted as a primitive string.
        wife.addValue("inverse", haswife);
        wife.addValue("domain", person);
        wife.addValue("range", person);
        wife.addValue("nrofvalues", 1);
        // inverse is a symmetric slot; (wife inverse haswife) implies (haswife inverse wife).  So
        // we don't need to set that explicitly
        haswife.addValue("domain", person);
        haswife.addValue("range", person);
        haswife.addValue("nrofvalues", 1);

        // All that so we can assert the following
        tom.addValue(wife, joan);

        // Retrieve the known slots for this entity
        System.out.println(tom.getSlots());

        // Ask whether (tom wife joan) is a belief in the kb (the long way MATLAB-style)
        Belief tomWifeJoan = tom.getBelief(wife, joan);
        System.out.println(tomWifeJoan.entityExists());

        // And a short(er) way.  Query objects extend RTWBag, which acts like a set of RTWValue
        // objects, and which offers a whole slew of access methods and iterators.
        System.out.println(tom.getQuery(wife).containsValue(joan));

        // See how to add and delete single or all values.
        Slot children = kb.createPrimitiveEntity("children", slot).toSlot();
        Query tomChildren = tom.getQuery("children");
        tomChildren.addValue(kb.createPrimitiveEntity("meghan", person));
        tomChildren.addValue(kb.createPrimitiveEntity("shannon", person));

        // Printing a Query object will print the symbolic representation of the query itself ("tom
        // wife").  The valueDump method will dump out the values stored in the KB corresponding to
        // that query.  In other words, the Query object is like a pointer to some potential set of
        // values in the KB that, together with the query, comprise a set of beliefs, and the
        // valueDump method dereferences that pointer.
        System.out.println(tomChildren.valueDump());
        System.out.println(tomChildren.getNumValues());
        tomChildren.deleteValue(kb.get("shannon"));
        System.out.println(tomChildren.getNumValues());
        tom.deleteAllValues(children);
        System.out.println(tomChildren.getNumValues());

        // Examples of using primitive datatypes for ranges: integer, double, string, boolean, list,
        // any.  These correspond to our Java classes RTWIntegerValue, RTWDoubleValue,
        // RTWStringValue, RTWBooleanValue, RTWListValue, RTWValue (the interface class), which
        // themselves are wrappers around Integer, Double, String, Boolean, and List<RTWValue>.
        // Methods accepting Object will autoconvert the value to the appropriate RTWValue class.
        Slot nlegs = kb.createPrimitiveEntity("nlegs", slot).toSlot();
        nlegs.addValue("range", "integer");
        tom.addValue(nlegs, 2);
        System.out.println(tom.getQuery(nlegs).valueDump());

        Slot narms = kb.createPrimitiveEntity("narms", slot).toSlot();
        narms.addValue("range", "double");
        tom.addValue(narms, 2.001);
        tom.addValue(narms, 2.002);
        tom.addValue(narms, 2.003);
        System.out.println(tom.getQuery(narms).valueDump());
        tom.deleteValue(narms, 2.001);
        System.out.println(tom.getQuery(narms).valueDump());

        Slot literalstring = kb.createPrimitiveEntity("literalstring", slot).toSlot();
        literalstring.addValue("range", "string");
        tom.addValue("literalstring", "Tom");
        System.out.println(tom.getQuery(literalstring).valueDump());

        Slot isalive = kb.createPrimitiveEntity("isalive", slot).toSlot();
        isalive.addValue("range", "boolean");
        tom.addValue("isalive", true);
        System.out.println(tom.getQuery(isalive).valueDump());

        // RTWListValue implements List<RTWValue> abstractly, so, as with ArrayList, to actually
        // instantiate something, we use RTWArrayListValue.  RTWArrayListValue doesn't (yet?) have a
        // fancy constructor that accepts Object... and automatically converts the parameters to the
        // appropriate RTWValue types, so here we see an example of constructing those manually.
        // Note that Entity (of course) is also an RTWValue.
        Slot favoritelists = kb.createPrimitiveEntity("favoritelists", slot).toSlot();
        favoritelists.addValue("range", "list");
        tom.addValue("favoritelists", new RTWArrayListValue(new RTWStringValue("dog"),
                        RTWBooleanValue.TRUE, new RTWDoubleValue(3.14), joan));
        System.out.println(tom.getQuery("favoritelists").valueDump());

        // isEntity works for all entities.  In this case we're checking the existence of a slot.
        System.out.println(tom.entityExists("favoritelists"));

        // Deleting all of the generalizations of an entity causes that entity to no longer exist,
        // but the current implementation will throw an exception if you try to do that without
        // first removing all other content in the entity.  Theo provides a turnkey delete as a
        // library function to make life easier.
        kb.deleteEntity(tom);
        System.out.println(tom.entityExists());

        // Next try it with composite entities: queries and beliefs.

        // Causing an entity to generalize to something is equivalent to creating it
        tom.addValue("generalizations", person);
        tom.addValue(wife, joan);
        
        Slot availablemethods = kb.createPrimitiveEntity("availablemethods", slot).toSlot();
        tom.getQuery(wife).addValue(availablemethods, "inherit");
        System.out.println(tom.getQuery(wife).getQuery(availablemethods).valueDump());
        System.out.println(tom.getQuery(wife).getQuery(availablemethods).getBelief("inherit").entityExists());
        System.out.println(tom.getQuery(wife).getBelief(availablemethods, "inherit").entityExists());
        System.out.println(tom.getQuery(wife).getQuery(availablemethods).has1Entity());

        Slot probability = kb.createPrimitiveEntity("probability", slot).toSlot();
        tomWifeJoan.addValue(probability, 0.9999);
        System.out.println(tom.getBelief(wife, joan).getQuery(probability).valueDump());
        System.out.println(tomWifeJoan.entityExists());
        System.out.println(tomWifeJoan.getQuery(probability).entityExists());
        Belief tomWifeJoanProb9999 = tomWifeJoan.getBelief(probability, 0.9999);
        System.out.println(tomWifeJoanProb9999.entityExists());

        Slot why = kb.createPrimitiveEntity("why", slot).toSlot();
        System.out.println(tomWifeJoanProb9999.getBelief(why, "because").entityExists());
        System.out.println(tomWifeJoanProb9999.getQuery(why).containsValue(new RTWStringValue("because")));
        tomWifeJoanProb9999.addValue(why, "because");
        System.out.println(tomWifeJoanProb9999.getBelief(why, "because").entityExists());
        System.out.println(tomWifeJoanProb9999.getQuery(why).valueDump());

        // For a slightly more real-world example, have a look inside this recursive entity
        // pretty-printing method.
        pre(System.out, tom, "");

        // Don't forget to close -- you may wind up with data corruption if caches and buffers and
        // so-forth are not committed.
        kb.close();
    }

    /**
     * Recurively pretty-print the content of the given entity
     */
    protected void pre(PrintStream out, Entity e, String indent) {
        try {
            for (Slot slot : e.getSlots()) {
                Query q = e.getQuery(slot);
                out.print(indent + slot + ": ");
                out.print(q.valueDump());
                out.print("\n");

                for (RTWValue v : e.getQuery(slot).iter()) {
                    Entity sube = e.getBelief(slot, v);
                    if (!sube.getSlots().isEmpty()) {
                        out.print(indent + "  =" + v + "\n");
                        pre(out, sube, indent + "  ");
                    }
                }

                if (!q.getSlots().isEmpty())
                    pre(out, q, indent + "  ");
            }
        } catch (Exception ex) {
            throw new RuntimeException("pre(<out>, " + e + ")", ex);
        }
    }

    /**
     * Recursively delete file or directory
     */
    protected boolean deleteDirectory(File path) { 
        if (path.exists()) { 
            File[] files = path.listFiles(); 
            for (int i = 0; i < files.length; i++) { 
                if (files[i].isDirectory()) { 
                    deleteDirectory(files[i]); 
                } else { 
                    files[i].delete(); 
                } 
            } 
        } 
        return (path.delete()); 
    } 

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        try {
            System.setProperty("log4j.console", "true");
            log = LogFactory.getLogger();
            TheoExample me = new TheoExample();
            me.run(args);
        } catch (Exception e) {
            log.fatal("Uncaught exception", e);
            System.exit(2);
        }
    }
}
