package jvstm;

import java.util.concurrent.atomic.AtomicReference;

import jvstm.util.Cons;

public class NestedCommitRecord {

    private final ParallelNestedTransaction committer;
    private final Cons<ParallelNestedTransaction> children;
    private final Cons<ParallelNestedTransaction> parentOrecs;
    protected final AtomicReference<NestedCommitRecord> next = new AtomicReference<NestedCommitRecord>(null);
    protected final int commitNumber;
    protected volatile boolean recordCommitted = false;

    public static final NestedCommitRecord NESTED_SENTINEL_RECORD = new NestedCommitRecord();

    public NestedCommitRecord() {
	this.committer = null;
	this.children = Cons.empty();
	this.parentOrecs = Cons.empty();
	this.commitNumber = 0;
	this.recordCommitted = true;
    }

    public NestedCommitRecord(ParallelNestedTransaction committer, Cons<ParallelNestedTransaction> children,
	    Cons<ParallelNestedTransaction> parentOrecs, int commitNumber) {
	this.committer = committer;
	this.children = children;
	this.commitNumber = commitNumber;
	this.parentOrecs = parentOrecs;
    }

    public void helpCommit() {
	ReadWriteTransaction parent = committer.getRWParent();

	commitOrec(parent, committer.orec);

	Cons<ParallelNestedTransaction> currentParentOrecs = parent.mergedTxs;
	if (currentParentOrecs == parentOrecs) {
	    for (ParallelNestedTransaction childrenCommit : children) {
		commitOrec(parent, childrenCommit.orec);
		currentParentOrecs = currentParentOrecs.cons(childrenCommit);
	    }
	    parent.CASmergedTxs(parentOrecs, currentParentOrecs);
	}

	// Arrays and PerTxBoxes are not yet supported in the lock-free nested
	// commit
	// if (parent.perTxValues == EMPTY_MAP) {
	// parent.perTxValues = perTxValues;
	// } else {
	// parent.perTxValues.putAll(perTxValues);
	// }
	//
	// if (parent.arrayWrites == EMPTY_MAP) {
	// parent.arrayWrites = arrayWrites;
	// parent.arrayWritesCount = arrayWritesCount;
	// for (VArrayEntry<?> entry : arrayWrites.values()) {
	// entry.nestedVersion = commitVersion;
	// }
	// } else {
	// // Propagate arrayWrites and correctly update the parent's
	// // arrayWritebacks counter
	// for (VArrayEntry<?> entry : arrayWrites.values()) {
	// // Update the array write entry nested version
	// entry.nestedVersion = commitVersion;
	//
	// if (parent.arrayWrites.put(entry, entry) != null)
	// continue;
	//
	// // Count number of writes to the array
	// Integer writeCount = parent.arrayWritesCount.get(entry.array);
	// if (writeCount == null)
	// writeCount = 0;
	// parent.arrayWritesCount.put(entry.array, writeCount + 1);
	// }
	// }

    }

    private void commitOrec(ReadWriteTransaction parent, OwnershipRecord committerOrec) {
	int currentNestedVersion = committerOrec.nestedVersion;
	ReadWriteTransaction currentOwner = committerOrec.owner;
	if (currentNestedVersion < commitNumber) {
	    committerOrec.CASnestedVersion(currentNestedVersion, commitNumber);
	}
	if (currentOwner == committer) {
	    committerOrec.CASowner(committer, parent);
	}
    }
}
