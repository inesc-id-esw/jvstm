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
package jvstm;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jvstm.util.Cons;

public class ActiveTransactionsRecord {

    /*
     * The TxQueueListeners are notified whenever we detect that some
     * old values became unreachable.  Yet, there is no guarantee
     * regarding the order of notification.  The same listener may be
     * notified simultaneously by several threads; so, the listeners
     * must be thread-safe.
     *
     * As there is no synchronization on the notifications, it may
     * happen that a listener is notified of a new oldestTx and only
     * later receives the notification of a prior number.  Yet,
     * whenever it receives a notification, it is guaranteed that
     * there is no active transaction older than the number notified.
     */

    private final static AtomicReference<Cons<TxQueueListener>> listeners =
        new AtomicReference<Cons<TxQueueListener>>(Cons.<TxQueueListener>empty());

    public static void addListener(TxQueueListener listener) {
        while (true) {
            Cons<TxQueueListener> old = listeners.get();
            if (listeners.compareAndSet(old, old.cons(listener))) {
                return;
            }
        }
    }

    public static void removeListener(TxQueueListener listener) {
        while (true) {
            Cons<TxQueueListener> old = listeners.get();
            if (listeners.compareAndSet(old, old.removeFirst(listener))) {
                return;
            }
        }
    }

    protected static void notifyListeners(int newOldest) {
        for (TxQueueListener l : listeners.get()) {
            try {
                l.noteOldestTransaction(newOldest);
            } catch (Throwable t) {
                t.printStackTrace();
                // don't throw error, or else it would kill the
                // current thread because of an error in the client
                // listener
            }
        }
    }


    public Transaction tx;

    /*
     * The ActiveTransactionsRecord class was designed as a lock-free data structure.  Its goal is
     * twofold: 1) to maintain a record of active transactions for the purpose of garbage collecting
     * old values; 2) to hold transaction's write-sets to enable future transaction's to validate
     * their read state.
     *
     * It is composed of the following fields:
     */

    // the transactionNumber is assigned when the record is created and never changes thereafter its
    // value corresponds to the number of a write-transaction that commits this record (an instance
    // of this class) is used to record all the running transactions that have this transaction
    // number, and that, thus, may need to access the values committed by the transaction that
    // created this record
    public final int transactionNumber;

    /* This is the write-set of the transaction that created this record.
     *
     * This slot is not final, because it is set to null, in clean().  This is safe, because then,
     * no other transaction will ever need to read it.  Setting this slot to null helps (a lot!) the
     * Java GC.
     */
    protected WriteSet writeSet;

    // the next field indicates a more recent record (the one that was created immediately after
    // this one).  This field's AtomicReference starts as null and is assigned only once through a
    // compare-and-set.  The transaction that succeeds in the CAS operation will be the next one to
    // win the race for the commit position.
    private final AtomicReference<ActiveTransactionsRecord> next =
        new AtomicReference<ActiveTransactionsRecord>(null);

    /* This variable needs to be volatile because the beginning of a transaction and the GC
     * algorithm triggered by the finish of another transaction, both test this variable.
     */
    private volatile boolean recordCommitted = false;

