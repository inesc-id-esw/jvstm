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
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

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
        @Override protected Random initialValue() {
            return new Random();
        }
    };

    /* Represents the default number os VBoxes to include in the same block.  This value is used to
     * provide enough work to a helper thread and to avoid for example to give 1 box per thread to
     * write, which would be a waste of context switching effort.  The best value certainly depends
     * on the architecture where this code will run.
     */
    protected static final int DEFAULT_BLOCK_SIZE = 10;

    /* There are nBlocks of VBoxes ranged from 0 to nBlocks-1.  Any i-th block (except the last one)
     * manages VBoxes from i*blockSize (including) to i*blockSize+blockSize (excluding).  The last
     * block manages the range from i*blockSize (including) to writeSetLength (excluding). */
    protected final int nBlocks;
    /* The number of VBoxes per block */
    protected final int blockSize;
    /* All the VBoxes lined-up in a fixed array for direct access */
    // protected final Map.Entry<VBox, Object> [] writeSet;
    protected final VBox [] allWrittenVBoxes;
    protected final Object [] allWrittenValues;
    // the previous arrays may be allocated in a larger size than required.
    // This happens when the same box is first written to the standard
    // write-set and later re-written in-place.  For this reason we should
    // never use the arrays 'length' attribute; use instead writeSetLength.
    protected final int writeSetLength;

    /* The VBoxBodies created when writing-back each block of VBoxes */
    protected final Cons<VBoxBody> [] bodiesPerBlock;
    /* A write-back status for each bucket */
    protected final AtomicBoolean [] blocksDone;

    protected WriteSet(Cons<VBox> boxesWrittenInPlace, Map<VBox, Object> otherBoxes, OwnershipRecord myOrec) {
        this(boxesWrittenInPlace, otherBoxes, myOrec, DEFAULT_BLOCK_SIZE);
    }

    protected WriteSet(Cons<VBox> boxesWrittenInPlace, Map<VBox, Object> otherBoxes, OwnershipRecord myOrec, int blockSize) {
        int boxesWrittenInPlaceSize = boxesWrittenInPlace.size();

        int maxRequiredSize = boxesWrittenInPlaceSize + otherBoxes.size();
        this.allWrittenVBoxes = new VBox[maxRequiredSize];
        this.allWrittenValues = new Object[maxRequiredSize];

        int pos = 0;
        for (VBox vbox : boxesWrittenInPlace) {
            this.allWrittenVBoxes[pos] = vbox;
            this.allWrittenValues[pos++] = vbox.tempValue;
        }

        for (Map.Entry<VBox, Object> entry : otherBoxes.entrySet()) {
            VBox vbox = entry.getKey();
            if (vbox.currentOwner == myOrec) { // if we also wrote directly to the box, we just skip this value
                continue;
            }
            this.allWrittenVBoxes[pos] = vbox;
            this.allWrittenValues[pos++] = entry.getValue();
        }
        this.writeSetLength = pos;

        this.blockSize = blockSize;
        int nBlocksAux = writeSetLength / blockSize;
        this.nBlocks = (nBlocksAux == 0 && writeSetLength > 0) ? 1 : nBlocksAux;
        this.bodiesPerBlock = new Cons[this.nBlocks];
        this.blocksDone = new AtomicBoolean[this.nBlocks];
        for (int i = 0; i < this.nBlocks; i++) {
            this.blocksDone[i] = new AtomicBoolean(false);
        }
    }

    // This constructor is used by InevitableTransactions. It is simpler
    // because we know that everything was already written in place.  There is
    // only one bucket and it will already be written-back.  The purpose is
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
        Cons<VBoxBody> bodiesCommitted = Cons.empty();
        for (VBox vbox : vboxesWrittenBack) {
            this.allWrittenVBoxes[pos] = vbox;
            this.allWrittenValues[pos++] = vbox.body.value;
            bodiesCommitted = bodiesCommitted.cons(vbox.body);
        }
        this.bodiesPerBlock[0] = bodiesCommitted;
        this.blocksDone[0] = new AtomicBoolean(true);
    }

    protected final void helpWriteBack(int newTxNumber) {
        if (this.nBlocks == 0) return;  // there was really nothing to be done

        int finalBlock = random.get().nextInt(this.nBlocks); // start at a random position
        int currentBlock = finalBlock;
        do {
            if (!this.blocksDone[currentBlock].get()) {
                // smf: is this safe?  multiple helping threads will write to this location, but since java
                // does not allow values out of the blue, we will always have a reference to a Cons with the
                // required bodies created, right?
                this.bodiesPerBlock[currentBlock] = writeBackBlock(currentBlock, newTxNumber);
                this.blocksDone[currentBlock].set(true);
            }
            currentBlock = (currentBlock + 1) % this.nBlocks;
        } while (currentBlock != finalBlock);
    }

    protected final Cons<VBoxBody> writeBackBlock(int block, int newTxNumber) {
        int min = block*this.blockSize;
        // max depends on whether this is the last block
        int max = (block == (this.nBlocks - 1)) ? this.writeSetLength : (min + this.blockSize);

        Cons<VBoxBody> newBodies = Cons.empty();
        for (int i = min; i < max; i++) {
            VBox vbox = this.allWrittenVBoxes[i];
            Object newValue = this.allWrittenValues[i];

            VBoxBody newBody = vbox.commit((newValue == ReadWriteTransaction.NULL_VALUE) ? null : newValue, newTxNumber);
            newBodies = newBodies.cons(newBody);
        }
        return newBodies;
    }

    protected final int size() {
        return this.writeSetLength;
    }
    
    protected static WriteSet empty() {
        return new WriteSet(Cons.<VBox>empty(), ReadWriteTransaction.EMPTY_MAP, null);
    }
}
