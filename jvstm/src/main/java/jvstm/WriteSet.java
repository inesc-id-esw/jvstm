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

import gnu.trove.impl.hash.TObjectHash;
import gnu.trove.map.hash.THashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import jvstm.util.Cons;
import jvstm.util.Pair;

/* This class contains information about the VBoxes modified by a transaction and the corresponding
 * new values.  It is used for two purposes: 1) to manage the concurrent write-back of VBox values;
 * and 2) to enable other (future) transactions to validate their own commits.
 *
 * Regarding the write-back mechanism the idea is as follows: the transaction's write-set is divided
 * into buckets, of approximately the same size each.  Any transaction can help to write-back, by
 * picking a unique bucket and copying the values of that bucket's VBoxes to their "public" place
 * (the body of the VBox).
 */
public final class WriteSet {
    private static final ThreadLocal<Random> random = new ThreadLocal<Random>() {
	@Override
	protected Random initialValue() {
	    return new Random();
	}
    };

    /*
     * Represents the default number os VBoxes to include in the same block.
     * This value is used to provide enough work to a helper thread and to avoid
     * for example to give 1 box per thread to write, which would be a waste of
     * context switching effort. The best value certainly depends on the
     * architecture where this code will run.
     */
    protected static final int DEFAULT_BLOCK_SIZE = 10;

    /*
     * There are nBlocks of VBoxes ranged from 0 to nBlocks-1. Any i-th block
     * (except the last one) manages VBoxes from i*blockSize (including) to
     * i*blockSize+blockSize (excluding). The last block manages the range from
     * i*blockSize (including) to writeSetLength (excluding).
     */
    protected final int nBlocks;
    /* The number of VBoxes per block */
    protected final int blockSize;
    /* All the VBoxes lined-up in a fixed array for direct access */
    protected final Object[] allWrittenVBoxes;
    protected final Object[] allWrittenValues;
    // the previous arrays may be allocated in a larger size than required.
    // This happens when the same box is first written to the standard
    // write-set and later re-written in-place. For this reason we should
    // never use the arrays 'length' attribute; use instead writeSetLength.
    protected final int writeSetLength;

    /* The VBoxBodies created when writing-back each block of VBoxes */
    protected final Cons<GarbageCollectable>[] bodiesPerBlock;
    /* A write-back status for each bucket */
    protected final AtomicBoolean[] blocksDone;

    /* Support for VArray */
    protected final VArrayCommitState[] arrayCommitState;

    /* Support for PerTxBoxes */
    protected final Map<PerTxBox, Object> perTxBoxesWrites;
    protected Map<VBox, Object> boxesWrittenDueToPerTxBoxes;
    protected final ReadWriteTransaction committer;
    protected final Map<VBox, Object> writeSetToCommit;

    protected WriteSet(Cons<ParallelNestedTransaction> mergedTxs, Cons<VBox> boxesWrittenInPlace, Map<VBox, Object> otherBoxes,
	    Map<VArrayEntry<?>, VArrayEntry<?>> arrayWrites, Map<VArray<?>, Integer> arrayWritesCount,
	    ReadWriteTransaction committer, Map<PerTxBox, Object> perTxBoxesWrites) {
	this(mergedTxs, boxesWrittenInPlace, otherBoxes, arrayWrites, arrayWritesCount, committer, perTxBoxesWrites,
		DEFAULT_BLOCK_SIZE);
    }

