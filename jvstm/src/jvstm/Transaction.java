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

public abstract class Transaction {

    // static part starts here


    /*
     * The mostRecentRecord static field is volatile to ensure correct
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
    protected static volatile ActiveTransactionsRecord mostRecentRecord = new ActiveTransactionsRecord(0, null);

    protected static final ThreadLocal<Transaction> current = new ThreadLocal<Transaction>();

    private static TransactionFactory TRANSACTION_FACTORY = new DefaultTransactionFactory();

    public static void setTransactionFactory(TransactionFactory factory) {
	TRANSACTION_FACTORY = factory;
    }

    public static Transaction current() {
        return current.get();
    }

    public static int getMostRecentCommitedNumber() {
        return mostRecentRecord.transactionNumber;
    }

    // this method is called during the commit of a write transaction
    // the commits are already synchronized, so this method doesn't need to be
    public static void setMostRecentActiveRecord(ActiveTransactionsRecord record) {
        mostRecentRecord.setNext(record);
        mostRecentRecord = record;
    }

    public static void addTxQueueListener(TxQueueListener listener) {
	ActiveTransactionsRecord.addListener(listener);
    }

    public static Transaction begin() {
        return begin(false);
    }

    public static Transaction begin(boolean readOnly) {
        Transaction parent = current.get();
        Transaction tx = null;

        if (parent == null) {
            ActiveTransactionsRecord activeRecord = mostRecentRecord.getRecordForNewTransaction();
            if (readOnly) {
                tx = TRANSACTION_FACTORY.makeReadOnlyTopLevelTransaction(activeRecord);
            } else {
                tx = TRANSACTION_FACTORY.makeTopLevelTransaction(activeRecord);
            }
        } else {
            tx = parent.makeNestedTransaction();
        }
        tx.start();

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

    protected Transaction getParent() {
        return parent;
    }

    public int getNumber() {
        return number;
    }

    protected void setNumber(int number) {
        this.number = number;
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
        finish();

        current.set(this.getParent());
    }

    protected void finish() {
        // intentionally empty
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
