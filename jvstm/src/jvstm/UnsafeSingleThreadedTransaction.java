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

/** An UnsafeSingleThreadedTransaction needs to run alone in a single thread.  This class is unsafe
 * because, it assumes that no other transactions are running, but doesn't check it.  Thus,
 * concurrent transactions can run, but *WILL BREAK* the system.  UnsafeSingleThreadedTransactions
 * are useful for setup scenarios, where an application is single-threadedly initialized before
 * being concurrently available.  There are potential problems in using this transaction type, thus
 * its use is extremely discouraged, except in well controlled cases. Use at your own risk. */
public class UnsafeSingleThreadedTransaction extends Transaction {

    private ActiveTransactionsRecord activeTxRecord;

    public UnsafeSingleThreadedTransaction(ActiveTransactionsRecord activeRecord) {
        super(activeRecord.transactionNumber);
	this.activeTxRecord = activeRecord;
    }

    @Override
    public void start() {
        // once we get here, we may already increment the transaction
        // number
        int newTxNumber = this.activeTxRecord.transactionNumber + 1;
        
	// renumber the TX to the new number
	setNumber(newTxNumber);

        super.start();
    }

    @Override
    protected void abortTx() {
        commitTx(true);
        //throw new Error("An UnsafeSingleThreaded transaction cannot abort.  I've committed it instead.");
    }

    @Override
    protected void finish() {
        super.finish();
        activeTxRecord.decrementRunning();
    }

    public Transaction makeNestedTransaction(boolean readOnly) {
	throw new Error("UnsafeSingleThreaded transactions don't support nesting yet");
    }

    public <T> T getBoxValue(VBox<T> vbox) {
        return vbox.body.value;
    }

    public <T> void setBoxValue(VBox<T> vbox, T value) {
	vbox.body = VBox.makeNewBody(value, number, null); // we immediatly clean old unused values
    }

    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	throw new Error("UnsafeSingleThreaded transactions don't support PerTxBoxes yet");
    }
    
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
	throw new Error("UnsafeSingleThreaded transactions don't support PerTxBoxes yet");
    }

    protected void doCommit() {
        // the commit is already done, so create a new ActiveTransactionsRecord
	ActiveTransactionsRecord newRecord = new ActiveTransactionsRecord(getNumber(), WriteSet.empty());
	newRecord.setCommitted();
        setMostRecentCommittedRecord(newRecord);

	if (!this.activeTxRecord.trySetNext(newRecord)) {
	    throw new Error("Unacceptable: UnsafeSingleThreadedTransaction in a concurrent environment");
	}

        // we must update the activeRecords accordingly
        
        // the correct order is to increment first the
        // new, and only then decrement the old
        newRecord.incrementRunning();
        this.activeTxRecord.decrementRunning();
        this.activeTxRecord = newRecord;
    }
}
