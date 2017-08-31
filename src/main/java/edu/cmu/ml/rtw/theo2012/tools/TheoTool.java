package edu.cmu.ml.rtw.theo2012.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Timer;

import edu.cmu.ml.rtw.theo2012.core.*;

/**
 * Simple CLI access to common Theo2 API and some handy additional utilties.
 */
public class TheoTool {
    private final static Logger log = LogFactory.getLogger();

    protected void pre(PrintStream out, Entity e) {
        try {
            RTWLocation l = e.getRTWLocation();
            String indent = "";
            for (int i = 0; i < l.size(); i++) indent = indent + "  ";

            for (Slot slot : e.getSlots()) {
                Query q = e.getQuery(slot);
                out.print(indent + slot + ": ");
                out.print(q.valueDump());
                out.print("\n");

                for (RTWValue v : e.getQuery(slot).iter()) {
                    Entity sube = e.getBelief(slot, v);
                    if (!sube.getSlots().isEmpty()) {
                        out.print(indent + "  =" + v + "\n");
                        pre(out, sube);
                    }
                }

                if (!q.getSlots().isEmpty())
                    pre(out, q);
            }
        } catch (Exception ex) {
            throw new RuntimeException("pre(<out>, " + e + ")", ex);
        }
    }

    protected void pre(Entity e) {
        try {
            PrintStream out = System.out;
            String indent = "";
            RTWLocation l = e.getRTWLocation();
            for (int i = 0; i < l.size(); i++) indent = indent + "  ";
            out.print(l + ":\n");
            pre(out, e);
            out.print("\n");
        } catch (Exception ex) {
            throw new RuntimeException("pre(" + e + ")", ex);
        }
    }

    // bkdb: do these belong in an abstract base or a utility class or what?  Seems similar to e.g. writedot and other such questions probably littered about
    protected void prer(Entity e) {
        try {
            pre(e);
            for (Entity s : e.getQuery("specializations").entityIter())
                prer(s);
        } catch (Exception ex) {
            throw new RuntimeException("prer(" + e + ")", ex);
        }
    }

    protected void ents(PrintStream out, String indent, Entity e) {
        if (e.isPrimitiveEntity()) {
            out.print(indent + e.toPrimitiveEntity().getName());
            indent = indent + "  ";
            for (Entity s : e.getQuery("specializations").entityIter())
                ents(out, indent, e);
        }
    }

    protected void ents(Entity e) {
        ents(System.out, "", e);
    }

    ////////////////////////////////////////////////////////////////////////////
    // QUALITY MANBASKETS
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Prints usage statement
     *
     * @param progname The name of this program
     */
    public void Usage(String progname) {
        // No multiline strings in Java D:
        //
        // And now this is going to get eaten for lunch by somebody's code formatter because the
        // strings don't start before column 20, meaning that they extend beyond column 100 in order
        // to be the "standard" 80 column width.  It's like punchcards and FORTRAN all over again!
        //
        System.out.print(
                "Simple commandline access to some simple Theo2 API bits and some handy other    \n" +
                " actions.                                                                       \n" +
                "                                                                                \n" +
                "Usage:                                                                          \n" +
                " " + progname + " [--kbformat=<kbformat>]                                       \n" +
                "    <kbloc> <cmd> [<arg1> [<arg2> [<arg3>]]]                                    \n" +
                "                                                                                \n" +
                "where                                                                           \n" +
                " --kbformat     Sets the format for new KB files, overriding whatever default is\n" +
                "                  in the properties files.  One of:                             \n" +
                "                  mdb      MapDB format.                                        \n" +
                "                  tch      Traditional Tokyo Cabinet format.                    \n" +
                "                  n4j      Neo4J format.                                        \n" +
                "                  n4j0     Neo4J format opened as Theo0 rather than Theo1.      \n" +
                "                                                                                \n" +
                " <kbfile>       Location of a Theo2012-compatable KB to use.  If this is used as\n" + 
                "                  an output, then existing content will NOT be deleted first.   \n" +
                "                  If the KB does not exist, then a new KB will be created with  \n" +
                "                  whatever minimal content KBs must have.  Default choice of    \n" +
                "                  format for new KBs is controlled by some combination of       \n" +
                "                  properties files and the given location, and may be set       \n" +
                "                  explicitly by the --kbformat flag.                            \n" +
                " <cmd>          Selects command to run.  One of:                                \n" +
                "                  getQuery: Display content of slot <arg2> of entity <arg1>     \n" +
                "                                                                                \n" +
                "                  getNumValues: Display number of values in slot <arg2> of      \n" +
                "                    entity <arg1>                                               \n" +
                "                                                                                \n" +
                "                  getReferringValues: Display beliefs where entity <arg1> is a  \n" +
                "                    value in any slot <arg2>                                    \n" +
                "                                                                                \n" +
                "                  getNumReferringValues: Display number of beliefs where entity \n" +
                "                    <arg1> is a value in any slot <arg2>                        \n" +
                "                                                                                \n" +
                "                  entityExists: Display whether or the entity <arg1> exists in  \n" +
                "                    the KB.  If <arg2> is supplied, it is taken to be a slot    \n" +
                "                    attached to <arg1>                                          \n" +
                "                                                                                \n" +
                "                  getSlots: Display all slots attached to the entity <arg1>     \n" +
                "                                                                                \n" +
                "                  addValue: Add value <arg3> to the slot <arg2> attached to the \n" +
                "                    entity <arg1>                                               \n" +
                "                                                                                \n" +
                "                  deleteValue: Delete value <arg3> from the slot <arg2> attached\n" +
                "                    to the entity <arg1>                                        \n" +
                "                                                                                \n" +
                "                  pre: Print the entity <arg1> and all other assertions attached\n" +
                "                                                                                \n" +
                "                  prer: Same as pre, but also recursively print any entities to \n" +
                "                    which <arg1> specializes (useful only if <arg1> is a        \n" +
                "                    primitive entity)                                           \n" +
                "                                                                                \n" +
                "                  ents: Prints <arg1> and the recursively do the same for any   \n" +
                "                    primitive entities to which it specializes.  In other words,\n" +
                "                    just the hierarchy of primitive entities, and not any slots \n" +
                "                    or values                                                   \n" +
                "                                                                                \n" +
                " <arg1/2/3>       The entities that act as arguments to the given commands.  In \n" +
                "                    general, these may be composite entities, not just primitive\n" +
                "                    entities.  Extra arguments will be silently ignored.        \n" +
                ""
                );
    }

