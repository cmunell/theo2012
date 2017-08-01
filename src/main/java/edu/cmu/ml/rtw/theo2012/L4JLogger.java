package edu.cmu.ml.rtw.theo2012;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * Main original {@link Logger} implementation that forwards logging to log4j
 */
public class L4JLogger extends edu.cmu.ml.rtw.theo2012.Logger { 
    protected final Logger underlyingLogger;
    protected final ExtendedLoggerWrapper logger;
    protected final String fqcn;

    public L4JLogger() {
        underlyingLogger = LogManager.getLogger();
        logger = new ExtendedLoggerWrapper((ExtendedLogger)underlyingLogger,
                underlyingLogger.getName(), underlyingLogger.getMessageFactory());
        fqcn = getClass().getName();
    }

    public void debug(CharSequence message, Throwable t) {
        logger.logMessage(fqcn, Level.DEBUG, null, new SimpleMessage(message), t);
    }

    public void info(CharSequence message, Throwable t) {
        logger.logMessage(fqcn, Level.INFO, null, new SimpleMessage(message), t);
    }

    public void warn(CharSequence message, Throwable t) {
        logger.logMessage(fqcn, Level.WARN, null, new SimpleMessage(message), t);
    }

    public void error(CharSequence message, Throwable t) {
        logger.logMessage(fqcn, Level.ERROR, null, new SimpleMessage(message), t);
    }

    public void fatal(CharSequence message, Throwable t) {
        logger.logMessage(fqcn, Level.FATAL, null, new SimpleMessage(message), t);
    }
}