    protected WriteSet(Cons<ParallelNestedTransaction> mergedTxs, Cons<VBox> boxesWrittenInPlace, Map<VBox, Object> otherBoxes,
	    Map<VArrayEntry<?>, VArrayEntry<?>> arrayWrites, Map<VArray<?>, Integer> arrayWritesCount,
	    ReadWriteTransaction committer, Map<PerTxBox, Object> perTxBoxesWrites, int blockSize) {

	this.perTxBoxesWrites = perTxBoxesWrites;
	this.committer = committer;

	int boxesWrittenInPlaceSize = boxesWrittenInPlace.size();
	for (ParallelNestedTransaction mergedTx : mergedTxs) {
	    boxesWrittenInPlaceSize += mergedTx.boxesWrittenInPlace.size();
	}
	int maxRequiredSize = boxesWrittenInPlaceSize + otherBoxes.size();

	if (this.perTxBoxesWrites != ReadWriteTransaction.EMPTY_MAP) {
	    this.writeSetToCommit = new THashMap<VBox, Object>(maxRequiredSize);

	    for (VBox vbox : boxesWrittenInPlace) {
		this.writeSetToCommit.put(vbox, vbox.inplace.tempValue);
		vbox.inplace.next = null;
	    }
	    for (ParallelNestedTransaction mergedTx : mergedTxs) {
		for (VBox vbox : mergedTx.boxesWrittenInPlace) {
		    this.writeSetToCommit.put(vbox, vbox.inplace.tempValue);
		    vbox.inplace.next = null;
		}
	    }

	    for (Map.Entry<VBox, Object> entry : otherBoxes.entrySet()) {
		VBox vbox = entry.getKey();
		if (vbox.inplace.orec.owner == committer) {
		    // if we also wrote directly to the box, we just skip this
		    // value
		    continue;
		}
		this.writeSetToCommit.put(vbox, entry.getValue());
	    }
	    this.allWrittenVBoxes = ((THashMap<VBox, Object>) this.writeSetToCommit)._set;
	    this.allWrittenValues = ((THashMap<VBox, Object>) this.writeSetToCommit)._values;
	    this.writeSetLength = this.allWrittenVBoxes.length;
	} else {
	    this.writeSetToCommit = null;
	    this.allWrittenVBoxes = new VBox[maxRequiredSize];
	    this.allWrittenValues = new Object[maxRequiredSize];
	    int pos = 0;

	    for (VBox vbox : boxesWrittenInPlace) {
		this.allWrittenVBoxes[pos] = vbox;
		this.allWrittenValues[pos++] = vbox.inplace.tempValue;
		vbox.inplace.next = null;
	    }
	    for (ParallelNestedTransaction mergedTx : mergedTxs) {
		for (VBox vbox : mergedTx.boxesWrittenInPlace) {
		    this.allWrittenVBoxes[pos] = vbox;
		    this.allWrittenValues[pos++] = vbox.inplace.tempValue;
		    vbox.inplace.next = null;
		}
	    }

	    for (Map.Entry<VBox, Object> entry : otherBoxes.entrySet()) {
		VBox vbox = entry.getKey();
		if (vbox.inplace.orec.owner == committer) {
		    // if we also wrote directly to the box, we just skip this
		    // value
		    continue;
		}
		this.allWrittenVBoxes[pos] = vbox;
		this.allWrittenValues[pos++] = entry.getValue();
	    }
	    this.writeSetLength = pos;
	}

	this.blockSize = blockSize;
	int nBlocksAux = writeSetLength / blockSize;
	this.nBlocks = (nBlocksAux == 0 && writeSetLength > 0) ? 1 : nBlocksAux;
	this.bodiesPerBlock = new Cons[this.nBlocks + arrayWritesCount.size()
		+ (perTxBoxesWrites == ReadWriteTransaction.EMPTY_MAP ? 0 : 1)];
	this.blocksDone = new AtomicBoolean[this.nBlocks + (perTxBoxesWrites == ReadWriteTransaction.EMPTY_MAP ? 0 : 1)];
	for (int i = 0; i < this.nBlocks + (perTxBoxesWrites == ReadWriteTransaction.EMPTY_MAP ? 0 : 1); i++) {
	    this.blocksDone[i] = new AtomicBoolean(false);
	}

	this.arrayCommitState = prepareArrayWrites(arrayWrites, arrayWritesCount);

    }

    // This constructor is used by InevitableTransactions. It is simpler
    // because we know that everything was already written in place. There is
    // only one bucket and it will already be written-back. The purpose is
    // that when any transaction tries to helpWriteBack will simply quickly
    // return and continue its work.
    protected WriteSet(Cons<VBox> vboxesWrittenBack) {
	this.writeSetLength = vboxesWrittenBack.size();

	this.nBlocks = 1;
	this.blockSize = this.writeSetLength;
	this.allWrittenVBoxes = new VBox[this.writeSetLength];
	this.allWrittenValues = new Object[this.writeSetLength];
	this.bodiesPerBlock = new Cons[this.nBlocks];
	this.blocksDone = new AtomicBoolean[this.nBlocks];

	int pos = 0;
	Cons<GarbageCollectable> bodiesCommitted = Cons.empty();
	for (VBox vbox : vboxesWrittenBack) {
	    this.allWrittenVBoxes[pos] = vbox;
	    this.allWrittenValues[pos++] = vbox.body.value;
	    bodiesCommitted = bodiesCommitted.cons(vbox.body);
	}
	this.bodiesPerBlock[0] = bodiesCommitted;
	this.blocksDone[0] = new AtomicBoolean(true);

	this.arrayCommitState = new VArrayCommitState[0];

	this.perTxBoxesWrites = ReadWriteTransaction.EMPTY_MAP;
	this.committer = null;
	this.writeSetToCommit = null;
    }

