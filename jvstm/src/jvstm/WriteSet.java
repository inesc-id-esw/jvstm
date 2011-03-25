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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/* This class contains information about the VBoxes modified by a transaction and the corresponding
 * new values.  It is used for two purposes: 1) to manage the concurrent write-back of VBox values;
 * and 2) to enable other (future) transactions to validate their own commits.
 *
 * Regarding the write-back mechanism the idea is as follows: the transaction's write-set is divided
 * into buckets, of approximately the same size each.  Any transaction can help to write-back, by
 * picking a unique bucket and copying the values of that bucket's VBoxes to their "public" place
 * (the body of the VBox).
 */
public class WriteSet {
    /* Represents the minimum number os VBoxes to include in the same block.  This value is used to
     * provide enough work to a helper thread and to avoid for example to give 1 box per thread to
     * write, which would be a waste of context switching effort.  The best value certainly depends
     * on the architecture where this code will run.
     */
    protected static final int MIN_BLOCK_SIZE = 10;
    /* We don't create more buckets than processors available.  We simply assume that more helper
     * threads than processors will actually not provide any additional help.
     */
    protected static final int N_PROCS = Runtime.getRuntime().availableProcessors();


    /* There are nBlocks of VBoxes ranged from 0 to nBlocks-1.  Any i-th block (except the last one)
     * manages VBoxes from i*blockSize (including) to i*blockSize+blockSize (excluding).  The last
     * block manages the range from i*blockSize (including) to writeSet.length (excluding). */
    protected final int nBlocks;
    /* The number of VBoxes per block */
    protected final int blockSize;
    /* All the VBoxes lined-up in a fixed array for direct access */
    protected final Map.Entry<VBox, Object> [] writeSet;
    /* The VBoxBodies created when writing-back each block of VBoxes */
    protected final Cons<VBoxBody> [] bodiesPerBlock;
    /* The atomic counter to uniquely distribute one block per request */
    protected final AtomicInteger nextBlock = new AtomicInteger(0);
    /* The atomic counter to find out who processed a block in last place */
    protected final AtomicInteger blocksDone = new AtomicInteger(0);

    protected WriteSet(Map<VBox, Object> boxesWritten) {
	this.writeSet = boxesWritten.entrySet().toArray(new Map.Entry[0]);
   	this.blockSize = decideBlockSize(writeSet.length);
 	int nBlocksAux = writeSet.length / blockSize;
	this.nBlocks = (nBlocksAux == 0 && writeSet.length > 0) ? 1 : nBlocksAux;
	this.bodiesPerBlock = new Cons[this.nBlocks];
    }

    private int decideBlockSize(int nBoxes) {
	// Lowest possible block size is 1, even if the write-set is empty.  This is to avoid a
	// division by zero.
	if (nBoxes == 0) {
	    return 1;
	}

	// if there are less boxes than MIN_BLOCK_SIZE then the blockSize will be equal to nBoxes
	// (to ensure at least one block)
	if (nBoxes < MIN_BLOCK_SIZE) {
	    return nBoxes;
	}

	int bs = nBoxes/N_PROCS;
	if (bs < MIN_BLOCK_SIZE) {
	    bs = MIN_BLOCK_SIZE;
	}

	return bs;
    }

    protected boolean helpWriteBack(int newTxNumber) {
	// getAndIncrement atomically ensures a unique block that no one else will be writing-back
	int block = this.nextBlock.getAndIncrement();
	while (block < this.nBlocks) {
	    this.bodiesPerBlock[block] = writeBackBlock(block, newTxNumber);
	    // Next, incrementing blocksDone enforces a barrier; it is done after setting the
	    // bodiesPerBlock, thus ensuring that all bodiesPerBlock are seen after the last block
	    // is written-back
	    if (blocksDone.incrementAndGet() == this.nBlocks) {
		return true;
	    }
	    block = this.nextBlock.getAndIncrement();
	}
	return false;
    }

    protected Cons<VBoxBody> writeBackBlock(int block, int newTxNumber) {
	int min = block*this.blockSize;
	// max depends on whether this is the last block
	int max = (block == (this.nBlocks - 1)) ? this.writeSet.length : (min + this.blockSize);

	Cons<VBoxBody> newBodies = Cons.empty();
	for (int i = min; i < max; i++) {
	    VBox vbox = this.writeSet[i].getKey();
	    Object newValue = this.writeSet[i].getValue();

	    VBoxBody newBody = vbox.commit((newValue == ReadWriteTransaction.NULL_VALUE) ? null : newValue, newTxNumber);
	    newBodies = newBodies.cons(newBody);
	}
	return newBodies;
    }

    protected static WriteSet empty() {
	return new WriteSet(ReadWriteTransaction.EMPTY_MAP);
    }
}
