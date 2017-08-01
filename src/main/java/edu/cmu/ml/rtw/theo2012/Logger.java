package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Wrapper class for Theo2012's logging needs
 *
 * The Theo2012 code originally made considerable use of log4j when it was a part of the monolithic
 * NELL codebase.  Upon factoring Theo2012 out, it became desirable to be able to use Theo2012 in
 * isolation, without the log4j jars being a requiered dependency.  By using this wrapper class, we
 * can optionally use an implementation that does not incur any external dependencies.
 *
 * {@link LogFactory} is the expected general way to obtain a Logger instance.
 */
public abstract class Logger {
    public abstract void debug(CharSequence message, Throwable t);

    public abstract void info(CharSequence message, Throwable t);

    public abstract void warn(CharSequence message, Throwable t);

    public abstract void error(CharSequence message, Throwable t);

    public abstract void fatal(CharSequence message, Throwable t);

    public void debug(CharSequence message) {
        debug(message, null);
    }

    public void info(CharSequence message) {
        info(message, null);
    }

    public void warn(CharSequence message) {
        warn(message, null);
    }

    public void error(CharSequence message) {
        error(message, null);
    }

    public void fatal(CharSequence message) {
        fatal(message, null);
    }
}