    /*
     * The next field creates, in effect, a linked list of records,
     * with older records pointing to newer records.  This linked-list
     * of records will allow us to move forward in the cleaning
     * process necessary to allow the garbage collection of
     * unreachable old-values.
     *
     * Cheking the commit state of a record is important, because records are enqueued as soon as a
     * transaction obtains its commit position.  However, that record should only be "seen" by new
     * transactions after it has been committed.  Given that records are committed "in order", when
     * we see an uncommitted record, we know that we should behave as if the remainder of the queue
     * didn't exist yet.
     *

    /* ADDITIONAL EXPLANATION FOR THIS CLASS
     *
     * Initially, this class was only used in the process of maintaining references to committed
     * versions of box's bodies and to clean them when they were no longer necessary.  Records were
     * created only after a successful commit operation.  We realized that, in effect, this class
     * implemented a queue of committed transactions.  We extended this behaviour to include
     * transactions that are in the middle of the commit process.  Instances of this class are
     * connected as the following example shows:
     *
     *
     *              mostRecentCommittedRecord
     *                       |
     *                       v
     *    +---------+    +---------+    +---------+    +---------+    +---------+
     *    | #9      |    | #10     |    | #11     |    | #12     |    | #13     |
     *    |         |    |         |    |         |    |         |    |         |
     *    |COMMITTED|    |COMMITTED|    | ACTIVE  |    | ACTIVE  |    | ACTIVE  |
     *    |         |    |         |    |         |    |         |    |         |
     *    |    next |--> |    next |--> |    next |--> |    next |--> |    next |-.
     *    +---------+    +---------+    +---------+    +---------+    +---------+
     *      ^                                              ^
     *      |                                              |
     *    activeTxRecord                             commitTxRecord
     *
     * Starting from the beginning of the queue and up to a certain point, this list will contain
     * only committed transaction records.  From that point on, any record will be in an uncommitted
     * state.  Records are enqueued (through a compare-and-set operation) after "their" transaction
     * is validated, thus ensuring a position in the list of successful commits to come.  However,
     * until that transaction actually commits, this record cannot be seen by newly starting
     * transactions.
     *
     * The static slot mostRecentCommittedRecord of the Transaction class will always point to a
     * record that represents a valid committed state.
     *
     * For each running transaction:
     *
     * - the activeTxRecord must point to some valid committed tx record.  This is the version of
     * the world that a transaction will see during its execution.
     *
     * - the commitTxRecord points to an ActiveTransactionsRecord that is created in the beginning
     * of the commit process.  This record will eventually be set to committed after the
     * transaction's commit operation completes.
     *
     * This structure enables the validation process to occur for any given transaction that is
     * trying to commit, because such transaction can access the write-sets of all the transactions
     * that occur(ed) since its beginning until its commit.
     *
     * Another advantage is that when a transaction tries to commit, if its activeTxRecord is the
     * last in line (next is null), then the transaction knows that nothing as changed and can
     * immediately commit without the need to validate.
     */


    // private method to be used only when instantiating the sentinel record
    private ActiveTransactionsRecord() {
        this.transactionNumber = 1;
        this.writeSet = WriteSet.empty();
        this.recordCommitted = true;
        this.tx = new TopLevelReadTransaction(this);
    }
    private static boolean sentinelRecordCreated = false;
    protected static synchronized ActiveTransactionsRecord makeSentinelRecord() {
        if (sentinelRecordCreated) {
            throw new Error("ActiveTransactionsRecord::makeSentinelRecord() invoked more than once!");
        }
        sentinelRecordCreated = true;
        return new ActiveTransactionsRecord();
    }

    public ActiveTransactionsRecord(int txNumber, WriteSet writeSet) {
        this.transactionNumber = txNumber;
        this.writeSet = writeSet;
        this.tx = new TopLevelReadTransaction(this);
    }

    public ActiveTransactionsRecord getNext() {
        return next.get();
    }

    /**
     * Sets the next record iff there was no next record already set
     *
     * @return <code>true</code> if the next was successfully set. <code>false</code> if there was
     * another record already set as next
     */
    public boolean trySetNext(ActiveTransactionsRecord next) {
        return this.next.compareAndSet(null, next);
    }

    protected void setCommitted() {
        this.recordCommitted = true;
    }

    public boolean isCommitted() {
        return this.recordCommitted;
    }

    public WriteSet getWriteSet() {
        return this.writeSet;
    }

    public void clean() {
        int nBlocks = this.writeSet.normalWriteSet.nBlocks;
        int blockIdx = 0, idx = 0;
        for (Cons<GarbageCollectable> bodiesPerBlock : this.writeSet.normalWriteSet.bodiesPerBlock) {
            for (GarbageCollectable body : bodiesPerBlock) {
                body.clearPrevious();
                if(REVERSION && blockIdx < nBlocks){
                    VBox vbox = this.writeSet.normalWriteSet.allWrittenVBoxes[idx];
                    tryRevert(vbox, body);
                    idx++;
                }
            }
            blockIdx++;
        }

        nBlocks = this.writeSet.perTxBoxesWriteSet.nBlocks;
        blockIdx = 0;
        idx = 0;
        for (Cons<GarbageCollectable> bodiesPerBlock : this.writeSet.perTxBoxesWriteSet.bodiesPerBlock) {
            for (GarbageCollectable body : bodiesPerBlock) {
                body.clearPrevious();
                if(REVERSION && blockIdx < nBlocks){
                    VBox vbox = this.writeSet.perTxBoxesWriteSet.allWrittenVBoxes[idx];
                    tryRevert(vbox, body);
                    idx++;
                }
            }
            blockIdx++;
        }
        writeSet = null; // this is helpful for the GC. verified by experimentation

        notifyListeners(transactionNumber);
    }

