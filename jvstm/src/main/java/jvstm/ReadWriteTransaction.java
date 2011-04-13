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

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import jvstm.util.Cons;

public abstract class ReadWriteTransaction extends Transaction {
    protected static final CommitException COMMIT_EXCEPTION = new CommitException();

    protected static final Object NULL_VALUE = new Object();

    protected static final Map EMPTY_MAP = Collections.emptyMap();

    protected static final ThreadLocal<Cons<VBox[]>> pool = new ThreadLocal<Cons<VBox[]>>() {
        public Cons<VBox[]> initialValue() {
            return Cons.empty();
        }
    };

    private static void returnToPool(VBox[] array) {
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

    protected Cons<VBox []> bodiesRead = Cons.empty();
    protected int next = -1;
    protected Map<VBox, Object> boxesWritten = EMPTY_MAP;
    protected Cons<VBox> boxesWrittenInPlace = Cons.empty();
    protected Map<PerTxBox,Object> perTxValues = EMPTY_MAP;
    protected final OwnershipRecord orec = new OwnershipRecord(); // final is not required here, but we know that this slot will never change...


    public ReadWriteTransaction(int number) {
        super(number);
    }

    public ReadWriteTransaction(ReadWriteTransaction parent) {
        super(parent);
    }

    public Transaction makeNestedTransaction(boolean readOnly) {
        // always create a RW nested transaction, because we need its read-set
        return new NestedTransaction(this);
    }

    ReadWriteTransaction getRWParent() {
        return (ReadWriteTransaction)getParent();
    }

    @Override
    protected void abortTx() {
        this.orec.version = OwnershipRecord.ABORTED;
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
        boxesWritten = null;
        boxesWrittenInPlace = null;
        perTxValues = null;
    }

    protected void doCommit() {
        tryCommit();
        // if commit is successful, then reset transaction to a clean state
        for (VBox[] array : bodiesRead) {
            returnToPool(array);
        }

        bodiesRead = Cons.empty();
        boxesWritten = EMPTY_MAP;
        boxesWrittenInPlace = Cons.empty();
        perTxValues = EMPTY_MAP;
    }

    protected abstract void tryCommit();

    protected <T> T getLocalValue(VBox<T> vbox) {
        if (vbox.currentOwner == this.orec) {
            return vbox.tempValue;
        } else {
            T value = null;
            if (boxesWritten != EMPTY_MAP) {
                value = (T)boxesWritten.get(vbox);
            }
            if ((value == null) && (parent != null)) {
                value = getRWParent().getLocalValue(vbox);
            }
        
            return value;
        }
    }

    private <T> T readFromBody(VBox<T> vbox) {
        VBoxBody<T> body = vbox.body;

        if (body.version > number) {
            throw COMMIT_EXCEPTION;
        }

        VBox[] readset = null;
        if (next < 0) {
            readset = borrowFromPool();
            next = readset.length - 1;
            bodiesRead = bodiesRead.cons(readset);
        } else {
            readset = bodiesRead.first();
        }
        readset[next--] = vbox;
        return body.value;
    }

    public <T> T getBoxValue(VBox<T> vbox) {
        /*
         * When either no one has written to this vbox or any committed writer is not older than my version we know that
         * this transaction (as well as any parent) does not have a local value. In this case we read directly from the
         * vbox's body.
         */
        OwnershipRecord currentOwner = vbox.currentOwner;
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

    public <T> void setBoxValue(VBox<T> vbox, T value) {
        OwnershipRecord currentOwner = vbox.currentOwner;
        if (currentOwner == this.orec) { // we are already the current writer
            vbox.tempValue = (value == null ? (T)NULL_VALUE : value);
            return;
        }

        // the next loop ends either when we succeed in writing directly to the vbox or fallback to using the standard
        // write-set
        do {
            /* When there is no previous writer or the previous writer either is aborted or committed with a version not
             * greater than ours we try to gain ownership of the vbox. If we succeed we write to the vbox otherwise we
             * retry.
             *
             * Otherwise (there is a previous writer still running or committed in a version greater than ours) we
             * fallback to the standard write-set
             */
            if (currentOwner.version != 0 && currentOwner.version <= this.number) {
                if (vbox.CASsetOwner(currentOwner, this.orec)) {
                    // note: it is possible that a second invocation of setBoxValue in the same transaction will end up
                    // here after writing to the normal write-set.  This case is accounted for when creating the
                    // WriteSet at commit time
                    vbox.tempValue = (value == null ? (T)NULL_VALUE : value);
                    boxesWrittenInPlace = boxesWrittenInPlace.cons(vbox);
                    return; // break
                } else {
                    // update the current owner and retry
                    currentOwner = vbox.currentOwner;
                    continue;
                }
            } else { // fallback to the standard write-set
                // note: here we could consider the special case when the other writer is committed with a version
                // greater than ours and either abort or try to upgrade the transaction
                if (boxesWritten == EMPTY_MAP) {
                    boxesWritten = new HashMap<VBox, Object>();
                }
                boxesWritten.put(vbox, value == null ? NULL_VALUE : value);
                return; // break
            }
        } while (true);
    }

    protected <T> T getPerTxValue(PerTxBox<T> box) {
        T value = null;
        if (perTxValues != EMPTY_MAP) {
            value = (T)perTxValues.get(box);
        }
        if ((value == null) && (parent != null)) {
            value = getRWParent().getPerTxValue(box);
        }
        return value;
    }

    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
        T value = getPerTxValue(box);
        if (value == null) {
            value = initial;
        }

        return value;
    }

    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
        if (perTxValues == EMPTY_MAP) {
            perTxValues = new HashMap<PerTxBox,Object>();
        }
        perTxValues.put(box, value);
    }

    @Override
    public <T> T getArrayValue(VArrayEntry<T> entry) {
        throw new Error("FIXME: Not implemented yet");
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
        throw new Error("FIXME: Not implemented yet");
    }

    /**
     * Validates this read-set against all active transaction records more recent that the one
     * <code>lastChecked</code>.
     *
     * @return The last successfully validated ActiveTransactionsRecord
     * @throws CommitException if the validation fails
     */
    protected ActiveTransactionsRecord validate(ActiveTransactionsRecord lastChecked) {
        ActiveTransactionsRecord recordToCheck = lastChecked.getNext();

        while (recordToCheck != null) {
            if (!this.bodiesRead.isEmpty() && !validFor(recordToCheck)) {
                throw COMMIT_EXCEPTION;
            }
            lastChecked = recordToCheck;
            recordToCheck = recordToCheck.getNext();
        }
        return lastChecked;
    }
        
    protected boolean validFor(ActiveTransactionsRecord recordToCheck) {
        VBox [] writtenVBoxes = recordToCheck.getWriteSet().allWrittenVBoxes;

        for (VBox vbox : writtenVBoxes) {

            // check if the given vbox is in the readset

            // the first array may not be full
            VBox[] array = this.bodiesRead.first();
            for (int j = next + 1; j < array.length; j++) {
                if (array[j] == vbox) {
                    return false;
                }
            }
            
            // the rest are full
            for (VBox[] ar : bodiesRead.rest()) {
                for (int j = 0; j < ar.length; j++) {
                    if (ar[j] == vbox) {
                        return false;
                    }
                }
            }

        }
        return true;
    }
}
