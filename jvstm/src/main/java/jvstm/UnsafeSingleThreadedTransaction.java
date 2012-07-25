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

import java.util.HashMap;
import java.util.Map;

/**
 * An UnsafeSingleThreadedTransaction needs to run alone in a single thread.
 * This class is unsafe because, it assumes that no other transactions are
 * running, but doesn't check it. Thus, concurrent transactions can run, but
 * *WILL BREAK* the system. UnsafeSingleThreadedTransactions are useful for
 * setup scenarios, where an application is single-threadedly initialized before
 * being concurrently available. There are potential problems in using this
 * transaction type, thus its use is extremely discouraged, except in well
 * controlled cases. Use at your own risk.
 */
public class UnsafeSingleThreadedTransaction extends Transaction {

    private ActiveTransactionsRecord activeTxRecord;

    private Map<PerTxBox, Object> perTxValues = ReadWriteTransaction.EMPTY_MAP;

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
    protected Transaction commitAndBeginTx(boolean readOnly) {
	commitTx(true);
	return Transaction.beginUnsafeSingleThreaded();
    }

    @Override
    protected void abortTx() {
	commitTx(true);
	// throw new
	// Error("An UnsafeSingleThreaded transaction cannot abort.  I've committed it instead.");
    }

    @Override
    protected void finish() {
	super.finish();
	if (!context().inCommitAndBegin) {
	    context().oldestRequiredVersion = null;
	}
    }

    @Override
    public Transaction makeNestedTransaction(boolean readOnly) {
	throw new Error("UnsafeSingleThreaded transactions don't support nesting yet");
    }

    @Override
    public Transaction makeParallelNestedTransaction(boolean readOnly) {
	throw new Error("UnsafeSingleThreaded transactions don't support nesting yet");
    }

    @Override
    public Transaction makeUnsafeMultithreaded() {
	throw new Error("UnsafeSingleThreaded transactions don't support nesting yet");
    }

    @Override
    public <T> T getBoxValue(VBox<T> vbox) {
	return vbox.body.value;
    }

    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
	vbox.body = VBox.makeNewBody(value, number, null); // we immediately
							   // clean old unused
							   // values
    }

    @Override
    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	T value = null;
	if (perTxValues != ReadWriteTransaction.EMPTY_MAP) {
	    value = (T) perTxValues.get(box);
	}
	if (value == null) {
	    value = initial;
	}

	return value;
    }

    @Override
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
	if (perTxValues == ReadWriteTransaction.EMPTY_MAP) {
	    perTxValues = new HashMap<PerTxBox, Object>();
	}
	perTxValues.put(box, value);
    }

    @Override
    protected void doCommit() {
	// the commit is already done, so create a new ActiveTransactionsRecord
	ActiveTransactionsRecord newRecord = new ActiveTransactionsRecord(getNumber(), WriteSet.empty());
	newRecord.setCommitted();
	setMostRecentCommittedRecord(newRecord);

	if (!this.activeTxRecord.trySetNext(newRecord)) {
	    throw new Error("Unacceptable: UnsafeSingleThreadedTransaction in a concurrent environment");
	}

	// we must update the activeRecords accordingly

	context().oldestRequiredVersion = newRecord;
	this.activeTxRecord = newRecord;
    }

    @Override
    public <T> T getArrayValue(VArrayEntry<T> entry) {
	// Read directly from array
	return entry.array.values.get(entry.index);
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
	VArray<T> array = entry.array;

	// Set array to current version, clear log
	if (array.version != number) {
	    array.version = number;
	    array.log = null;
	}

	// Write directly into array
	array.values.lazySet(entry.index, value);
    }

    @Override
    public boolean isWriteTransaction() {
	return true;
    }
}
