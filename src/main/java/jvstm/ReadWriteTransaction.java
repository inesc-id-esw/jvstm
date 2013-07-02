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

import static jvstm.UtilUnsafe.UNSAFE;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import jvstm.util.Cons;

public abstract class ReadWriteTransaction extends Transaction {

    protected static final Object NULL_VALUE = new Object();

    protected static final int[] EMPTY_VERSIONS = new int[0];
    protected static final VBox[] EMPTY_WRITE_SET = new VBox[0];
    protected static final Map EMPTY_MAP = Collections.emptyMap();

    protected static final ThreadLocal<Cons<VBox[]>> pool = new ThreadLocal<Cons<VBox[]>>() {
        @Override
        public Cons<VBox[]> initialValue() {
            return Cons.empty();
        }
    };

    protected static void returnToPool(VBox[] array) {
        pool.set(pool.get().cons(array));
    }

    private static VBox[] borrowFromPool() {
        Cons<VBox[]> available = pool.get();
        if (available.isEmpty()) {
            VBox[] newArray = new VBox[1000];
            return newArray;
        } else {
            pool.set(available.rest());
            return available.first();
        }
    }

    protected Cons<VBox[]> bodiesRead = Cons.empty();
    protected Cons<VArrayEntry<?>> arraysRead = Cons.empty();
    protected int next = -1;
    protected Map<VBox, Object> boxesWritten = EMPTY_MAP;
    protected Cons<VBox> boxesWrittenInPlace = Cons.empty();
    protected Map<PerTxBox, Object> perTxValues = EMPTY_MAP;
    protected Map<VArrayEntry<?>, VArrayEntry<?>> arrayWrites = EMPTY_MAP;
    protected Map<VArray<?>, Integer> arrayWritesCount = EMPTY_MAP;
    protected OwnershipRecord orec = new OwnershipRecord(this);
    public Cons<ParallelNestedTransaction> mergedTxs = Cons.empty();
    protected Cons<OwnershipRecord> linearNestedOrecs = Cons.empty();
    protected int[] ancVersions;

    protected volatile NestedCommitRecord nestedCommitQueue = new NestedCommitRecord();

    public ReadWriteTransaction(int number) {
        super(number);
        this.ancVersions = EMPTY_VERSIONS;
    }

    public ReadWriteTransaction(ReadWriteTransaction parent) {
        super(parent);
    }

    @Override
    public Transaction makeNestedTransaction(boolean readOnly) {
        // always create a RW nested transaction, because we need its read-set
        return new NestedTransaction(this);
    }

    @Override
    public Transaction makeParallelNestedTransaction(boolean readOnly) {
        if (readOnly) {
            return new ParallelNestedReadOnlyTransaction(this);
        } else {
            return new ParallelNestedTransaction(this);
        }
    }

    ReadWriteTransaction getRWParent() {
        return (ReadWriteTransaction) getParent();
    }

    @Override
    public void abortTx() {
        this.orec.version = OwnershipRecord.ABORTED;
        for (OwnershipRecord linearMergedOrec : linearNestedOrecs) {
            linearMergedOrec.version = OwnershipRecord.ABORTED;
        }
        for (ParallelNestedTransaction mergedTx : mergedTxs) {
            mergedTx.orec.version = OwnershipRecord.ABORTED;
        }
        super.abortTx();
    }

    @Override
    protected void finish() {
        super.finish();
        for (VBox[] array : bodiesRead) {
            returnToPool(array);
        }

        // to allow garbage collecting the collections
        bodiesRead = null;
        arraysRead = null;
        boxesWritten = null;
        boxesWrittenInPlace = null;
        perTxValues = null;
        arrayWrites = null;
        arrayWritesCount = null;
        cleanUp();
    }

    protected void cleanUp() {
        if (mergedTxs != Cons.<ParallelNestedTransaction> empty()) {
            for (ParallelNestedTransaction mergedTx : mergedTxs) {
                mergedTx.cleanUp();
            }
            mergedTxs = null;
        }
    }

    @Override
    protected void doCommit() {
        tryCommit();
        // if commit is successful, then reset transaction to a clean state
        for (VBox[] array : bodiesRead) {
            returnToPool(array);
        }

        bodiesRead = Cons.empty();
        arraysRead = Cons.empty();
        boxesWritten = EMPTY_MAP;
        boxesWrittenInPlace = Cons.empty();
        perTxValues = EMPTY_MAP;
        arrayWrites = EMPTY_MAP;
        arrayWritesCount = EMPTY_MAP;
    }

