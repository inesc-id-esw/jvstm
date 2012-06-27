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

import jvstm.util.Cons;

/* An inevitable transaction is accomplished as follows: 1) first it tries to enqueue a commit
 * record using a compare-and-swap; 2) when it succeeds it ensures that the previous record is
 * committed; 3) then it can execute without interference from other write transactions.  The commit
 * does not need to validate the transaction, because it will read from the immediately previous
 * state.
 *
 * There are two catches: 1) inevitable transactions must still keep the write-set for the following
 * transactions to validate against this commit; 2) the write-set cannot be made accessible until
 * the end of the transaction, thus a special InevitableTransactionsRecord is used that doesn't
 * return the write-set until it has been computed, even though its commit record may already exist.
 */
public class InevitableTransaction extends TopLevelTransaction {

    private Cons<VBox> vboxesWrittenBack = Cons.empty();
    
    public InevitableTransaction(ActiveTransactionsRecord activeRecord) {
        super(activeRecord, -1);
    }

    @Override
    public void start() {
        ActiveTransactionsRecord latestRecord = this.activeTxRecord;
        // start by enqueueing the request
        do {
            latestRecord = findLatestRecord(latestRecord);
            this.commitTxRecord = new InevitableActiveTransactionsRecord(latestRecord.transactionNumber + 1);
        } while (!latestRecord.trySetNext(this.commitTxRecord));

        ensureCommitStatus();
        upgradeTx(latestRecord);

        // once we get here, we may already increment the transaction number.
        // This is also required to allow setBoxValue to immediately write to
        // the vbox.body
        setNumber(commitTxRecord.transactionNumber);
        super.start();
    }

    // Note that this method differs from the one in the superclass.  The loop guard doesn't include
    // our own record.  We don't want to commit it just yet.
    @Override
    protected void ensureCommitStatus() {
        ActiveTransactionsRecord recordToCommit = Transaction.mostRecentCommittedRecord.getNext();

        while ((recordToCommit != null)
               && (recordToCommit.transactionNumber < this.commitTxRecord.transactionNumber)) {
            helpCommit(recordToCommit);
            recordToCommit = recordToCommit.getNext();
        }
    }

    protected ActiveTransactionsRecord findLatestRecord(ActiveTransactionsRecord from) {
        ActiveTransactionsRecord latest = from;
        for (ActiveTransactionsRecord aux; (aux = latest.getNext()) != null; latest = aux);
        return latest;
    }

    // Also, InevitableTransactions cannot abort because their commit record as already been created
    @Override
    protected void abortTx() {
        commitTx(true);
        //throw new Error("An Inevitable transaction cannot abort.  I've committed it instead.");
    }

    public Transaction makeNestedTransaction(boolean readOnly) {
        throw new Error(getClass().getSimpleName() + " doesn't support nesting yet");
    }

    @Override
    public <T> T getBoxValue(VBox<T> vbox) {
        // we don't keep a read-set because this transaction will be valid for sure
        return vbox.body.value;
    }

    // Always store in place, given that commits are not ocurring
    // concurrently, but still keep the write-set to enable other transactions
    // to validate against this one.
    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
        if ((vbox.body != null) && (vbox.body.version == this.number)) {
            vbox.body.value = value;
        } else {
            VBoxBody<T> newBody = VBox.makeNewBody(value, number, vbox.body);
            if (vbox.body != null) { // smf: I guess that a null body this means that the VBox was created during this tx
                this.vboxesWrittenBack = this.vboxesWrittenBack.cons(vbox);
            }
            vbox.body = newBody;
        }
    }

    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
        throw new Error(getClass().getSimpleName() + " doesn't support PerTxBoxes yet");
    }
    
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
        throw new Error(getClass().getSimpleName() + " doesn't support PerTxBoxes yet");
    }

    @Override
    protected WriteSet makeWriteSet() {
        return new WriteSet(vboxesWrittenBack);
    }
    
    @Override
    protected void tryCommit() {
        // we know we're valid and we're already enqueued. just set the writeset
        ((InevitableActiveTransactionsRecord)commitTxRecord).setWriteSet(makeWriteSet());

        helpCommit(this.commitTxRecord);
        upgradeTx(this.commitTxRecord);
    }

    @Override
    public <T> T getArrayValue(VArrayEntry<T> entry) {
        // Read directly from array
        return entry.array.values.get(entry.index);
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
        throw new Error(getClass().getSimpleName() + " doesn't support writing to VArrays yet");
    }

}
