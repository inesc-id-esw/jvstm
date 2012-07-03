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
 * This transaction is only used during the lock-free commit helping procedure.
 * Namely, it encapsulates the execution of the PerTxBoxes of the committing
 * transaction at commit time. This allows new implementations of getBoxValue
 * and setBoxValue to be hooked, so that they correctly handle a situation in
 * which there are potential multiple helpers going through the same PerTxBoxes.
 * 
 * @author nmld
 * 
 */
public class CommitTimeTransaction extends Transaction {

    protected static final StopPerTxBoxesCommitException STOP_PER_TX_BOX_COMMIT_EXCEPTION = new StopPerTxBoxesCommitException();

    // Note: these are not meant to be modified!
    private Map<VBox, Object> writeSet;
    private Map<PerTxBox, Object> perTxValues;

    private Map<VBox, Object> writeSetProduced = ReadWriteTransaction.EMPTY_MAP;
    private Transaction transactionHelping;

    public CommitTimeTransaction(ReadWriteTransaction committer) {
	super(committer.getNumber());
	this.writeSet = committer.boxesWritten;
	this.perTxValues = committer.perTxValues;

	this.transactionHelping = Transaction.current();
	Transaction.current.set(this);
    }

    public Map<VBox, Object> finishExecution() {
	Map<VBox, Object> result = this.writeSetProduced;

	this.writeSet = null;
	this.perTxValues = null;
	this.writeSetProduced = null;

	Transaction.current.set(this.transactionHelping);

	this.transactionHelping = null;

	return result;
    }

    public void finishEarly() {
	this.writeSet = null;
	this.perTxValues = null;
	this.writeSetProduced = null;

	Transaction.current.set(this.transactionHelping);

	this.transactionHelping = null;
    }

    protected <T> T getLocalValue(VBox<T> vbox) {
	T result = null;
	if (writeSetProduced != ReadWriteTransaction.EMPTY_MAP) {
	    result = (T) writeSetProduced.get(vbox);
	}
	if (result == null && writeSet != ReadWriteTransaction.EMPTY_MAP) {
	    result = (T) writeSet.get(vbox);
	}
	return result;
    }

    @Override
    public <T> T getBoxValue(VBox<T> vbox) {
	T value = getLocalValue(vbox);
	if (value != null) {
	    return (value == ReadWriteTransaction.NULL_VALUE) ? null : value;
	}

	try {
	    return vbox.body.getBody(this.number).value;
	} catch (NullPointerException npe) {
	    // The thread helping in this commit may be arbitrarily delayed and
	    // executing this code long after the commit has been finished.
	    // Therefore, the "this.number" version and all bellow it may have
	    // been GCed by the JVSTM GC. Consequently, the getBody.value will
	    // throw a NPE. In that case, we know that some helper finished this
	    // and therefore may preemptively stop.

	    // A sanity check is performed in the WriteSet class where this
	    // exception is caught.
	    throw STOP_PER_TX_BOX_COMMIT_EXCEPTION;
	}
    }

    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
	if (writeSetProduced == ReadWriteTransaction.EMPTY_MAP) {
	    writeSetProduced = new HashMap<VBox, Object>();
	}
	writeSetProduced.put(vbox, value == null ? ReadWriteTransaction.NULL_VALUE : value);
    }

    private static final String NOT_YET_SUPPORTED_MESSAGE = "The CommitTimeTransaction does not YET implement this operation";

    @Override
    public <T> T getArrayValue(VArrayEntry<T> entry) {
	throw new UnsupportedOperationException(NOT_YET_SUPPORTED_MESSAGE);
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
	throw new UnsupportedOperationException(NOT_YET_SUPPORTED_MESSAGE);
    }

    private static final String UNSUPPORTED_MESSAGE = "The CommitTimeTransaction does not implement this operation";

    @Override
    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    protected Transaction commitAndBeginTx(boolean readOnly) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Transaction makeNestedTransaction(boolean readOnly) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    protected void doCommit() {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Transaction makeUnsafeMultithreaded() {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Transaction makeParallelNestedTransaction(boolean readOnly) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public boolean isWriteTransaction() {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

}