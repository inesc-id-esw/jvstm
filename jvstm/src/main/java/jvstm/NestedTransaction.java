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

import java.util.IdentityHashMap;
import java.util.Map;

import jvstm.util.Cons;

/**
 * Linear Nested Transaction, meaning that it its guaranteed that is the only 
 * active transaction in its nesting tree. The name was preserved for backwards 
 * compatibility. 
 * This nested transaction is able to write in-place even if its root top-level 
 * ancestor also did so in some VBox.
 * @author nmld
 *
 */
public class NestedTransaction extends ReadWriteTransaction {

    protected Cons<VBox> overwrittenAncestorWriteSet = Cons.<VBox>empty();

    public NestedTransaction(ReadWriteTransaction parent) {
	super(parent);
	// start with parent's read-set
	this.bodiesRead = parent.bodiesRead;
	this.arraysRead = parent.arraysRead;
	this.next = parent.next;
	// start with parent write-set of boxes written in place (useful to commit to parent a little faster)
	this.boxesWrittenInPlace = parent.boxesWrittenInPlace;
	// use the parent Orec, which will be necessarily the root top-level tx's orec
	super.ancVersions = ReadWriteTransaction.EMPTY_VERSIONS;
    }

    @Override
    public Transaction makeUnsafeMultithreaded() {
	throw new Error("An Unsafe Parallel Transaction may only be spawned by another Unsafe or a Top-Level transaction");
    }

    @Override
    public void abortTx() {
	// do not call super, we do not want to make the Orec of the ancestor
	// aborted (at least not yet, it might happen depending on the nature 
	// of the abort)
	for (VBox vbox : overwrittenAncestorWriteSet) {
	    // revert the in-place entry that had overwritten
	    vbox.inplace = vbox.inplace.next;
	}

        this.orec.version = OwnershipRecord.ABORTED;
        for (OwnershipRecord mergedLinear : linearNestedOrecs) {
            mergedLinear.version = OwnershipRecord.ABORTED;
        }
	for (ParallelNestedTransaction mergedTx : mergedTxs) {
	    mergedTx.orec.version = OwnershipRecord.ABORTED;
	}
	
	// give the read set arrays, which were used exclusively by this nested or its children, back to the thread pool
	Cons<VBox[]> parentArrays = this.getRWParent().bodiesRead;
	Cons<VBox[]> myArrays = this.bodiesRead;
	while (myArrays != parentArrays) {
	    returnToPool(myArrays.first());
	    myArrays = myArrays.rest();
	}
	
	bodiesRead = null;
	boxesWritten = null;
	boxesWrittenInPlace = null;
	perTxValues = null;
	overwrittenAncestorWriteSet = null;
	mergedTxs = null;
	linearNestedOrecs = null;
	current.set(this.getParent());
    }
    
    protected boolean isAncestor(Transaction tx) {
	Transaction nextParent = parent;
	while (nextParent != null) {
	    if (nextParent == tx) {
		return true;
	    }
	    nextParent = nextParent.parent;
	}
	return false;
    }

    // Differs from the super method because it registers overwritten entries
    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
	InplaceWrite<T> inplaceWrite = vbox.inplace;
	OwnershipRecord currentOwner = inplaceWrite.orec;
	if (currentOwner.owner == this) {
	    inplaceWrite.tempValue = (value == null ? (T)NULL_VALUE : value);
	    return;
	}

	// differs here
	if (isAncestor(currentOwner.owner)) {
	    vbox.inplace = new InplaceWrite<T>(this.orec, (value == null ? (T) NULL_VALUE : value), inplaceWrite);
	    overwrittenAncestorWriteSet = overwrittenAncestorWriteSet.cons(vbox);
	    return;
	}

