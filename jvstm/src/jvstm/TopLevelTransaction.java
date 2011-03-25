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

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import jvstm.util.Cons;

public class TopLevelTransaction extends ReadWriteTransaction {

    protected ActiveTransactionsRecord activeTxRecord;

    // this record is created when the transaction starts to commit. It marks the transaction's
    // commit order in the active transactions queue
    protected ActiveTransactionsRecord commitTxRecord;

    public TopLevelTransaction(ActiveTransactionsRecord activeRecord) {
        super(activeRecord.transactionNumber);
        this.activeTxRecord = activeRecord;
    }

    @Override
    protected void finish() {
        super.finish();
        activeTxRecord.decrementRunning();
    }

    protected boolean isWriteTransaction() {
	return (! boxesWritten.isEmpty()) || (! perTxValues.isEmpty());
    }

    /* Upgrades this transaction's valid read state.  It only makes sense to invoke this method with
     * a newRecord in the committed state.
     */
    protected void upgradeTx(ActiveTransactionsRecord newRecord) {
	newRecord.incrementRunning();
	this.activeTxRecord.decrementRunning();
	this.activeTxRecord = newRecord;
	setNumber(newRecord.transactionNumber);
    }

    /* validate this transaction and afterwards try to enqueue its commit request with a
     * compare-and-swap. If the CAS doesn't succeed, then it's because other transaction(s) got
     * ahead and entered the commit queue, so we also have to validate against that(those)
     * transaction(s) before re-attempting the CAS.
     *
     * If validation suceeds the transaction is upgraded to the latest valid read state (which is
     * the record previous to the commitTxRecord)
     */
    protected void validateCommitAndEnqueue() {
	ActiveTransactionsRecord lastValid = this.activeTxRecord;
	do {
	    lastValid = validate(lastValid);
	    this.commitTxRecord = new ActiveTransactionsRecord(lastValid.transactionNumber + 1, null, boxesWritten);
	} while (!lastValid.trySetNext(this.commitTxRecord));

	// after validating, upgrade the transaction's valid read state
	upgradeTx(lastValid);
    }

    protected void tryCommit() {
	if (isWriteTransaction()) {
	    validateCommitAndEnqueue();
	    waitUntilCommitted(activeTxRecord); // we know for sure that the activeTxRecord is the previous record
	    reallyCommit();
	}
    }

    /* Wait for a record to be in the committed state, blocking if necessary.  We avoid
     * synchronization overheads by using the double-checked locking pattern.  According to the Java
     * Memory Model, this is ok because we're checking a volatile variable.
     */
    protected void waitUntilCommitted(ActiveTransactionsRecord recordToCommit) {
	if (recordToCommit.isCommitted()) {
	    return;
	} else {
	    synchronized(recordToCommit) {
		// double-checked lock pattern. This is ok because we're checking a volatile variable
		while (!recordToCommit.isCommitted()) {
		    try {
			recordToCommit.wait();
		    } catch (InterruptedException ie) {
			// ignore and continue to wait
		    }
		}
	    }
	}
    }

    protected void reallyCommit() {
	Cons<VBoxBody> bodiesCommitted = performValidCommit();
	commitTxRecord.setBodiesToGC(bodiesCommitted); // must occur before setting the record to committed state!

	synchronized(commitTxRecord) {
	    commitTxRecord.setCommitted();
	    // It is safer to notifyAll() than to notify(), because in the future there might be
	    // more than one transaction waiting here
	    commitTxRecord.notifyAll();
	}

	setMostRecentCommittedRecord(commitTxRecord);
	upgradeTx(commitTxRecord);
    }

    protected Cons<VBoxBody> performValidCommit() {
	// for now we don't support PerTxBoxes :-(
// 	for (Map.Entry<PerTxBox,Object> entry : perTxValues.entrySet()) {
// 	    entry.getKey().commit(entry.getValue());
// 	}
	return doCommit(commitTxRecord.transactionNumber);
    }

    protected Cons<VBoxBody> doCommit(int newTxNumber) {
        Cons<VBoxBody> newBodies = Cons.empty();

        for (Map.Entry<VBox,Object> entry : boxesWritten.entrySet()) {
            VBox vbox = entry.getKey();
            Object newValue = entry.getValue();

	    VBoxBody<?> newBody = vbox.commit((newValue == NULL_VALUE) ? null : newValue, newTxNumber);
            newBodies = newBodies.cons(newBody);
        }

        return newBodies;
    }
}