    protected abstract void tryCommit();

    protected <T> T getLocalValue(VBox<T> vbox) {
        InplaceWrite<T> inplace = vbox.inplace;
        if (inplace.orec.owner == this) {
            return inplace.tempValue;
        }

        T value = null;
        if (boxesWritten != EMPTY_MAP) {
            value = (T)boxesWritten.get(vbox);
        }
        if ((value == null) && (parent != null)) {
            value = getRWParent().getLocalValue(vbox);
        }

        return value;
    }

    protected <T> T readFromBody(VBox<T> vbox) {
        VBoxBody<T> body = vbox.body;

        if (body!= null && body.version > number) {
            body = newerVersionDetected(body);
        }

        addToReadSet(vbox);

        if (body == null) {
            /*
             * The object (vbox) is in the compact layout (of the AOM) and the
             * vbox itself corresponds to the most recent committed version.
             */
            return (T) vbox;
        } else {
            return getValueFromBody(vbox, body);
        }
    }

    protected <T> VBoxBody<T> newerVersionDetected(VBoxBody<T> body) {
        // signal early transaction abort
        TransactionSignaller.SIGNALLER.signalEarlyAbort();
        throw new AssertionError("Impossible condition - Commit fail signalled!");
    }

    protected <T> void addToReadSet(VBox<T> vbox) {
        VBox[] readset = null;
        if (next < 0) {
            readset = borrowFromPool();
            next = readset.length - 1;
            bodiesRead = bodiesRead.cons(readset);
        } else {
            readset = bodiesRead.first();
        }
        readset[next--] = vbox;
    }

