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

import jvstm.util.Cons;

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
public class ProcessPerTxBoxesTransaction extends Transaction {

    public static final ProcessPerTxBoxesTransaction EMPTY_COMMIT_TX = new ProcessPerTxBoxesTransaction();
    
    protected Map<PerTxBox, Object> specPerTxBoxes = ReadWriteTransaction.EMPTY_MAP;
    protected Map<VBox, Object> specWriteSet = ReadWriteTransaction.EMPTY_MAP;
    private TopLevelTransaction committer;

    public ProcessPerTxBoxesTransaction() {
	super(0);
    }
    
    public ProcessPerTxBoxesTransaction(int maxVersion, TopLevelTransaction committer) {
	super(maxVersion);
	this.committer = committer;
	Transaction.current.set(this);
    }

    public void finishExecution() {
	Transaction.current.set(this.committer);
	this.committer = null;
    }
    
    protected <T> T getLocalValue(VBox<T> vbox) {
	InplaceWrite<T> inplace = vbox.inplace;
	if (inplace.orec.owner == this.committer) {
	    return inplace.tempValue;
	}
	if (committer.boxesWritten != ReadWriteTransaction.EMPTY_MAP) {
	    return (T) committer.boxesWritten.get(vbox);
	}
	return null;
    }

    @Override
    public <T> T getBoxValue(VBox<T> vbox) {
	T value = null;
	if (specWriteSet != ReadWriteTransaction.EMPTY_MAP) {
	    value = (T) specWriteSet.get(vbox);
	}
	if (value != null) {
	    return (value == ReadWriteTransaction.NULL_VALUE) ? null : value;
	}
	
        OwnershipRecord currentOwner = vbox.inplace.orec;
        if (currentOwner.version > 0 && currentOwner.version <= this.number) {
            return vbox.body.getBody(this.number).value;
        } 
        
        value = getLocalValue(vbox);
        if (value == null) {
            return vbox.body.getBody(this.number).value;
        }
	
        return (value == ReadWriteTransaction.NULL_VALUE) ? null : value;
    }

    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
	if (specWriteSet == ReadWriteTransaction.EMPTY_MAP) {
	    specWriteSet = new HashMap<VBox, Object>();
	}
	specWriteSet.put(vbox, value == null ? ReadWriteTransaction.NULL_VALUE : value);
    }

    @Override
    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	T value = null;
	if (specPerTxBoxes != ReadWriteTransaction.EMPTY_MAP) {
	    value = (T) specPerTxBoxes.get(box);
	}
	if (value == null && committer.perTxValues != ReadWriteTransaction.EMPTY_MAP) {
	    value = (T) committer.perTxValues.get(box);
	}
	if (value == null) {
	    value = initial;
	}

	return value;
    }

    @Override
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
	if (specPerTxBoxes == ReadWriteTransaction.EMPTY_MAP) {
	    specPerTxBoxes = new HashMap<PerTxBox, Object>();
	}
	specPerTxBoxes.put(box, value);
    }

    private static final String NOT_YET_SUPPORTED_MESSAGE = "The CommitTimeTransaction does not YET implement this operation";

    // TODO to work out later with Ivo
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