    /**
     * The real main (as a nonstatic member)
     *
     * @param args The commandline arguments as-is
     */
    protected void run(String[] args) throws Exception {
        String progname =
                getClass().getName().substring(getClass().getName().lastIndexOf('.')+1);
        String kbLocation = null;
        String cmd = null;
        String arg1 = null;
        String arg2 = null;
        String arg3 = null;
        List<String> options = new ArrayList<String>();

        // Parse command line options (quick and dirty to avoid adding more 3rd-party dependencies)
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.equals("-h") || arg.equals("--help")) {
                    Usage(progname);
                    System.exit(0);
                }
                options.add(arg);
            } else if (kbLocation == null) {
                kbLocation = arg;
            } else if (cmd == null) {
                cmd = arg;
            } else if (arg1 == null) {
                arg1 = arg;
            } else if (arg2 == null) {
                arg2 = arg;
            } else if (arg3 == null) {
                arg3 = arg;
            } else {
                System.err.println("Too many commandline arguments.  Use --help for help.");
                System.exit(1);
            }
        }
        if (arg1 == null) {
            Usage(progname);
            System.exit(1);
        }

        boolean readOnly = true;
        if (cmd.equals("addValue") || cmd.equals("deleteValue")) readOnly = false;
        Theo2 theo = TheoFactory.open(kbLocation, readOnly, !readOnly, options);

        // bkdb: Make this not ugly after we have whiteboard Theo expressions worked out
        RTWLocation loc = StoreInverselessTheo1.parseLocationArgument(arg1);
        log.debug("For location " + loc);
        Entity e = theo.get(loc);

        if (cmd.equals("getQuery")) {
            System.out.println(e.getQuery(arg2).valueDump());
        } else if (cmd.equals("getNumValues")) {
            System.out.println(e.getNumValues(arg2));
        } else if (cmd.equals("getReferringValues")) {
            System.out.println(e.getReferringValues(arg2).valueDump());
        } else if (cmd.equals("getNumReferringValues")) {
            System.out.println(e.getNumReferringValues(arg2));
        } else if (cmd.equals("entityExists")) {
            if (arg2 != null) {
                System.out.println(e.entityExists(arg2));
            } else {
                System.out.println(e.entityExists());
            }
        } else if (cmd.equals("getSlots")) {
            System.out.println(e.getSlots());
        } else if (cmd.equals("addValue")) {
            RTWValue value = StoreInverselessTheo1.parseValueArgument(arg3);
            System.out.println(e.addValue(arg2, value));
            System.out.println(e.getQuery(arg2).valueDump());
        } else if (cmd.equals("deleteValue")) {
            RTWValue value = StoreInverselessTheo1.parseValueArgument(arg3);
            e.deleteValue(arg2, value);
            System.out.println(e.getQuery(arg2).valueDump());
        } else if (cmd.equals("pre")) {
            pre(e);
        } else if (cmd.equals("prer")) {
            prer(e);
        } else if (cmd.equals("ents")) {
            ents(e);
        } else {
            System.out.println("Unrecognized command");
        }

        theo.close();
    }
                
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        try {
            HFTTool me = new HFTTool();
            me.run(args);
        } catch (Exception e) {
            log.fatal("Uncaught exception", e);
            System.exit(2);
        }
    }
}