	do {
	    if (currentOwner.version != 0 && currentOwner.version <= this.number) {
		if (inplaceWrite.CASowner(currentOwner, this.orec)) {
		    inplaceWrite.tempValue = (value == null ? (T) NULL_VALUE : value);
		    boxesWrittenInPlace = boxesWrittenInPlace.cons(vbox);
		    return;
		} else {
		    currentOwner = inplaceWrite.orec;
		    continue;
		}
	    } else {
		if (boxesWritten == EMPTY_MAP) {
		    boxesWritten = new IdentityHashMap<VBox, Object>();
		}
		boxesWritten.put(vbox, value == null ? NULL_VALUE : value);
		return;
	    }
	} while (true);
    }

    @Override
    protected Transaction commitAndBeginTx(boolean readOnly) {
	commitTx(true);
	return beginWithActiveRecord(readOnly, null);
    }

    @Override
    protected void finish() {
	bodiesRead = null;
	boxesWritten = null;
	perTxValues = null;
	overwrittenAncestorWriteSet = null;
	boxesWrittenInPlace = null;
	mergedTxs = null;
	linearNestedOrecs = null;
    }

    @Override
    protected void doCommit() {
	tryCommit();

	bodiesRead = Cons.empty();
	boxesWritten = EMPTY_MAP;
	perTxValues = EMPTY_MAP;
	overwrittenAncestorWriteSet = Cons.empty();
	boxesWrittenInPlace = Cons.empty();
	mergedTxs = Cons.empty();
	linearNestedOrecs = Cons.empty();
    }

    @Override
    protected void tryCommit() {
	ReadWriteTransaction parent = getRWParent();
	// update parent's read-set
	parent.bodiesRead = this.bodiesRead;
	parent.next = this.next;

	// update parent's write-set

	// first, add boxesWritten to parent.  Warning: 'this.boxesWritten'
	// may overwrite values of vboxes in parent.boxesWrittenInPlace, so
	// care must be taken to check for this case.  Also we could have
	// written to this.boxesWritten as well as simultaneously to the
	// VBox.tempValue, so check for that as well.

	for (Map.Entry<VBox, Object> entry : this.boxesWritten.entrySet()) {
	    VBox vbox = entry.getKey();
	    Object value = entry.getValue();
	    if (vbox.inplace.orec.owner == this) {
		// if this nested also wrote in-place, then it was after this private write 
		continue;
	    } else {
		if (parent.boxesWritten == EMPTY_MAP) {
		    parent.boxesWritten = new IdentityHashMap<VBox, Object>();
		}
		parent.boxesWritten.put(vbox, value);
	    }
	}

	// pass the orecs of linear nested transactions
	orec.owner = parent;
	Cons<OwnershipRecord> linearNestedAlreadyMerged = parent.linearNestedOrecs.cons(this.orec);
	for (OwnershipRecord linearNestedToMerge : this.linearNestedOrecs) {
	    linearNestedToMerge.owner = parent;
	    linearNestedAlreadyMerged = linearNestedAlreadyMerged.cons(linearNestedToMerge);
	}
	parent.linearNestedOrecs = linearNestedAlreadyMerged;
	
	// pass parallel nested transactions
	Cons<ParallelNestedTransaction> txsAlreadyMerged = parent.mergedTxs;
	for (ParallelNestedTransaction mergedTx : mergedTxs) {
	    mergedTx.orec.owner = parent;
	    txsAlreadyMerged = txsAlreadyMerged.cons(mergedTx);
	}
	parent.mergedTxs = txsAlreadyMerged;
	
	// now, pass the boxes to the parent
	parent.boxesWrittenInPlace = this.boxesWrittenInPlace;

	if (parent.perTxValues == EMPTY_MAP) {
	    parent.perTxValues = perTxValues;
	} else {
	    parent.perTxValues.putAll(perTxValues);
	}
	parent.arraysRead = this.arraysRead;
	if (parent.arrayWrites == EMPTY_MAP) {
	    parent.arrayWrites = arrayWrites;
	    parent.arrayWritesCount = arrayWritesCount;
	} else {
	    // Propagate arrayWrites and correctly update the parent's arrayWritebacks counter
	    for (VArrayEntry<?> entry : arrayWrites.values()) {
		if (parent.arrayWrites.put(entry, entry) != null) continue;

		// Count number of writes to the array
		Integer writeCount = parent.arrayWritesCount.get(entry.array);
		if (writeCount == null) writeCount = 0;
		parent.arrayWritesCount.put(entry.array, writeCount + 1);
	    }
	}
    }
}
