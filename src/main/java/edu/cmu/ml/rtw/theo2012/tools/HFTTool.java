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

import gnu.getopt.*;

import edu.cmu.ml.rtw.util.Logger;
import edu.cmu.ml.rtw.util.LogFactory;
import edu.cmu.ml.rtw.util.Timer;

import edu.cmu.ml.rtw.theo2012.core.*;
import edu.cmu.ml.rtw.theo2012.util.HFTUtil;

/**
 * Collection of HFT-related commandline functionalities, like importing from and exporting to.
 */
public class HFTTool {
    private final static Logger log = LogFactory.getLogger();

    protected void importHFT(String hftFile, String kbLocation, String defaultFormat) {
        try {
            // Special case for testing N4JTheo0 outside of a Theo1 wrapper
            Theo0 kb;
            if (defaultFormat != null && defaultFormat.equals("n4j0")) {
                //bkdb is't this done with TheoFactory now? kb = new N4JTheo0(kbLocation, false, true);
                kb = null;
            } else {
                kb = TheoFactory.openTheo1(kbLocation, false, true, defaultFormat);
            }

            HFTUtil hftUtil = new HFTUtil(kb);
            hftUtil.importHFT0(hftFile);

            kb.close();
        } catch (Exception e) {
            throw new RuntimeException("importHFT(" + hftFile + ", " + kbLocation + ", "
                    + defaultFormat + ")", e);
        }
    }

    protected void exportHFT(String hftFile, String kbLocation, String defaultFormat) {
        try {
            // Special case for testing N4JTheo0 outside of a Theo1 wrapper
            Theo0 kb;
            if (defaultFormat != null && defaultFormat.equals("n4j0")) {
                // bkdb isn't this done with TheoFactory now? kb = new N4JTheo0(kbLocation, false, true);
                kb = null;
            } else {
                kb = TheoFactory.openTheo1(kbLocation, true, false, defaultFormat);
            }
            
            HFTUtil hftUtil = new HFTUtil(kb);
            hftUtil.exportHFT0(hftFile);

            kb.close();
        } catch (Exception e) {
            throw new RuntimeException("exportHFT(" + hftFile + ", " + kbLocation + ")", e);
        }
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
                "Commandline access to things to do with sentences                               \n" +
                "                                                                                \n" +
                "Usage:                                                                          \n" +
                " " + progname + " [--kbformat=<kbformat>] <cmd> <hftfile> <kbloc>               \n" +
                "                                                                                \n" +
                "where                                                                           \n" +
                " --kbformat     Sets the format for new KB files, overriding whatever default is\n" +
                "                  in the properties files.  One of:                             \n" +
                "                  mdb      MapDB format.                                        \n" +
                "                  tch      Traditional Tokyo Cabinet format.                    \n" +
                "                  n4j      Neo4J format.                                        \n" +
                "                  n4j0     Neo4J format opened as Theo0 rather than Theo1.      \n" +
                "                                                                                \n" +
                " <cmd>          Selects command to run.  One of:                                \n" +
                "                  import: Reads <hftfile> and \"executes\" it into <kbfile>.      \n" +
                "                                                                                \n" +
                "                  export: Read <kbfile> and creates / overwrites <hftfile> with \n" +
                "                    a new HFT file that will recreate the KB if executed into a \n" +
                "                    new, empty KB.  The choice of how to construct this HFT file\n" +
                "                    will be some sort of \"best practice\".  As of this writing,  \n" +
                "                    HFT0 is the only HFT format that exists, and only one kind  \n" +
                "                    of \"command\" is defined, so there aren't any choices to be  \n" +
                "                    made yet.                                                   \n" +
                "                                                                                \n" +
                " <hftfile>      HFT file to use.  If this is an output file, then it will be    \n" +
                "                  created, overwriting any existing file.                       \n" +
                " <kbfile>       Location of a Theo2012-compatable KB to use.  If this is used as\n" + 
                "                  an output, then existing content will NOT be deleted first.   \n" +
                "                  If the KB does not exist, then a new KB will be created with  \n" +
                "                  whatever minimal content KBs must have.  Default choice of    \n" +
                "                  format for new KBs is controlled by some combination of       \n" +
                "                  properties files and the given location, and may be set       \n" +
                "                  explicitly by the --kbformat flag.                            \n" +
                "                                                                                \n" +
                "If the HFT file ends in \".gz\", it will automatically be gunzipped during an     \n" +
                "  import, and automatically gzipped during an export.                           \n" +
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
        String cmd = null;
        String hftFile = null;
        String kbLocation = null;
        String kbFormat = null;

        // Parse command line options
        StringBuffer longbuffer = new StringBuffer();
        LongOpt[] longopts = new LongOpt[2];
        longopts[0] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
        longopts[1] = new LongOpt("kbformat", LongOpt.REQUIRED_ARGUMENT, longbuffer, 1);
        Getopt g = new Getopt(progname, args, "h", longopts);
        int c;
        while ((c = g.getopt()) != -1)
            switch (c) {
            case 'h':
                Usage(progname);
                System.exit(0);

            case 0:
                // Our universal long option indicator.  Check longbuffer to find out which one --
                // it will be the string version of 4th argument in the LongOpt constructor.
                if (longbuffer.toString().equals("1")) {
                    kbFormat = g.getOptarg();
                } else {
                    throw new RuntimeException("Internal Error: Unrecognized LongOpt id '"
                            + longbuffer + "' from getopt");
                }
                break;

            case '?':
                // Parsing error about which g has already complained.
                System.exit(1);

            default:
                throw new RuntimeException("Internal Error: Unforseen return '" + (char)c
                        + "' from getopt");
            }

            
        // Eat up the rest of the command line
        for (int i = g.getOptind(); i < args.length ; i++) {
            String arg = args[i];
            if (cmd == null) {
                cmd = arg;
            } else if (hftFile == null) {
                hftFile = arg;
            } else if (kbLocation == null) {
                kbLocation = arg;
            } else {
                System.err.println("Too many commandline arguments.  Use --help for help.");
                System.exit(1);
            }
        }
        if (kbLocation == null) {
            Usage(progname);
            System.exit(1);
        }
        
        // We'll let the handler for each command worry about opening files and suchlike.  We can
        // refactor some of that back here if/when there are more commands with more commonality.
        if (cmd.equals("import")) {
            importHFT(hftFile, kbLocation, kbFormat);
        } else if (cmd.equals("export")) {
            exportHFT(hftFile, kbLocation, kbFormat);
        } else {
            throw new RuntimeException("Unrecognized command \"" + cmd + "\"");
        }
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