    protected final void helpWriteBack(int newTxNumber) {
	if (this.perTxBoxesWrites != ReadWriteTransaction.EMPTY_MAP && this.boxesWrittenDueToPerTxBoxes == null) {
	    CommitTimeTransaction commitTx = new CommitTimeTransaction(this.committer);

	    try {
		for (Map.Entry<PerTxBox, Object> entry : this.perTxBoxesWrites.entrySet()) {
		    entry.getKey().commit(entry.getValue());
		}

		// This commit of perTxBoxes always produces the same set of
		// writes in a 'CommitTimeTransaction', because such txs use
		// the same baseline writeSet from the committing tx, and
		// read VBoxBodies with the same version.
		this.boxesWrittenDueToPerTxBoxes = commitTx.finishExecution();

	    } catch (StopPerTxBoxesCommitException e) {
		// A read over a VBox detected that some other helper already
		// did all this.
		commitTx.finishEarly();

		// Sanity check to ensure the GC is bug-free
		if (!this.blocksDone[this.blocksDone.length - 1].get()) {
		    throw new Error(
			    "GC algorithm Error: A version was collected; yet, a transaction being committed (but not yet committed) could still read it!");
		}
	    }
	}

	// Write-back the writes produced by committing the PerTxBoxes.
	// These must be run before the write-back of the "normal" writes, so
	// that any overwrite produced in the PerTxBoxes makes it to the
	// VBoxBodies first. This means that next, if some write-back is
	// performed on a VBox that was also written inside a PerTxBox, then
	// that write will be ignored and will not make it to the VBoxBodies.
	if (boxesWrittenDueToPerTxBoxes != null && !this.blocksDone[this.blocksDone.length - 1].get()) {
	    Cons<GarbageCollectable> newBodies = Cons.empty();
	    for (Map.Entry<VBox, Object> entryPerTxBoxWriteSet : boxesWrittenDueToPerTxBoxes.entrySet()) {
		VBox vbox = entryPerTxBoxWriteSet.getKey();
		Object newValue = entryPerTxBoxWriteSet.getValue();

		VBoxBody newBody = vbox.commit((newValue == ReadWriteTransaction.NULL_VALUE) ? null : newValue, newTxNumber);
		newBodies = newBodies.cons(newBody);
	    }
	    this.bodiesPerBlock[this.bodiesPerBlock.length - 1] = newBodies;
	    this.blocksDone[this.blocksDone.length - 1].set(true);
	}

	if (this.nBlocks > 0) {
	    int finalBlock = random.get().nextInt(this.nBlocks); // start at a
								 // random
								 // position
	    int currentBlock = finalBlock;
	    do {
		if (!this.blocksDone[currentBlock].get()) {
		    // smf: is this safe? multiple helping threads will write to
		    // this location, but since java
		    // does not allow values out of the blue, we will always
		    // have a reference to a Cons with the
		    // required bodies created, right?
		    this.bodiesPerBlock[currentBlock] = writeBackBlock(currentBlock, newTxNumber);
		    this.blocksDone[currentBlock].set(true);
		}
		currentBlock = (currentBlock + 1) % this.nBlocks;
	    } while (currentBlock != finalBlock);
	}

	// Writeback to arrays
	// This uses locking, but locks are only used if the current transaction
	// WROTE to an array
	//
	// Algorithm:
	// - All threads tryLock each array: if they succeed, they writeback to
	// the array, otherwise they
	// move on to the other arrays.
	// - Because no thread should leave this method until all arrays are
	// written back, a second pass is
	// done, locking each array again, and checking that writeback is
	// complete -- it might not be, if
	// tryLock failed because a thread helping an older commit was still
	// holding the lock. This way,
	// only after all arrays are written back can the thread continue.
	if (this.arrayCommitState.length > 0) {
	    for (int i = 0; i < this.arrayCommitState.length; i++) {
		VArrayCommitState cs = this.arrayCommitState[i];
		if (cs.array.writebackLock.tryLock())
		    try {
			if (cs.array.version >= newTxNumber)
			    continue;
			this.bodiesPerBlock[this.nBlocks + i] = cs.doWriteback(newTxNumber);
		    } finally {
			cs.array.writebackLock.unlock();
		    }
	    }

	    // This loop is SIMILAR but not THE SAME as the one above: it uses
	    // lock instead of tryLock
	    for (int i = 0; i < this.arrayCommitState.length; i++) {
		VArrayCommitState cs = this.arrayCommitState[i];
		cs.array.writebackLock.lock();
		try {
		    if (cs.array.version >= newTxNumber)
			continue;
		    this.bodiesPerBlock[this.nBlocks + i] = cs.doWriteback(newTxNumber);
		} finally {
		    cs.array.writebackLock.unlock();
		}
	    }
	}
    }

