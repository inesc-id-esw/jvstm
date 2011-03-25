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
import java.util.Random;

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
    protected ActiveTransactionsRecord getSameRecordForNewTransaction() {
	this.activeTxRecord.incrementRunning();
	return this.activeTxRecord;
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

    protected WriteSet makeWriteSet() {
	return new WriteSet(boxesWritten);
    }
    
    /* validate this transaction and afterwards try to enqueue its commit request with a
     * compare-and-swap. If the CAS doesn't succeed, then it's because other transaction(s) got
     * ahead and entered the commit queue, so we also have to validate against that(those)
     * transaction(s) before re-attempting the CAS.
     *
//      * If validation suceeds the transaction is upgraded to the latest valid read state (which is
//      * the record previous to the commitTxRecord)
     */
    private void validateCommitAndEnqueue(ActiveTransactionsRecord lastValid) {
	WriteSet writeSet = makeWriteSet();

// 	ActiveTransactionsRecord lastValid = this.activeTxRecord;
	do {
	    lastValid = validate(lastValid);
	    this.commitTxRecord = new ActiveTransactionsRecord(lastValid.transactionNumber + 1, writeSet);
	} while (!lastValid.trySetNext(this.commitTxRecord));

	// after validating, upgrade the transaction's valid read state
//   	upgradeTx(lastValid);
    }

    protected void tryCommit() {
	if (isWriteTransaction()) {
	    validate();
	    // for now we don't support PerTxBoxes :-(
	    ensureCommitStatus();
	    upgradeTx(this.commitTxRecord);
	}
    }

    // this may be heavier than simply doing the new validateAndEnqueue when running on a small
    // number of cores.  We might improve by using an adaptive validation that only used the more
    // complex solution whenever relevant. Idea for relevant: compare "the average write-set size
    // multiplied by the distance between activeTx # and last enqueued #" with the read-set size.
    protected void validate() {
	ActiveTransactionsRecord lastSeenCommitted = ensureCommitStatusBeforeValidation();
	oldStyleValidation(); // this validates up to the last seen committed at least
	validateCommitAndEnqueue(lastSeenCommitted);
    }

    protected ActiveTransactionsRecord ensureCommitStatusBeforeValidation() {
	ActiveTransactionsRecord lastSeenCommitted = Transaction.mostRecentCommittedRecord;
	ActiveTransactionsRecord recordToCommit = lastSeenCommitted.getNext();
	
        while (recordToCommit != null) {
	    helpCommit(recordToCommit);
	    lastSeenCommitted = recordToCommit;
	    recordToCommit = recordToCommit.getNext();
        }
	return lastSeenCommitted;
    }

    protected void oldStyleValidation() {
        for (Map.Entry<VBox,VBoxBody> entry : bodiesRead.entrySet()) {
            if (entry.getKey().body != entry.getValue()) {
                throw new CommitException();
            }
        }
    }

    protected void ensureCommitStatus() {
	ActiveTransactionsRecord recordToCommit = Transaction.mostRecentCommittedRecord.getNext();
	
        while ((recordToCommit != null)
	       && (recordToCommit.transactionNumber <= this.commitTxRecord.transactionNumber)) {
	    helpCommit(recordToCommit);
	    recordToCommit = recordToCommit.getNext();
        }
    }

    // Help to commit has much as possible.
    protected void helpCommit(ActiveTransactionsRecord recordToCommit) {
	if (!recordToCommit.isCommitted()) {
	    // We must check whether recordToCommit.getWriteSet() could, in the meanwhile, become
	    // null.  This occurs when this recordToCommit was already committed and even cleaned
	    // while this thread was waiting to be scheduled
	    WriteSet writeSet = recordToCommit.getWriteSet();
	    if ((writeSet != null) && writeSet.helpWriteBack(recordToCommit.transactionNumber)) {
		// the thread that commits the last body will handle the rest of the commit
		finishCommit(recordToCommit);
	    } else { // didn't commit the last body and no more help can be provided
		waitUntilCommitted(recordToCommit);
	    }
	}
    }

    /* Wait for a record to be in the committed state */
    protected void waitUntilCommitted(ActiveTransactionsRecord recordToCommit) {
	Random r = new Random();
  	int time = 1;
	while (!recordToCommit.isCommitted()) {
  	    time <<= 1;
	    // smf: I think that maybe this wait loop keeps the algorithm from being lock-free,
            // because progress is not guaranteed.  However, this situation will only occur when
            // enough threads are already busy writing-back this record's write-set. We can ensure
            // (really?  check!) that in such situation the code that other threads are running will
            // not fail (unless the whole system breaks, e.g., with an OutOfMemoryException), and
            // thus this loop will end.  Discuss this.
 	    try {
		Thread.sleep(r.nextInt(time));
	    } catch(InterruptedException ie) {
		// ignore
	    }
// 	    Thread.yield();
	}
    }

    protected void finishCommit(ActiveTransactionsRecord recordToCommit) {
	recordToCommit.setCommitted();
	Transaction.setMostRecentCommittedRecord(recordToCommit);
    }
}
