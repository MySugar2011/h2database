/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import java.lang.ref.WeakReference;
import java.security.AccessControlException;
import java.sql.SQLException;
import org.h2.constant.SysProperties;
import org.h2.engine.Database;
import org.h2.log.LogSystem;
import org.h2.message.Trace;
import org.h2.message.TraceSystem;

/**
 * The writer thread is responsible to flush the transaction log file from time
 * to time.
 */
public class WriterThread implements Runnable {

    /**
     * The reference to the database.
     *
     * Thread objects are not garbage collected
     * until they returned from the run() method
     * (even if they where never started)
     * so if the connection was not closed,
     * the database object cannot get reclaimed
     * by the garbage collector if we use a hard reference.
     */
    private volatile WeakReference<Database> databaseRef;

    private int writeDelay;
    private volatile boolean stop;

    private WriterThread(Database database, int writeDelay) {
        this.databaseRef = new WeakReference<Database>(database);
        this.writeDelay = writeDelay;
    }

    /**
     * Change the write delay
     *
     * @param writeDelay the new write delay
     */
    public void setWriteDelay(int writeDelay) {
        LogSystem log = getLog();
        this.writeDelay = writeDelay;
        // TODO check if MIN_WRITE_DELAY is a good value
        if (writeDelay < SysProperties.MIN_WRITE_DELAY) {
            log.setFlushOnEachCommit(true);
        } else {
            log.setFlushOnEachCommit(false);
        }
    }

    /**
     * Create and start a new writer thread for the given database. If the
     * thread can't be created, this method returns null.
     *
     * @param database the database
     * @param writeDelay the delay
     * @return the writer thread object or null
     */
    public static WriterThread create(Database database, int writeDelay) {
        try {
            WriterThread writer = new WriterThread(database, writeDelay);
            Thread thread = new Thread(writer);
            thread.setName("H2 Log Writer " + database.getShortName());
            thread.setDaemon(true);
            thread.start();
            return writer;
        } catch (AccessControlException e) {
            // // Google App Engine does not allow threads
            return null;
        }
    }

    private LogSystem getLog() {
        Database database = databaseRef.get();
        if (database == null) {
            return null;
        }
        LogSystem log = database.getLog();
        return log;
    }

    public void run() {
        while (!stop) {
            Database database = databaseRef.get();
            if (database == null) {
                break;
            }

            int wait = writeDelay;
            try {
                if (database.isFileLockSerialized()) {
                    wait = SysProperties.MIN_WRITE_DELAY;
                    database.checkpointIfRequired();
                } else {
                    LogSystem log = database.getLog();
                    if (log == null) {
                        break;
                    }
                    log.flush();
                }
            } catch (SQLException e) {
                TraceSystem traceSystem = database.getTraceSystem();
                if (traceSystem != null) {
                    traceSystem.getTrace(Trace.LOG).error("flush", e);
                }
            } catch (RuntimeException e) {
                TraceSystem traceSystem = database.getTraceSystem();
                if (traceSystem != null) {
                    traceSystem.getTrace(Trace.LOG).error("flush", e);
                }
            }

            // TODO log writer: could also flush the dirty cache when there is
            // low activity
            if (wait < SysProperties.MIN_WRITE_DELAY) {
                // wait 0 mean wait forever, which is not what we want
                wait = SysProperties.MIN_WRITE_DELAY;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        databaseRef = null;
    }

    /**
     * Stop the thread. This method is called when closing the database.
     */
    public void stopThread() {
        stop = true;
    }

}
