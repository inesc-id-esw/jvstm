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
    protected Transaction commitAndBeginTx(boolean readOnly) {
        context().inCommitAndBegin = true;
        try {
            commitTx(true);
        } finally {
            context().inCommitAndBegin = false;
        }
        // at this point activeTxRecord and commitTxRecord are the same...
        return Transaction.beginWithActiveRecord(readOnly, this.activeTxRecord);
    }

    @Override
    protected void finish() {
        super.finish();
        if (! context().inCommitAndBegin) {
            context().oldestRequiredVersion = null;
        }
    }

    protected boolean isWriteTransaction() {
        return (!boxesWrittenInPlace.isEmpty()) || (! boxesWritten.isEmpty()) || (! perTxValues.isEmpty())  || (! arrayWrites.isEmpty());
    }

    /* Upgrades this transaction's valid read state.  It only makes sense to invoke this method with
     * a newRecord in the committed state.
     */
    protected void upgradeTx(ActiveTransactionsRecord newRecord) {
        context().oldestRequiredVersion = newRecord;
        this.activeTxRecord = newRecord;
        setNumber(newRecord.transactionNumber);
    }

    protected WriteSet makeWriteSet() {
        return new WriteSet(this.boxesWrittenInPlace, this.boxesWritten, this.arrayWrites, this.arrayWritesCount, this.orec);
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
        lastValid = validate(lastValid);
        WriteSet writeSet = makeWriteSet();
        this.commitTxRecord = new ActiveTransactionsRecord(lastValid.transactionNumber + 1, writeSet);
        while (!lastValid.trySetNext(this.commitTxRecord)) {
            lastValid = validate(lastValid);
            this.commitTxRecord = new ActiveTransactionsRecord(lastValid.transactionNumber + 1, writeSet);
        }

        // At this point we no longer need the values we wrote in the VBox's
        // tempValue slot, so we update the ownership record's version to
        // allow reuse of the slot.
        this.orec.version = commitTxRecord.transactionNumber;

        // after validating, upgrade the transaction's valid read state
//      upgradeTx(lastValid);
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
        ActiveTransactionsRecord lastSeenCommitted = helpCommitAll();
        // if (isSnapshotValidationWorthIt(lastSeenCommitted)) {
            snapshotValidation(lastSeenCommitted.transactionNumber); // this validates up to the last seen committed at least
            validateCommitAndEnqueue(lastSeenCommitted);
        // } else {
        //     validateCommitAndEnqueue(this.activeTxRecord);
        // }
    }

    // when the ratio between writes and reads (to validate) is greater than WR_THRESHOLD, then we
    // assume that snapshotValidation is worth executing
    private static float WR_THRESHOLD = 0.5f;
    protected boolean isSnapshotValidationWorthIt(ActiveTransactionsRecord lastRecord) {
        if (this.bodiesRead.isEmpty()) {
            return false;
        }

        int numberOfReadsToCheck = this.bodiesRead.first().length - (next + 1);
        // if there are more arrays the rest are full, for sure
        for (VBox[] array : bodiesRead.rest()) {
            numberOfReadsToCheck += array.length;
        }

        int numberOfWritesToCheck = 0;
        for (ActiveTransactionsRecord rec = this.activeTxRecord.getNext(); rec != null; rec = rec.getNext()) {
            numberOfWritesToCheck += rec.getWriteSet().size();
        }
        return ((float)numberOfWritesToCheck)/numberOfReadsToCheck > WR_THRESHOLD;
    }

    protected static ActiveTransactionsRecord helpCommitAll() {
        ActiveTransactionsRecord lastSeenCommitted = Transaction.mostRecentCommittedRecord;
        ActiveTransactionsRecord recordToCommit = lastSeenCommitted.getNext();
        
        while (recordToCommit != null) {
            helpCommit(recordToCommit);
            lastSeenCommitted = recordToCommit;
            recordToCommit = recordToCommit.getNext();
        }
        return lastSeenCommitted;
    }

    protected void snapshotValidation(int lastSeenCommittedTxNumber) {
        if (lastSeenCommittedTxNumber == getNumber()) {
            return;
        }

        int myNumber = getNumber();

        if (!bodiesRead.isEmpty()) {
            // the first may not be full
            VBox[] array = bodiesRead.first();
            for (int i = next + 1; i < array.length; i++) {
                if (array[i].body.version > myNumber) {
                    throw ReadWriteTransaction.COMMIT_EXCEPTION;
                }
            }

            // the rest are full
            for (VBox[] ar : bodiesRead.rest()) {
                for (int i = 0; i < ar.length; i++) {
                    if (ar[i].body.version > myNumber) {
                        throw ReadWriteTransaction.COMMIT_EXCEPTION;
                    }
                }
            }
        }

        // VArray
        for (VArrayEntry<?> entry : arraysRead) {
            if (!entry.validate()) {
                throw ReadWriteTransaction.COMMIT_EXCEPTION;
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

    /** Help to commit a transaction as much as possible.
     *
     * @param recordToCommit the record to help commit
     */
    protected static void helpCommit(ActiveTransactionsRecord recordToCommit) {
        if (!recordToCommit.isCommitted()) {
            // We must check whether recordToCommit.getWriteSet() could, in the meanwhile, have
            // become null.  This occurs when this recordToCommit was already committed and even
            // cleaned while this thread was waiting to be scheduled
            WriteSet writeSet = recordToCommit.getWriteSet();
            if (writeSet != null) {
                writeSet.helpWriteBack(recordToCommit.transactionNumber);
                // the thread that commits the last body will handle the rest of the commit
                finishCommit(recordToCommit);
            }
        }
    }

    protected static void finishCommit(ActiveTransactionsRecord recordToCommit) {
        // we only advance the most recent committed record if we don't see this transaction already committed
        if (!recordToCommit.isCommitted()) {
            recordToCommit.setCommitted();
            Transaction.setMostRecentCommittedRecord(recordToCommit);
        }
    }
}
