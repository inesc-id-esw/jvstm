/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm.gc;

import java.lang.reflect.Field;

import java.lang.ref.WeakReference;

import jvstm.ActiveTransactionsRecord;
import static jvstm.UtilUnsafe.UNSAFE;

public class TxContext {
    // --- Setup to use Unsafe
    private static final long nextOffset;
    static {                      // <clinit>
        Field f = null;
        try { f = TxContext.class.getDeclaredField("next"); }
        catch (java.lang.NoSuchFieldException e) { throw new RuntimeException(e); }
        nextOffset = UNSAFE.objectFieldOffset(f);
    }

    /** The oldest version that may be required by transactions running in this tx context is
     * given by the corresponding ActiveTransactionsRecord */
    public volatile ActiveTransactionsRecord oldestRequiredVersion = null;
    /** A thread can only nullify the oldestRequiredVersion when inCommitAndBegin is false.  This is used to support the
     * atomic commitAndBegin operation, in which we need to finish a transaction but not let go of its current
     * transaction record, because we will want to use it later. */
    public boolean inCommitAndBegin = false;
    /** A ref to the next TxContext */
    protected TxContext next = null;
    /*  The slot 'owner' is used to enable garbage collection of this TxContext when it is no longer necessary.  It
     * holds a WeakReference to either a Thread or a Transaction.  In the normal case it is a Thread (because we reuse
     * TxContexts for all transactions that run in the same Thread).  If a running transaction is suspended, we enqueue
     * a TxContext specifically for that transaction, in which case the owner is the suspended transaction (not an
     * instance of SuspendedTransaction, but an instance of Transaction!)
     */
    /** The owner that needs this TxContext. Either a Thread or a Transaction */
    public final WeakReference owner;

    public TxContext(Object owner) {
        this.owner = new WeakReference(owner);
    }

    public TxContext enqueue(TxContext ctx) {
        // System.out.println(Thread.currentThread().getName());
        // new Exception().printStackTrace();
        TxContext current = this;
        while (true) {
            while (current.next != null) {
                current = current.next;
            }

            if (UNSAFE.compareAndSwapObject(current, nextOffset, null, ctx)) {
                return ctx;
            }
        }
    }

    public TxContext next(){
        return next;
    } 
}
