package edu.cmu.ml.rtw.theo2012.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.cmu.ml.rtw.util.AsyncInputStream;
import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Timer;

import edu.cmu.ml.rtw.theo2012.core.*;

/**
 * Utility methods for reading and writing HFT files
 *
 * This uses the 2015 definition of HFT0, where each line is an "ab" command followed by a Belief
 * rendered into a MATLAB-style string
 *
 * In keeping with a number of our other classes, this will automatically gzip or ungzip the HFT
 * file if the filename ends in ".gz".  Maybe that's a bit ghetto, but it's convenient.
 */
public class HFTUtil {
    private final static Logger log = LogFactory.getLogger();
    protected Theo2 kb;

    /**
     * Constructor associating this HFTUtil instance with a particular KB
     */
    public HFTUtil(Theo2 kb) {
        this.kb = kb;
    }

    ////////////////////////////////////////////////////////////////////////////
    // "import" commands
    ////////////////////////////////////////////////////////////////////////////

    protected boolean continueOnError = true;
    protected boolean logCommands = false;
    protected boolean parseOnly = false;

    protected void importHFT0Line(String line) {
        try {
            // Skip commented and empty lines
            if (line.length() == 0 || line.charAt(0) == '#') return;

            int pos = 0;
            if (line.length() < 4
                    || (line.charAt(pos++) != 'a')
                    || (line.charAt(pos++) != 'b')
                    || (line.charAt(pos++) != ' ')) {
                if (continueOnError)
                    log.warn("Ignoring unrecognized HFT0 command: " + line);
                else
                    throw new RuntimeException("Ignoring unrecognized HFT0 command: " + line);
                return;
            }

            RTWValue expression = kb.valueFromString(line.substring(3));
            if (logCommands) log.debug(expression.toString());  // bkdb: shouldn't need tostring
            if (expression instanceof Entity) {
                Entity entity = (Entity)expression;
                if (entity.isBelief()) {
                    if (!parseOnly) {
                        Belief b = entity.toBelief();
                        b.getBeliefQuery().addValue(b.getBeliefValue());
                    }
                } else {
                    if (continueOnError)
                        log.warn("Ignoring non-belief expression " + expression);
                    else
                        throw new RuntimeException("Ignoring non-belief expression " + expression);
                }
            } else {
                if (continueOnError)
                    log.warn("Ignoring non-entity expression " + expression);
                else
                    throw new RuntimeException("Ignoring non-entity expression " + expression);
            }
        } catch (Exception e) {
            throw new RuntimeException("importHFT0Line(\"" + line + "\")", e);
        }
    }

    public void importHFT0(BufferedReader in, String sourceName) {
        try {
            int lines = 0;
            long totalMemory, usedMemory;
            Timer lastJVMReport = null;
            int errorCount = 0;

            try {
                String line = in.readLine();
                lines++;
                if (!line.startsWith("HFTBEGIN 0"))
                    throw new RuntimeException("Unrecognized HFT header line: " + line);
                boolean gotEnd = false;
                while (true) {
                    line = in.readLine().trim();
                    lines++;
                    if (lines % 1000000 == 0) {
                        String jvmString = "";
                        if (lastJVMReport == null || lastJVMReport.getElapsedMin() >= 10) {
                            totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
                            usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
                            jvmString = " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory
                                    + "MB)";
                            if (lastJVMReport == null) lastJVMReport = new Timer();
                            lastJVMReport.start();
                        }
                        log.info("Imported " + (lines / 1000000) + "M lines" + jvmString);
                    }
                    if (line.startsWith("HFTEND")) {
                        gotEnd = true;
                        break;
                    }
                    if (line.equals("")) continue;
                    try {
                        importHFT0Line(line);
                    } catch (Exception e) {
                        if (continueOnError) {
                            log.error(sourceName + ":" + line, e);
                            errorCount++;
                            if (errorCount > 100)
                                throw new RuntimeException("Too many errors: aborting");
                        } else {
                            throw new RuntimeException(sourceName + ":" + line, e);
                        }
                    }
                }
                if (!gotEnd)
                    throw new RuntimeException("Incomplete HFT file");
                log.info("Finished importing " + lines + " HFT lines from " + sourceName);
            } catch (Exception e) {
                log.fatal("At " + sourceName + " line " + lines, e);
            }
            
            in.close();
        } catch (Exception e) {
            throw new RuntimeException("importHFT0(<in>, \"" + sourceName +"\")", e);
        }
    }

