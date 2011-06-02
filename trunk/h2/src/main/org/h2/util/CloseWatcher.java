/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 * Iso8601:
 * Initial Developer: Robert Rathsack (firstName dot lastName at gmx dot de)
 */
package org.h2.util;

import java.io.Closeable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;

/**
 * A phantom reference to watch for unclosed objects.
 */
public class CloseWatcher extends PhantomReference<Object> {

    /**
     * The queue (might be set to null at any time).
     */
    public static ReferenceQueue<Object> queue = new ReferenceQueue<Object>();

    /**
     * The reference set. Must keep it, otherwise the references are garbage
     * collected first and thus never enqueued.
     */
    public static HashSet<CloseWatcher> refs = New.hashSet();

    /**
     * The stack trace of when the object was created. It is converted to a
     * string early on to avoid classloader problems (a classloader can't be
     * garbage collected if there is a static reference to one of its classes).
     */
    private String openStackTrace;

    /**
     * The closeable object.
     */
    private Closeable closeable;

    public CloseWatcher(Object referent, ReferenceQueue<Object> q, Closeable closeable) {
        super(referent, q);
        this.closeable = closeable;
    }

    /**
     * Check for an collected object.
     *
     * @return the first watcher
     */
    public static CloseWatcher pollUnclosed() {
        ReferenceQueue<Object> q = queue;
        if (q == null) {
            return null;
        }
        while (true) {
            CloseWatcher cw = (CloseWatcher) q.poll();
            if (cw == null) {
                return null;
            }
            if (refs != null) {
                refs.remove(cw);
            }
            if (cw.closeable != null) {
                return cw;
            }
        }
    }

    /**
     * Register an object. Before calling this method, pollUnclosed() should be
     * called in a loop to remove old references.
     *
     * @param o the object
     * @param stackTrace whether the stack trace should be registered (this is
     *            relatively slow)
     */
    public static CloseWatcher register(Object o, Closeable closeable, boolean stackTrace) {
        ReferenceQueue<Object> q = queue;
        if (q == null) {
            q = new ReferenceQueue<Object>();
            queue = q;
        }
        CloseWatcher cw = new CloseWatcher(o, q, closeable);
        if (stackTrace) {
            Exception e = new Exception("Open Stack Trace");
            StringWriter s = new StringWriter();
            e.printStackTrace(new PrintWriter(s));
            cw.openStackTrace = s.toString();
        }
        if (refs == null) {
            refs = New.hashSet();
        }
        refs.add(cw);
        return cw;
    }

    /**
     * Unregister an object, so it is no longer tracked.
     *
     * @param w the reference
     */
    public static void unregister(CloseWatcher w) {
        w.closeable = null;
        refs.remove(w);
    }

    /**
     * Get the open stack trace or null if none.
     *
     * @return the open stack trace
     */
    public String getOpenStackTrace() {
        return openStackTrace;
    }

    public Closeable getCloseable() {
        return closeable;
    }

}