    protected final Cons<GarbageCollectable> writeBackBlock(int block, int newTxNumber) {
	int min = block * this.blockSize;
	// max depends on whether this is the last block
	int max = (block == (this.nBlocks - 1)) ? this.writeSetLength : (min + this.blockSize);

	Cons<GarbageCollectable> newBodies = Cons.empty();
	if (this.perTxBoxesWrites != ReadWriteTransaction.EMPTY_MAP) {
	    for (int i = min; i < max; i++) {
		Object obj = allWrittenVBoxes[i];
		if (obj == TObjectHash.FREE || obj == TObjectHash.REMOVED) {
		    continue;
		}
		VBox vbox = (VBox) obj;
		Object newValue = this.allWrittenValues[i];

		VBoxBody newBody = vbox.commit((newValue == ReadWriteTransaction.NULL_VALUE) ? null : newValue, newTxNumber);
		newBodies = newBodies.cons(newBody);
	    }
	} else {
	    VBox[] allWrittenVBoxes = (VBox[]) this.allWrittenVBoxes;
	    for (int i = min; i < max; i++) {
		VBox vbox = allWrittenVBoxes[i];
		Object newValue = this.allWrittenValues[i];

		VBoxBody newBody = vbox.commit((newValue == ReadWriteTransaction.NULL_VALUE) ? null : newValue, newTxNumber);
		newBodies = newBodies.cons(newBody);
	    }
	}

	return newBodies;
    }

    protected final int size() {
	return this.writeSetLength;
    }

    protected static WriteSet empty() {
	return new WriteSet(Cons.<ParallelNestedTransaction> empty(), Cons.<VBox> empty(), ReadWriteTransaction.EMPTY_MAP,
		ReadWriteTransaction.EMPTY_MAP, ReadWriteTransaction.EMPTY_MAP, null, ReadWriteTransaction.EMPTY_MAP);
    }

    static final class VArrayCommitState {
	final VArray<?> array;
	final VArrayEntry<?>[] writesToCommit;
	final int[] logEntryIndexes;

	VArrayCommitState(VArray<?> array, VArrayEntry<?>[] writesToCommit, int[] logEntryIndexes) {
	    this.array = array;
	    this.writesToCommit = writesToCommit;
	    this.logEntryIndexes = logEntryIndexes;
	}

	private Cons<GarbageCollectable> doWriteback(int newTxNumber) {
	    GarbageCollectable newLogNode = array.commit(newTxNumber, writesToCommit, logEntryIndexes);
	    return Cons.<GarbageCollectable> empty().cons(newLogNode);
	}
    }

    private VArrayCommitState[] prepareArrayWrites(Map<VArrayEntry<?>, VArrayEntry<?>> arrayWrites,
	    Map<VArray<?>, Integer> arrayWritesCount) {
	if (arrayWrites.isEmpty())
	    return new VArrayCommitState[0];

	// During commit, arrayWritebacks keeps the write-set divided into
	// per-array lists
	Map<VArray<?>, Pair<VArrayEntry<?>[], Integer>> arrayWritebacks = new HashMap<VArray<?>, Pair<VArrayEntry<?>[], Integer>>(
		arrayWritesCount.size());

	for (Map.Entry<VArray<?>, Integer> entry : arrayWritesCount.entrySet()) {
	    arrayWritebacks.put(entry.getKey(), new Pair<VArrayEntry<?>[], Integer>(new VArrayEntry[entry.getValue()], 0));
	}

	VArray<?> lastArray = null;
	Pair<VArrayEntry<?>[], Integer> lastArrayEntries = null;
	VArrayCommitState[] commitState = new VArrayCommitState[arrayWritesCount.size()];
	int nextCommitStatePos = 0;

	// Split array write-set into per-array lists
	for (VArrayEntry<?> entry : arrayWrites.values()) {
	    VArray<?> array = entry.array;

	    if (array != lastArray) {
		lastArray = array;
		lastArrayEntries = arrayWritebacks.get(array);
	    }

	    // Don't ask. Just try to do lastArrayEntries.second++ and you'll
	    // see what I mean
	    int pos = ++lastArrayEntries.second - 1;
	    lastArrayEntries.first[pos] = entry;

	    if (lastArrayEntries.first.length == pos + 1) { // We have all the
							    // writes for the
							    // current array
		VArrayEntry<?>[] writesToCommit = lastArrayEntries.first;

		// Sort entries
		java.util.Arrays.sort(writesToCommit);

		// Create logEntryIndexes to be used in the log
		int[] logEntryIndexes = new int[writesToCommit.length];
		for (int i = 0; i < writesToCommit.length; i++) {
		    logEntryIndexes[i] = writesToCommit[i].index;
		}

		commitState[nextCommitStatePos++] = new VArrayCommitState(array, writesToCommit, logEntryIndexes);
	    }
	}

	return commitState;
    }
}