    public void importHFT0(String hftFile) {
        try {
            BufferedReader in;
            if (hftFile.endsWith(".gz")) {
                // Are you ready to have some fun?
                in = new BufferedReader(new InputStreamReader(new AsyncInputStream(new GZIPInputStream(new FileInputStream(hftFile)))));
            } else {
                in = new BufferedReader(new FileReader(hftFile));
            }
            log.info("Importing from " + hftFile + "...");
            importHFT0(in, hftFile);
            in.close();
        } catch (Exception e) {
            throw new RuntimeException("importHFT0(\"" + hftFile + "\")", e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // "export" commands
    //
    // We have two important issues during an export.  One is that we must export the KB content in
    // an order such that importation will work trivially, meaning we have to pay attention to
    // things like creating entities before asserting any beliefs about them (including the case of
    // a belief where the value is a reference to that entity, which might be a composite entity).
    // Second, we want to not duplicate assertions; this dovetails with the first issue in that we
    // want to be able to know whether or not we've already asserted the subject and object of
    // something that we'd like to assert.
    //
    // Exportation could be written in terms of a copy from one KB to another, in which case we can
    // use the destination KB as a way to know what has an has not been asserted yet.  Being able to
    // answer questions like that efficiently and tractably is one of the jobs of a KB
    // implementation, after all.  We could start with something as simple as an HFTStoreMap class
    // paralleling the current TCHStoreMap class.  But the problem is that then we'd need to be
    // writing the HFT file as a side-effect of writes to the KB; otherwise, HFTStoreMap would face
    // the same problem we face here when it came time to save its content to a file.
    //
    // So perhaps some future work should include some kind of a general iterator that would visit
    // everything in the KB exactly one and in an order meeting our needs.  But, because we need
    // this functionality immediately, we'll start off with a naieve implementation that simply
    // keeps a gigantic set of all (composite) entities that we've already asserted.  This
    // implementation can be our baseline and strawman for future innovations.
    ////////////////////////////////////////////////////////////////////////////

    protected Set<Slot> slaveSlots = new HashSet<Slot>();
    protected int totalPEs = 0;
    protected int numPEs = 0;

    /**
     * Maps a primitive entity to the set of Strings based on it that have been exported.  When we
     * have completely exported a primitive entity, we replace that set with doneExporting as an
     * indication that all RTWLocations rooted there have been exported, which saves memory.
     */
    protected Map<String, Set<String>> exportedEntities = new HashMap<String, Set<String>>();
    protected Set<String> doneExporting = new HashSet<String>();

    protected void exportHFT0Entity(PrintStream out, Entity entity, Set<String> exportedLocs) {
        try {
            // Contract this out to exportFHTPrimitive if this is a primitive entity
            if (entity.isPrimitiveEntity()) {
                exportHFT0Primitive(out, entity.toPrimitiveEntity());
                return;
            }

            // If this is a belief, export its query basis, and that will entail exporting all
            // beliefs built off of the query.
            //
            // I suspect we're vulnerable here to pathologies like values in two slots referring to
            // each-other.  Let's tackle those as they come up, and maybe outlaw the miserable ones.
            if (entity.isBelief()) {
                exportHFT0Entity(out, entity.toBelief().getBeliefQuery(), exportedLocs);
                return;
            }

            // If this is a query, first recurse to ensure that the entity whence it is based
            // exists, and then we can hand off to exportHFT0Query.
            log.debug("exportHFT0Entity(" + entity + ")");
            if (entity.isQuery()) {
                Query q = entity.toQuery();
                exportHFT0Entity(out, q.getQueryEntity(), exportedLocs);
                exportHFT0Primitive(out, q.getQuerySlot());
                return;
            }

            throw new RuntimeException("What the heck is this if not a primitive entity, query, or belief?");
        } catch (Exception e) {
            log.info("bkdb: about to throw an exception about an entity of class " + entity.getClass().getName());
            throw new RuntimeException("exportHFT0Entity(<out>, " + entity + ")", e);
        }
    }

    protected int queryDepth = 0;

    /**
     * Here, we know that the entity forming the basis for this query already exists.
     *
     * We also know that we've already been through exportHFT0PrimitiveOnly, meaning that we don't
     * want to assert any generalizations, but that we do want to continue to recurse into them in
     * order to assert any assertions about them.
     */
    protected void exportHFT0Query(PrintStream out, Query q, Set<String> exportedLocs) {
        try { 
            // bkdb: somewhere in or under here is where we'd want to skip non-canonical beliefs
            String qstr = q.toString();
            if (exportedLocs.contains(qstr)) {
                // log.info("Skipping alraedy-emitted query " + qstr);
                return;
            }
            if (queryDepth++ > 200)
                throw new RuntimeException("Nested query recursion too deep at " + qstr);

            // Our assumption is that our query is based off of an entity that already exists.

            // But we have to ensure that the slot we're going to use exists.  Also do its inverse
            // (if any) so that we get the full compliment of slot metadata and make sure that
            // inversing is active before we use the slots.
            Slot querySlot = q.getQuerySlot();
            exportHFT0Entity(out, querySlot, exportedLocs);
            Entity inverse = querySlot.getQuery("inverse").into1Entity();
            if (inverse != null) exportHFT0Entity(out, inverse, exportedLocs);

            boolean isGeneralizations = querySlot.getName().equals("generalizations");

            // Add all values to our KB, recursing into each 
            for (RTWValue v : q.iter()) {
                if (!isGeneralizations) {
                    // Make sure the value exists if it is a composite entity (primitive entities
                    // all already exist thanks to exportHFT0PrimitiveOnly, and this is where that
                    // pays off in terms of pruning our recursion.
                    if (v instanceof Entity && !((Entity)v).isPrimitiveEntity())
                        exportHFT0Entity(out, (Entity)v, exportedLocs);
                    out.println("ab " + q.getBelief(v).toString());
                }

                // Recurse
                Belief b = q.getBelief(v);
                for (Slot s : b.getSlots()) {
                    if (slaveSlots.contains(s)) continue;
                    exportHFT0Query(out, b.getQuery(s), exportedLocs);
                }
            } 

            exportedLocs.add(qstr);

            // And all queries based off this query itself
            for (Slot s : q.getSlots())
                exportHFT0Query(out, q.getQuery(s), exportedLocs);

            queryDepth--;
        } catch (Exception e) { 
            throw new RuntimeException("exportHFT0Query(<out>, " + q + ")", e);
        } 
    }

    protected void exportHFT0Primitive(PrintStream out, PrimitiveEntity pe) {
        try {
            String peName = pe.getName();
            Set<String> exportedLocs = exportedEntities.get(peName);
            if (exportedLocs != null) return;
            exportedLocs = new HashSet<String>();
            exportedEntities.put(peName, exportedLocs);

            // log.debug("exportHFT0Primitive(" + pe + ")");
            
            // Normally, we'd have to assert the generaliztion first here ni order to create this
            // entity, but exportHFT0PrimitiveOnly has already done that for us.

            // Assert all other queries rooted at this entity
            for (Slot s : pe.getSlots()) {
                if (slaveSlots.contains(s)) continue;
                exportHFT0Query(out, pe.getQuery(s), exportedLocs);
            }

            // Now we can mark this entity as being completely done
            exportedLocs = null;
            exportedEntities.put(peName, doneExporting);

            if (++numPEs % 100000 == 0) {
                int percent = (numPEs * 100) / totalPEs;
                int numPartialExports = exportedEntities.size() - numPEs;
                long totalMemory = Runtime.getRuntime().totalMemory() / 1048756;
                long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / 1048756;
                String jvmString = " (JVM Total:" + totalMemory + "MB, Used:" + usedMemory
                        + "MB)";
                log.info("Finished exporting " + (numPEs / 1000) + "k primitive entities (" + percent
                        + "%) with " + numPartialExports
                        + " primitive enties partially exported due to recursion..." + jvmString);
            }

            // Recurse into specializations that we have not yet visited
            for (Entity spec : pe.getQuery("specializations").entityIter()) {
                try {
                    // bkdb: workaround for lack of primitive entity iterator
                    exportHFT0Primitive(out, spec.toPrimitiveEntity());
                } catch (Exception e) {
                    throw new RuntimeException("For specialization " + spec, e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("exportHFT0Primitive(<out>, " + pe + ")", e);
        }
    }

    /**
     * Same as exportHFT0Primitive, but only builds the generalizations hierarchy.  Ideally, spitting
     * out all of the primitive entities beforehand will go a long way in curtailing depth of
     * recursion we will get when we try to follow allong all of the other assertions in the KB.
     * This is especially true of the simplier and more common case of storing slot values that are
     * primitive entities rather than composite entities.
     *
     * Note that we do not here assert anything about the generalizations assertions.
     *
     * Note that we do not at this time ensure that the generlaizations entity itself is asserted
     * first.  It might be aesthetically desirable to do that, but it's a legitimate corner to cut
     * in that any Theo implementation is itself responsible for ensuring the inherent pre-existence
     * of the generalizations entity.
     *
     * Because this is a separate first step, we'll go ahead and use exportedEntities for our own
     * purposes here, on the assumption that it will be cleared and reused by exportHFT0Primitive et
     * al.  They can avoid duplicating any of our effort simply by assuming that all generalizatios
     * have already been emitted.
     *
     * This also counts up the total number of primitive entities into totalPEs.
     */
    protected void exportHFT0PrimitiveOnly(PrintStream out, PrimitiveEntity pe) {
        try {
            String peName = pe.getName();
            Set<String> exportedLocs = exportedEntities.get(peName);
            if (exportedLocs == doneExporting) return;
            if (exportedLocs == null) {
                exportedLocs = new HashSet<String>();
                exportedEntities.put(peName, exportedLocs);
            }

            Query q = pe.getQuery("generalizations");
            for (Entity gen : q.entityIter()) {
                // Make sure our generalization exists first, of course
                exportHFT0PrimitiveOnly(out, gen.toPrimitiveEntity());

                String bstr = q.getBelief(gen).toString();
                if (exportedLocs.add(bstr))
                    out.println("ab " + bstr);
            }
            exportedLocs = null;
            exportedEntities.put(peName, doneExporting);

            if (++totalPEs % 2000000 == 0) {
                log.info("Visited " + (totalPEs / 1000) + "k primitive entities...");
            }

            // Recurse into specializations that we have not yet visited
            for (Entity spec : pe.getQuery("specializations").entityIter()) {
                try {
                    exportHFT0PrimitiveOnly(out, spec.toPrimitiveEntity());
                } catch (Exception e) {
                    throw new RuntimeException("For specialization " + spec, e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("exportHFT0PrimitiveOnly(<out>, " + pe + ")", e);
        }
    }

    protected void exportHFT0BuildSlaveSlots(Slot slot) {
        try {
            if (!slot.getQuery("inverse").isEmpty()
                    && slot.getQuery("masterinverse").into1Value().equals(RTWBooleanValue.FALSE))
                slaveSlots.add(slot);

            // Recurse into specializations that we have not yet visited
            for (Entity spec : slot.getQuery("specializations").entityIter()) {
                try {
                    // bkdb: workaround for lack of slot iterator
                    exportHFT0BuildSlaveSlots(spec.toSlot());
                } catch (Exception e) {
                    throw new RuntimeException("For specialization " + spec, e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("exportHFT0BuildSlaveSlots(" + slot + ")", e);
        }
    }

    /**
     * Export HFT0 recursing along the generalizations hierarchy starting at the primitive entity
     * given by root.<p>
     *
     * TODO: actually this needs to be done more carefully so that only the slots needed are
     * included.<p>
     */
    protected void exportHFT0(PrintStream out, String root) {
        try {
            numPEs = 0;
            totalPEs = 0;
            out.println("HFTBEGIN 0");

            // Build a set of slave slots to not recurse into so that we don't wind up with Belief
            // entities getting canonicalized and suddently hanging off of a different primitive
            // entity etc.
            exportHFT0BuildSlaveSlots(kb.getSlot("slot"));
            log.debug("Detected slave slots: " + slaveSlots);
            
            // First all generalizations.  Then reset and do the full thing.
            log.info("Exporting generalizations hierarchy starting at " + root + "...");
            exportHFT0PrimitiveOnly(out, kb.getPrimitiveEntity("everything"));
            log.info("There are " + totalPEs + " primitive entities in the KB to export.");
            exportedEntities.clear();
            log.info("Exporting everything else...");
            exportHFT0Primitive(out, kb.getPrimitiveEntity(root));
            log.info("Done exporting!");

            out.println("HFTEND");
            out.close();
        } catch (Exception e) {
            throw new RuntimeException("exportHFT0(<out>, \"" + root + "\")", e);
        }
    }

    /**
     * Export HFT0 recursing along the generalizations hierarchy starting at the primitive entity
     * given by root.<p>
     *
     * If the given filenae ends in ".gz" the output will be gzipped.<p>
     *
     * TODO: actually this needs to be done more carefully so that only the slots needed are
     * included.<p>
     */
    protected void exportHFT0(String filename, String root) {
        try {
            // FODO: I guess we should have an AsyncOutputStream for cases like this one
            PrintStream out;
            if (filename.endsWith(".gz")) {
                out = new PrintStream(new GZIPOutputStream(new FileOutputStream(filename)));
            } else {
                out = new PrintStream(new FileOutputStream(filename));
            }
            exportHFT0(out, root);
        } catch (Exception e) {
            throw new RuntimeException("exportHFT0(\"" + filename + "\", \"" + root + "\")", e);
        }
    }

    /**
     * Export the entire KB as HFT0
     */
    public void exportHFT0(PrintStream out) {
        exportHFT0(out, "everything");
    }

    /**
     * Export the entire KB as HFT0<p>
     *
     * If the given filenae ends in ".gz" the output will be gzipped.
     */
    public void exportHFT0(String filename) {
        exportHFT0(filename, "everything");
    }
}
