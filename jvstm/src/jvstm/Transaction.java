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

public abstract class Transaction implements Comparable<Transaction> {

    // static part starts here


    /*
     * The committed static field is volatile to ensure correct
     * synchronization among different threads:
     *
     * - A newly created transaction reads the value of this field at
     *   the very beginning of its existence, before trying to
     *   access any box.
     *
     * - A write transaction writes to this field at the very end,
     *   after commiting all the boxes to their new values.
     *
     * This way, because of the new semantics of the Java Memory
     * Model, as specified by JSR133 (which is incorporated in the
     * newest Java Language Specification), we know that all the
     * values written previously in the commit of write transaction
     * will be visible to any other transaction that is created with
     * the new value of the committed field.
     *
     * This change is sufficient to ensure the correct synchronization
     * guarantees, even if we remove all the remaining volatile
     * declarations from the VBox and VBoxBody classes.
     */
    private static volatile int committed = 0;

    protected static final ActiveTransactionQueue ACTIVE_TXS;

    static {
        ACTIVE_TXS = new ActiveTransactionQueue();
        ACTIVE_TXS.enable();
    }

    protected static final ThreadLocal<Transaction> current = new ThreadLocal<Transaction>();

    private static TransactionFactory TRANSACTION_FACTORY = new DefaultTransactionFactory();

    public static void setTransactionFactory(TransactionFactory factory) {
	TRANSACTION_FACTORY = factory;
    }

    public static Transaction current() {
        return current.get();
    }

    public static int getCommitted() {
        return committed;
    }

    public static void setCommitted(int number) {
        committed = Math.max(number, committed);
    }

    public static void addTxQueueListener(TxQueueListener listener) {
	ACTIVE_TXS.addListener(listener);
    }

    public static Transaction begin() {
        return begin(false);
    }

    public static Transaction begin(boolean readOnly) {
        Transaction parent = current.get();
        Transaction tx = null;

	// we need to synchronize on the queue LOCK to inhibit the queue clean-up because we 
	// don't want that a transaction that commits between we get the last committed number 
	// and we add the new transaction cleans up the version number that we got before 
	// the new transaction is added to the queue
        ACTIVE_TXS.LOCK.lock();
        try {
	    if (parent == null) {
                if (readOnly) {
                    tx = TRANSACTION_FACTORY.makeReadOnlyTopLevelTransaction(getCommitted());
                } else {
                    tx = TRANSACTION_FACTORY.makeTopLevelTransaction(getCommitted());
                }
	    } else {
		tx = parent.makeNestedTransaction();
	    }
            tx.start();
	    ACTIVE_TXS.add(tx);
	} finally {
            ACTIVE_TXS.LOCK.unlock();
        }

        return tx;
    }


    public static void abort() {
	Transaction tx = current.get();
        tx.abortTx();
    }

    public static void commit() {
	Transaction tx = current.get();
        tx.commitTx(true);
    }

    public static void checkpoint() {
        Transaction tx = current.get();
        tx.commitTx(false);
    }

    protected int number;
    protected final Transaction parent;
    protected volatile boolean finished = false;
    protected volatile Thread thread = Thread.currentThread();
    
    public Transaction(int number) {
        this.number = number;
        this.parent = null;
    }

    public Transaction(Transaction parent) {
        this.number = parent.getNumber();
        this.parent = parent;
    }

    public void start() {
        current.set(this);
    }

    public Thread getThread() {
        return thread;
    }

    protected Transaction getParent() {
        return parent;
    }

    public int getNumber() {
        return number;
    }

    protected void setNumber(int number) {
        this.number = number;
    }

    public int compareTo(Transaction o) {
	return (this.getNumber() - o.getNumber());
    }

    protected void renumber(int txNumber) {
	// To keep the queue ordered, we have to remove and reinsert the TX when it is renumbered
	ACTIVE_TXS.renumberTransaction(this, txNumber);
    }

    protected void abortTx() {
        finishTx();
    }

    protected void commitTx(boolean finishAlso) {
        doCommit();

        if (finishAlso) {
            finishTx();
        }
    }

    private void finishTx() {
        // ensure that this method is called only by the thread "owning" the transaction, 
        // because, otherwise, we may not set the current ThreadLocal variable of the correct thread
        if (Thread.currentThread() != this.thread) {
            throw new Error("ERROR: Cannot finish a transaction from another thread than the one running it");
        }

        finish();

        current.set(this.getParent());

        this.finished = true;

        // clean up the reference to the thread
        this.thread = null;
    }

    public boolean isFinished() {
        return finished;
    }

    protected void finish() {
        // intentionally empty
    }

    protected void gcTransaction() {
	// by default, do nothing
    }

    public abstract Transaction makeNestedTransaction();

    public abstract <T> T getBoxValue(VBox<T> vbox);

    public abstract <T> void setBoxValue(VBox<T> vbox, T value);
    
    public abstract <T> T getPerTxValue(PerTxBox<T> box, T initial);

    public abstract <T> void setPerTxValue(PerTxBox<T> box, T value);

    protected abstract void doCommit();


    public static void transactionallyDo(TransactionalCommand command) {
        while (true) {
            Transaction tx = Transaction.begin();
            try {
                command.doIt();
                tx.commit();
                tx = null;
                return;
            } catch (CommitException ce) {
                tx.abort();
                tx = null;
            } finally {
                if (tx != null) {
                    tx.abort();
                }
            }
        }
    }
}
