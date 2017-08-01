package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * {@link Logger} implementation that does nothing at all
 *
 * Using this effectively disables all logging, and is the simplest way to avoid Theo2012 having any
 * dependency on any 3rd party logging libarary (e.g. log4j)
 */
public class NullLogger extends Logger { 
    public void debug(CharSequence message, Throwable t) {
    }

    public void info(CharSequence message, Throwable t) {
    }

    public void warn(CharSequence message, Throwable t) {
    }

    public void error(CharSequence message, Throwable t) {
    }

    public void fatal(CharSequence message, Throwable t) {
    }
}