    // The vbox argument is needed in subclasses
    protected <T> T getValueFromBody(VBox<T> vbox, VBoxBody<T> body) {
        return body.value;
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
            return readFromBody(vbox);
        } else {
            T value = getLocalValue(vbox);
            if (value == null) { // no local value exists
                return readFromBody(vbox);
            }
            // else
            return (value == NULL_VALUE) ? null : value;
        }
    }

    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
        InplaceWrite<T> inplaceWrite = vbox.inplace;
        OwnershipRecord currentOwner = inplaceWrite.orec;
        if (currentOwner.owner == this) { // we are already the current writer
            inplaceWrite.tempValue = (value == null ? (T) NULL_VALUE : value);
            return;
        }

        // the next loop ends either when we succeed in writing directly to the
        // vbox or fallback to using the standard
        // write-set
        do {
            /*
             * When there is no previous writer or the previous writer either is
             * aborted or committed with a version not greater than ours we try
             * to gain ownership of the vbox. If we succeed we write to the vbox
             * otherwise we retry.
             *
             * Otherwise (there is a previous writer still running or committed
             * in a version greater than ours) we fallback to the standard
             * write-set
             */
            if (currentOwner.version != 0 && currentOwner.version <= this.number) {
                if (inplaceWrite.CASowner(currentOwner, this.orec)) {
                    // note: it is possible that a second invocation of
                    // setBoxValue in the same transaction will end up
                    // here after writing to the normal write-set. This case is
                    // accounted for when creating the
                    // WriteSet at commit time
                    inplaceWrite.tempValue = (value == null ? (T) NULL_VALUE : value);
                    boxesWrittenInPlace = boxesWrittenInPlace.cons(vbox);
                    return; // break
                } else {
                    // update the current owner and retry
                    currentOwner = inplaceWrite.orec;
                    continue;
                }
            } else { // fallback to the standard write-set
                // note: here we could consider the special case when the
                // other writer is committed with a version
                // greater than ours and either abort or try to upgrade the
                // transaction
                if (boxesWritten == EMPTY_MAP) {
                    boxesWritten = new IdentityHashMap<VBox, Object>();
                }
                boxesWritten.put(vbox, value == null ? NULL_VALUE : value);
                return; // break
            }
        } while (true);
    }

    protected <T> T getPerTxValue(PerTxBox<T> box) {
        T value = null;
        if (perTxValues != EMPTY_MAP) {
            value = (T) perTxValues.get(box);
        }
        if ((value == null) && (parent != null)) {
            value = getRWParent().getPerTxValue(box);
        }
        return value;
    }

    @Override
    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
        T value = getPerTxValue(box);
        if (value == null) {
            value = initial;
        }

        return value;
    }

    @Override
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
        if (perTxValues == EMPTY_MAP) {
            perTxValues = new IdentityHashMap<PerTxBox, Object>();
        }
        perTxValues.put(box, value);
    }

    protected <T> T getLocalArrayValue(VArrayEntry<T> entry) {
        T value = null;
        if (arrayWrites != EMPTY_MAP) {
            VArrayEntry<T> wsEntry = (VArrayEntry<T>) arrayWrites.get(entry);
            if (wsEntry != null) {
                value = (wsEntry.getWriteValue() == null ? (T) NULL_VALUE : wsEntry.getWriteValue());
            }
        }
        if ((value == null) && (parent != null)) {
            value = getRWParent().getLocalArrayValue(entry);
        }

        return value;
    }

    @Override
    public <T> T getArrayValue(VArrayEntry<T> entry) {
        T value = getLocalArrayValue(entry);
        if (value == null) {
            value = entry.getValue(number);
            arraysRead = arraysRead.cons(entry);
        }
        return (value == NULL_VALUE) ? null : value;
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
        if (arrayWrites == EMPTY_MAP) {
            arrayWrites = new IdentityHashMap<VArrayEntry<?>, VArrayEntry<?>>();
            arrayWritesCount = new IdentityHashMap<VArray<?>, Integer>();
        }
        entry.setWriteValue(value, this.nestedCommitQueue.commitNumber);
        if (arrayWrites.put(entry, entry) != null) {
            return;
        }

        // Count number of writes to the array
        Integer writeCount = arrayWritesCount.get(entry.array);
        if (writeCount == null) {
            writeCount = 0;
        }
        arrayWritesCount.put(entry.array, writeCount + 1);
    }

    protected void snapshotValidation(int lastSeenCommittedTxNumber) {
        if (lastSeenCommittedTxNumber == getNumber()) {
            return;
        }

        int myNumber = getNumber();

        if (!this.bodiesRead.isEmpty()) {
            // the first may not be full
            VBox[] array = bodiesRead.first();
            for (int i = next + 1; i < array.length; i++) {
                VBoxBody body = array[i].body;
                if (body != null && body.version > myNumber) {
                    TransactionSignaller.SIGNALLER.signalCommitFail();
                }
            }

            // the rest are full
            for (VBox[] ar : bodiesRead.rest()) {
                for (int i = 0; i < ar.length; i++) {
                    VBoxBody body = ar[i].body;
                    if (body != null && body.version > myNumber) {
                        TransactionSignaller.SIGNALLER.signalCommitFail();
                    }
                }
            }
        }

        for (ParallelNestedTransaction mergedTx : mergedTxs) {
            if (!mergedTx.globalReads.isEmpty()) {
                // the first may not be full
                VBox[] array = mergedTx.globalReads.first().entries;
                for (int i = mergedTx.next + 1; i < array.length; i++) {
                    VBoxBody body = array[i].body;
                    if (body != null && body.version > myNumber) {
                        TransactionSignaller.SIGNALLER.signalCommitFail();
                    }
                }

                // the rest are full
                for (ReadBlock block : mergedTx.globalReads.rest()) {
                    array = block.entries;
                    for (int i = 0; i < array.length; i++) {
                        VBoxBody body = array[i].body;
                        if (body != null && body.version > myNumber) {
                            TransactionSignaller.SIGNALLER.signalCommitFail();
                        }
                    }
                }
            }
        }

        // VArray
        for (VArrayEntry<?> entry : arraysRead) {
            if (!entry.validate()) {
                TransactionSignaller.SIGNALLER.signalCommitFail();
            }
        }
    }

    @Override
    public boolean isWriteTransaction() {
        Cons<ParallelNestedTransaction> emptyCons = Cons.<ParallelNestedTransaction> empty();
        return (mergedTxs != emptyCons) || (!boxesWritten.isEmpty()) || (!boxesWrittenInPlace.isEmpty())
                || (!arrayWrites.isEmpty()) || (perTxValues != null && !perTxValues.isEmpty());
    }

    /**
     * Due to the JVSTM integration in Deuce, we must keep in a separate
     * class all constants initialized with Unasfe operations, otherwise
     * the JVM may crash on the bootstrap when it loads a transactional
     * class.
     */
    private static class Offsets {

        // --- Setup to use Unsafe
        private static final long mergedTxsOffset = UtilUnsafe.objectFieldOffset(ReadWriteTransaction.class, "mergedTxs");

    }

    protected boolean CASmergedTxs(Cons<ParallelNestedTransaction> expectedMergedTxs, Cons<ParallelNestedTransaction> newMergedTxs) {
        return UNSAFE.compareAndSwapObject(this, Offsets.mergedTxsOffset, expectedMergedTxs, newMergedTxs);
    }
}
