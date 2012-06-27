package jvstm;

import java.util.HashMap;

import jvstm.util.Cons;

/**
 * This type of transaction is meant for usage when one parallelizes a transaction 
 * into subparts that are disjoint. This means that an UnsafeParallelTransaction 
 * never aborts because of a conflict with is siblings.
 * @author nmld
 *
 */
public class UnsafeParallelTransaction extends ParallelNestedTransaction {

    protected final OwnershipRecord parentOrec;

    public UnsafeParallelTransaction(ReadWriteTransaction parent) {
	super(parent, true);
	this.parentOrec = parent.orec;
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
    protected void tryCommit() {
	ReadWriteTransaction parent = getRWParent();
	synchronized (parent) {
	    int commitVersion = parent.nestedVersion;
	    orec.nestedVersion = commitVersion;
	    orec.owner = parent;
	    Cons<ParallelNestedTransaction> txsAlreadyMerged = parent.mergedTxs.cons(this);

	    parent.mergedTxs = txsAlreadyMerged;
	}
	Transaction.current.set(null);
    }

    @Override
    protected Transaction commitAndBeginTx(boolean readOnly) {
	throw new Error("Unsafe Parallel Transaction cannot use 'commit and begin'");
    }

}
