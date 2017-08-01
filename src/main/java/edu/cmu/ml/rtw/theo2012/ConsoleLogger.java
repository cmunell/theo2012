package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * {@link Logger} implementation that offers a very basic form of logging to the console
 *
 * This is meant mainly for odd debugging purposes when the use of {@link L4JLogger} isn't possible.
 * The initial implementation is intentionally very basic; the expectation is that we can make this
 * more sophisticated if at some point we wind up needing it.
 */
public class ConsoleLogger extends Logger { 
    public void debug(CharSequence message, Throwable t) {
        // Probably don't want this at all unless we add an option or start logging to a file or
        // whatever.
    }

    public void info(CharSequence message, Throwable t) {
        System.out.println(message);
        if (t != null) System.out.println(t.getMessage());
    }

    public void warn(CharSequence message, Throwable t) {
        System.out.println(message);
        System.err.println(message);
        if (t != null) {
            System.out.println(t.getMessage());
            System.err.println(t.getMessage());
        }
    }

    public void error(CharSequence message, Throwable t) {
        warn(message, t);
    }

    public void fatal(CharSequence message, Throwable t) {
        fatal(message, t);
    }
}