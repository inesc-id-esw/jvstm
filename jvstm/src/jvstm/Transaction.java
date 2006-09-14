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

public abstract class Transaction implements Comparable<Transaction> {

    private enum TxState { RUNNING, COMMITING, FINISHED }

    private static int commited = 0;

    protected static final ActiveTransactionQueue ACTIVE_TXS = new ActiveTransactionQueue();

    protected static final ThreadLocal<Transaction> current = new ThreadLocal<Transaction>();

    private static TransactionFactory TRANSACTION_FACTORY = new TransactionFactory() {
	    public Transaction makeTopLevelTransaction(int txNumber) {
		return new TopLevelTransaction(txNumber);
	    }
            public Transaction makeReadOnlyTopLevelTransaction(int txNumber) {
                return new ReadTransaction(txNumber);
            }
	};

    public static void setTransactionFactory(TransactionFactory factory) {
	TRANSACTION_FACTORY = factory;
    }

    public static Transaction current() {
        return current.get();
    }

    public static synchronized int getCommitted() {
        return commited;
    }

    public static synchronized void setCommitted(int number) {
        commited = Math.max(number, commited);
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

	// we need to synchronize on the queue to inhibit the queue clean-up because we 
	// don't want that a transaction that commits between we get the last committed number 
	// and we add the new transaction cleans up the version number that we got before 
	// the new transaction is added to the queue
	synchronized (ACTIVE_TXS) {
	    if (parent == null) {
                if (readOnly) {
                    tx = TRANSACTION_FACTORY.makeReadOnlyTopLevelTransaction(getCommitted());
                } else {
                    tx = TRANSACTION_FACTORY.makeTopLevelTransaction(getCommitted());
                }
	    } else {
		tx = parent.makeNestedTransaction();
	    }
	    current.set(tx);
	    ACTIVE_TXS.add(tx);
	}
        return tx;
    }


    public static void abort() {
	Transaction tx = current.get();
        tx.abortTx();
        current.set(tx.getParent());
    }

    public static void commit() {
	Transaction tx = current.get();
        tx.commitTx();
        current.set(tx.getParent());
    }

    public static void checkpoint() {
        Transaction tx = current.get();
        tx.doCommit();
    }

    public static void setTimeout(long millis) {
        Transaction tx = current.get();
        tx.setTimeoutMillis(millis);        
    }

    protected int number;
    protected Transaction parent;
    protected TxState state = TxState.RUNNING;
    protected Thread thread = Thread.currentThread();
    protected long timeoutAfterMillis = -1;
    
    public Transaction(int number) {
        this.number = number;
    }

    public Transaction(Transaction parent) {
        this(parent.getNumber());
        this.parent = parent;
    }

    public void setTimeoutMillis(long millis) {
        timeoutAfterMillis = (millis == -1) ? -1 : System.currentTimeMillis() + millis;
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
        boolean callFinish = false;
        synchronized (this) {
            if (state == TxState.RUNNING) {
                state = TxState.FINISHED;
                callFinish = true;
            }
        }
        if (callFinish) {
            finish();
        }
    }

    protected void commitTx() {
        synchronized (this) {
            if (state == TxState.RUNNING) {
                state = TxState.COMMITING;
            } else {
                throw new Error("Cannot commit a transaction that is not running anymore");
            }
        }

        try {
            doCommit();

            synchronized (this) {
                state = TxState.FINISHED;
            }
        } finally {
            synchronized (this) {
                // If an exception ocurred during the commit revert
                // the state back to RUNNING so that a posterior abort
                // properly finishes the transaction
                if (state == TxState.COMMITING) {
                    state = TxState.RUNNING;
                }
            }
        }

        finish();
    }

    synchronized boolean isFinished() {
        return state == TxState.FINISHED;
    }

    boolean hasPassedTimeout() {
        return (timeoutAfterMillis > 0) && (System.currentTimeMillis() > timeoutAfterMillis);
    }

    protected void finish() {
	// the eventual setCommitted was already done, then we may clean-up
	ACTIVE_TXS.noteTxFinished(this);
    }

    protected void gcTransaction() {
	// by default, do nothing
    }

    protected abstract Transaction makeNestedTransaction();

    protected abstract <T> void register(VBox<T> vbox, VBoxBody<T> body);

    protected abstract <T> VBoxBody<T> getBodyForRead(VBox<T> vbox);

    protected abstract <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox);
    
    protected abstract <T> T getPerTxValue(PerTxBox<T> box, T initial);

    protected abstract <T> void setPerTxValue(PerTxBox<T> box, T value);

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
