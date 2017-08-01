package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * General way to obtain some appropriate {@link Logger} instance
 *
 * This is meant to factor out the question of which concrete Logger implementation to use so that
 * dependency on log4j (or whatever else) is easily controlled in some global fashion.  The
 * expectation is for Theo2012 code to simply use the {@link getLogger} method in order to achieve
 * the appropriate policy.
 *
 * Initially, this uses log4j if and only if the "log4j.configurationFile" system property is set.
 * If no such property is set, then logging is disabled and there will be no need to have log4j
 * loadable from the classpath.  The important design goal here is that logging be unobstrusive and
 * not require dependency on 3rd party libraries unless doing otherwise is explicitly turned on
 * programatically or by some configureation.  We can get more sophisticated as needed.
 */
public class LogFactory { 
    public static Logger getNullLogger() {
        return new NullLogger();
    }

    public static Logger getConsoleLogger() {
        return new ConsoleLogger();
    }

    public static Logger getL4JLogger() {
        return new L4JLogger();
    }

    public static Logger getLogger() {
        if (System.getProperty("log4j.configurationFile") == null)
            return getNullLogger();
        else
            return getL4JLogger();
    }
}