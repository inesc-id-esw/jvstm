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

import jvstm.util.Cons;

/**
 * This type of transaction is meant for usage when one parallelizes a
 * transaction into subparts that are disjoint. This means that an
 * UnsafeParallelTransaction never aborts because of a conflict with is
 * siblings.
 * 
 * @author nmld
 * 
 */
public class UnsafeParallelTransaction extends ParallelNestedTransaction {

    protected final OwnershipRecord parentOrec;

    public UnsafeParallelTransaction(ReadWriteTransaction parent) {
	super(parent, true);
	this.parentOrec = parent.orec;
	this.orec.owner = parent;
    }

    @Override
    public Transaction makeNestedTransaction(boolean readOnly) {
	throw new Error("Unsafe Parallel Transactions can only spawn nested transactions that are themselves unsafe");
    }

    @Override
    public Transaction makeParallelNestedTransaction(boolean readOnly) {
	throw new Error("Unsafe Parallel Transactions can only spawn nested transactions that are themselves unsafe");
    }

    @Override
    protected void abortTx() {
	boxesWritten = null;
	perTxValues = null;

	int i = 0;
	for (ReadBlock block : globalReads) {
	    block.free = true;
	    i++;
	}
	blocksFree.get().addAndGet(i);

	globalReads = null;
	boxesWrittenInPlace = null;
	Transaction.current.set(null);
    }

    @Override
    protected void finish() {
	boxesWritten = null;
	perTxValues = null;
	Transaction.current.set(null);
    }

    @Override
    protected void doCommit() {
	tryCommit();
    }

    @Override
    protected void cleanUp() {
	for (ReadBlock block : globalReads) {
	    block.free = true;
	    block.freeBlocks.incrementAndGet();
	}
	globalReads = null;
	boxesWrittenInPlace = null;
    }

    @Override
    protected <T> T getLocalValue(VBox<T> vbox) {
	InplaceWrite<T> inplace = vbox.inplace;
	if (inplace.orec.owner == parent) {
	    return inplace.tempValue;
	} else {
	    T value = null;
	    synchronized (parent) {
		if (boxesWritten != EMPTY_MAP) {
		    value = (T) boxesWritten.get(vbox);
		}
	    }
	    return value;
	}
    }

    @Override
    public <T> T getBoxValue(VBox<T> vbox) {
	/*
	 * When either no one has written to this vbox or any committed writer
	 * is not older than my version we know that this transaction (as well
	 * as any parent) does not have a local value. In this case we read
	 * directly from the vbox's body.
	 */
	OwnershipRecord currentOwner = vbox.inplace.orec;
	if (currentOwner.version > 0 && currentOwner.version <= this.number) {
	    return readGlobal(vbox);
	} else {
	    T value = getLocalValue(vbox);
	    if (value == null) { // no local value exists
		return readGlobal(vbox);
	    }
	    // else
	    return (value == NULL_VALUE) ? null : value;
	}
    }

    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
	InplaceWrite<T> inplaceWrite = vbox.inplace;
	OwnershipRecord currentOwner = inplaceWrite.orec;
	if (currentOwner.owner == parent) { // we are already the current writer
	    inplaceWrite.tempValue = (value == null ? (T) NULL_VALUE : value);
	    return;
	}

	do {
	    if (currentOwner.version != 0 && currentOwner.version <= this.number) {
		if (inplaceWrite.CASowner(currentOwner, parentOrec)) {
		    inplaceWrite.tempValue = (value == null ? (T) NULL_VALUE : value);
		    boxesWrittenInPlace = boxesWrittenInPlace.cons(vbox);
		    return; // break
		} else {
		    // update the current owner and retry
		    currentOwner = inplaceWrite.orec;
		    continue;
		}
	    } else {
		synchronized (parent) {
		    if (boxesWritten == EMPTY_MAP) {
			boxesWritten = new HashMap<VBox, Object>();
		    }
		    boxesWritten.put(vbox, (value == null ? (T) NULL_VALUE : value));
		    return;
		}
	    }
	} while (true);
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
	ReadWriteTransaction parent = getRWParent();
	synchronized (parent) {
	    if (parent.arrayWrites == EMPTY_MAP) {
		parent.arrayWrites = new HashMap<VArrayEntry<?>, VArrayEntry<?>>();
		parent.arrayWritesCount = new HashMap<VArray<?>, Integer>();
	    }
	    entry.setWriteValue(value, parent.nestedCommitQueue.commitNumber);
	    if (parent.arrayWrites.put(entry, entry) != null)
		return;

	    // Count number of writes to the array
	    Integer writeCount = parent.arrayWritesCount.get(entry.array);
	    if (writeCount == null)
		writeCount = 0;
	    parent.arrayWritesCount.put(entry.array, writeCount + 1);
	}
    }

    @Override
    protected <T> T getLocalArrayValue(VArrayEntry<T> entry) {
	ReadWriteTransaction iter = getRWParent();
	while (iter != null) {
	    synchronized (iter) {
		if (iter.arrayWrites != EMPTY_MAP) {
		    VArrayEntry<T> wsEntry = (VArrayEntry<T>) iter.arrayWrites.get(entry);
		    if (wsEntry != null) {
			return (wsEntry.getWriteValue() == null ? (T) NULL_VALUE : wsEntry.getWriteValue());
		    }
		}
	    }
	    iter = iter.getRWParent();
	}
	return null;
    }

    @Override
    protected void tryCommit() {
	ReadWriteTransaction parent = getRWParent();
	Cons<ParallelNestedTransaction> currentOrecs;
	Cons<ParallelNestedTransaction> modifiedOrecs;

	do {
	    currentOrecs = parent.mergedTxs;
	    modifiedOrecs = currentOrecs.cons(this);
	} while (!parent.CASmergedTxs(currentOrecs, modifiedOrecs));

	if (!this.arraysRead.isEmpty()) {
	    synchronized (parent) {
		// the possible array writes are already placed in the parent
		parent.arraysRead = this.arraysRead.reverseInto(parent.arraysRead);
	    }
	}

	Transaction.current.set(null);
    }

    @Override
    protected Transaction commitAndBeginTx(boolean readOnly) {
	throw new Error("Unsafe Parallel Transaction cannot use 'commit and begin'");
    }

}
