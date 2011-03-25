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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // the clean field indicates whether this record has been clean or not.  It is atomically set
    // from false to true only once.  The thread that accomplishes this transition will be in charge
    // of cleaning the record.  A true value in this field also indicates that no transactions older
    // than this record exist
    private final AtomicBoolean clean;

    /* This is the write-set of the transaction that created this record.
     *
     * This slot is not final, because it is set to null, in clean().  This is safe, because then,
     * no other transaction will ever need to read it.  Setting this slot to null helps (a lot!) the
     * Java GC.
     */
    protected WriteSet writeSet;

    // the running field indicates how many transactions with 
    // this record's transactionNumber are still running
    private final AtomicInteger running = new AtomicInteger(0);

    // the next field indicates a more recent record (the one that was created immediately after
    // this one).  This field's AtomicReference starts as null and is assigned only once through a
    // compare-and-set.  The transaction that succeeds in the CAS operation will be the next one to
    // win the race for the commit position.
    private final AtomicReference<ActiveTransactionsRecord> next =
	new AtomicReference<ActiveTransactionsRecord>(null);

    /* This variable needs to be volatile for two reasons:
     *
     * - code in TopLevelTransaction uses the double-check locking pattern
     *
     * - the beginning of a transaction and the GC algorithm triggered by the finish of another
     *   transaction (maybeCleanSuc), both test this variable.
     */
    private volatile boolean recordCommitted = false;

    /*
     * The next field creates, in effect, a linked list of records,
     * with older records pointing to newer records.  This linked-list
     * of records will allow us to move forward in the cleaning
     * process necessary to allow the garbage collection of
     * unreachable old-values.
     *
     * The thread that is responsible for discarding the old-values made obsolete by transaction
     * number N (these values are kept in the bodiesPerBlock field of the writeSet for record with
     * number N) is the thread of the last transaction to finish that has a number less than N,
     * provided that no new transaction may start with a number less than N.
     *
     * We must be sure, however, that we do not clean-up while there
     * are still old transactions running, and that we do not allow
     * new transactions to start with an old number that may be
     * inactive already.
     *
     * The algorithms in this class ensure that in a lock-free way.
     * Let's see how...
     *
     * The fundamental fields used in these lock-free algorithms are
     * the fields "running", "next", and recordCommitted.  The access to these fields
     * must be done in a special order to ensure the correction of the
     * algorithms.
     *
     * But, before we look deeper into how these fields are accessed,
     * we must understand when is a record updated...
     *
     * A record may be changed only in one of three situations:
     *
     *   1. A new top-level transaction starts
     *   2. A top-level write-transaction commits [1]
     *   3. A top-level transaction finishes (either after commit, or on abort)
     *
     * The cases 1 and 3 seem simple: when a transaction starts, we
     * must increment the "running" counter, and when a transaction
     * finishes, we must decrement that same counter.
     *
     * Case 2 corresponds to the creation of a new record and linking it to the most recent one, via
     * the "next" field of this latter record.
     *
     * Finally, we must clean-up the records.  When can we do it?  The
     * idea is that a record may be cleaned-up when we are sure that
     * there are no running transactions older than the
     * to-be-cleaned-up record transactionNumber.
     *
     * This is where the "running" field enters into play.  When it
     * reaches 0 for a record with number N, we know that there are no
     * transactions with number N running.  But that does not mean
     * necessarily that there are no transactions with a number less
     * than N running, nor that no future transactions may start with
     * the number N.
     *
     * To be able to clean-up records safely, we enforce the following
     * invariants:
     *
     *   1. a record with number N is cleaned-up only when no
     *      transaction with a number less than N is running
     *
     *   2. no new transaction may start for a record that already has
     *      a non-null value in its "next" field AS LONG AS that next record is committed [1]
     *
     *   3. a record is cleaned-up when the last running transaction of
     *      one of its predecessors finishes
     *
     * [1] Cheking the commit state of a record is important, because records are enqueued as soon
     * as a transaction obtains its commit position.  However, that record should only be "seen" by
     * new transactions after it has been committed.  Given that records are committed "in order",
     * when we see an uncommitted record, we know that we should behave as if the remainder of the
     * queue didn't exist yet.
     *
     * A record may clean-up its successor when it has a non-null next value, the next record is
     * committed, its (current record) running counter is 0, and it is clean already (meaning that
     * there are no previous running transactions).
     *
     * So, in principle, whenever one of these conditions may change,
     * we should check whether we can clean-up the successor.
     *
     * In fact, we may simplify the cases that we need to consider.
     * The "next" field is set only during the commit of a write
     * transaction, when it creates a new record.  Yet, this
     * committing transaction is still running, and it has a number
     * that is either equal to the record that will see its next field
     * changed (in which case, that record must have a running value
     * greater than 0), or it belongs to an older record (in which
     * case, the record with the next field updated cannot be clean
     * yet).  So, we do not need to check whether a record may be
     * cleaned-up when its "next" field is updated.
     *
     * This leaves us with the two remaining cases.
     * 
     * Yet, because the operations that may change any of these fields
     * execute concurrently, we must be careful on how we update and
     * check the fields of a record.
     *
     * When checking whether a record may be clean, we *MUST* check first that its "next" field is
     * non-null and its "next" record is committed.  If that is true, then, because of the invariant
     * 2 stated above, we know that no new transactions may start at this level.  So, if the value
     * of running is 0 and the record is itself clean, we may clean the successor.  If any of these
     * two latter conditions fail, then we do nothing, and whatever changes that must check again.
     *
     * When a new transaction starts, it needs to increment the appropriate running counter.  It
     * starts with the mostRecentCommittedRecord and speculatively increments its running counter.
     * By doing so, we prevent this record to be cleaned-up if it has not been already, thereby
     * avoiding problems with a possible data-race on the updating of the various fields of the
     * record.  Then, we must check whether the "next" field is non-null and its "next" record is
     * committed.  If true, we must back-off of our speculative increment by decrementing the
     * counter again (which may trigger the cleaning-up process, because next is non-null), and try
     * again with the next record.  This algorithm may cause starvation on a thread that is trying
     * to start a new transaction, but only if successive write-transactions commit in between.
     * Thus, this algorithm is lock-free, rather than wait-free.
     *
     * The final piece of all this is that when we clean-up a record, we should try to propagate the
     * cleaning process to its successor.  This propagation is accomplished by the method
     * maybeCleanSuc that calls the clean method successively for the various records.
     *
     * To avoid propagating of the cleaning multiple times by racing threads, the clean method only
     * allows cleaning a record once.
     *
     */

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
	this.transactionNumber = 0;
	this.clean = new AtomicBoolean(true);
	this.writeSet = WriteSet.empty();
	this.recordCommitted = true;
    }
    protected static ActiveTransactionsRecord makeSentinelRecord() {
	return new ActiveTransactionsRecord();
    }

    public ActiveTransactionsRecord(int txNumber, WriteSet writeSet) {
        this.transactionNumber = txNumber;
	this.clean = new AtomicBoolean(false);
	this.writeSet = writeSet;
    }

    public void incrementRunning() {
        running.incrementAndGet();
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
    protected boolean trySetNext(ActiveTransactionsRecord next) {
	return this.next.compareAndSet(null, next);
    }
    
    protected void setCommitted() {
	this.recordCommitted = true;
    }

    protected boolean isCommitted() {
	return this.recordCommitted;
    }

    protected WriteSet getWriteSet() {
	return this.writeSet;
    }

    public ActiveTransactionsRecord getRecordForNewTransaction() {
        ActiveTransactionsRecord rec = this;
        while (true) {
            rec.running.incrementAndGet();
            if (rec.getNext() == null || !rec.getNext().recordCommitted) {
                // if there is no next yet, then it's because the rec
                // is the most recent one and we may return it
		// if the next exists, but is not yet committed, we also return this
                return rec;
            } else {
                // a more recent record exists, so backoff and try
                // again with the new one
                rec.decrementRunning();
                rec = rec.getNext();
            }
        }
    }

    public void decrementRunning() {
        if (running.decrementAndGet() == 0) {
            // when running reachs 0 maybe it's time to clean our successor
            maybeCleanSuc();
        }
    }

    private void maybeCleanSuc() {
        // it is crucial that we test the next field first, because
        // only after having the next non-null, do we have the
        // guarantee that no transactions may start for this record

        // if we checked the number of running first, it could happen
        // that no running existed, but one started between the test
        // for running and the test for next

        ActiveTransactionsRecord rec = this;
        while (true) {
            if ((rec.getNext() != null && rec.getNext().recordCommitted /* must be first */)
		&& rec.isClean() && (rec.running.get() == 0 /* must be after */)) {
                if (rec.getNext().clean()) {
                    // if we cleaned-up, try to clean-up further down the list
                    rec = rec.getNext();
                    continue;
                }
            }
            break;
        }
    }

    private boolean isClean() {
        return this.clean.get();
    }

    protected boolean clean() {
        Boolean alreadyCleaned = this.clean.getAndSet(true);

        // the toClean may be null because more than one thread may
        // race into this method
        // yet, because of the atomic getAndSet above, only one will
        // actually clean the bodies
        if (!alreadyCleaned) {
	    for (Cons<VBoxBody> bodiesPerBlock : this.getWriteSet().bodiesPerBlock) {
		for (VBoxBody<?> body : bodiesPerBlock) {
		    body.clearPrevious();
		}
	    }
 	    writeSet = null; // this is very helpful for the GC. verified by experimentation

            notifyListeners(transactionNumber);

            return true;
        } else {
            return false;
        }
    }
}