    /*===========================================================================*
     *~~~~~~~~~~~~~     REVERSION part of the AOM approach  ~~~~~~~~~~~~~~~~~~~~~*
     *===========================================================================*/

    static final String REVERSION_PROP = "jvstm.aom.reversion";
    static final boolean REVERSION;
    // public static int nrOfCleans = 0;
    public static int nrOfReversions = 0;
    public static int nrOfTries = 0;

    static{
        Logger logger = Logger.getLogger("jvstm");
        REVERSION = Boolean.getBoolean(REVERSION_PROP);
        logger.info(String.format(
                "********** AOM reversion = %b (disable/enable it in property %s)",
                REVERSION,
                REVERSION_PROP));
    }

    /**
     *  If vbodies' history only have one VBoxBody then we will try to revert it.
     *
     *  The reversion process first acquires the object’s monitor and then executes the following
     *  3 steps (that are marked in the code bellow):
     *
     *  - (1) read the head of the object’s history and check that it is the VBoxBody that was
     *  just trimmed (the body argument), executing the following two steps only if it is;
     *
     *  - (2) copy the values of the last committed body to the corresponding fields in the object.
     *  This copy is performed by the toCompactLayout method.
     *
     *  - (3) do an atomic compare and swap to change the value of the object’s header from its
     *  previously read value to null.
     *
     *  To understand why our current approach is correct, note that when we revert an object,
     *  we do not change the instances of VBoxBody that represent the object’s history; we
     *  simply set the body field of the reverted object to null.
     *  Moreover, the old object’s history is still valid, in the sense that it represents
     *  correct information about the committed values for the object; it just happens
     *  that it is not needed anymore.
     *
     *  Correctness of the Reversion:
     *
     *  Imagine that one thread Tw is at commit phase and writing back to an object concurrently
     *  with another thread Tr that is trying to revert it.
     *  In this scenario consider the following interleaves:
     *
     *  - Tw -> Tr(1):
     *
     *  If Tw finishes all its steps before Tr reads the head of the object’s history, then Tr
     *  will refuse to revert the object because it will see in the head of the history a different body.
     *
     *  - Tr(3) -> Tw:
     *
     *  If Tr is able to run to completion before Tw reads the history’s head, then Tw will see
     *  the object in the compact layout and will extend it as usual (See VBoxAom::commit method).
     *
     *  - Tw -> Tr(2/3):
     *
     *  If Tr sees the expected value at the history’s head, and Tw sees that same value, because
     *  it reads it before Tr sets it to null. In this case, Tw decides that it does not
     *  need to extend the object (because the object is already in the extended layout)
     *  and simply adds another version at the head of the current history and sets the
     *  corresponding body field in the vbox object; this will be done independently of what Tr does.
     *  If Tw finishes first, the CAS of Tr will fail (step 3), leaving the object in the correct state
     *  (in the extended layout with the new value at the beginning of its history - the value written by Tw).
     *
     *  - Tw(1) -> Tr -> T2(2)
     *
     *  If both Tr and Tw both see the object extended and Tr sees the expected value at the history’s
     *  head and finishes before Tw, then Tr successfully converts the object to the compact layout and
     *  after that the Tw overwrites the body field with the new history, effectively turning the object
     *  into the extended layout again.
     *
     */
    private <T extends VBox<T>> boolean tryRevert(VBox<T> vbox, GarbageCollectable body){
        synchronized (vbox){
            if(vbox.body == body /* (1) step one of the reversion */
                    // && (Transaction.mostRecentRecord.transactionNumber - body.version) >= 8
                    // && (currentOwner.version != 0 && currentOwner.version <= this.transactionNumber)
            ){
                nrOfTries++;
                vbox.toCompactLayout(((VBoxBody<T>)body).value); /* (2) step two of the reversion */
                boolean res = UtilUnsafe.UNSAFE.compareAndSwapObject(vbox, VBox.Offsets.bodyOffset, body, null); /* (3) step three of the reversion */
                if(res) nrOfReversions++;
                return res;
            }
        }
        return false;
    }
}
