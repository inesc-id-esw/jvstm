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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import static jvstm.ReadWriteTransaction.NULL_VALUE;

/** Transactional array implementation for the JVSTM optimized for read-heavy workloads
 *
 * This implementation uses less memory and presents better read performance than other options.
 * For more details, check the notes on the source-code.
 *
 * Note that:
 * - Even on jvstm-lock-free, writing (committing) to a VArray uses locking; no locking is needed
 *   for reading
 * - Under a workload with lots of writes, a simple array of VBoxes might present better performance,
 *   and solves the aforementioned locking issue
 **/

/*
 * Main design notes:
 * - VArray keeps latest versions of values on VArray.values
 * - Older versions are kept in a linked-list of VArrayLogNode, similar to VBoxBody, but
 *   only one VArrayLogNode keeps all of the old values that a transaction changed
 * - VArrayEntry is used to represent the pair (array, index) inside read and write-sets
 *
 * How does it work (sketch)
 * To read a value, we check the array version. If it's older than our tx number, we
 * return the value from the latest version array.
 * If, instead, we need an older value, we have to check the logs to find the value
 * corresponding to our world version, and return that version, if found; otherwise,
 * the value from the latest version array is returned -- it just means that the value
 * was never changed, although other array positions were.
 *
 * To commit (see TopLevelTransaction), the writeset is iterated, and the entries are
 * distributed into an array of entries for each VArray that is going to be changed.
 * This array is then sorted, and used to generate the logEntryIndexes.
 * After this operation, commit is called on the array, resulting in the log node
 * being created, and the writeback done.
 */
final class VArrayLogNode<E> implements GarbageCollectable {

    /* These fields are kept outside the VArrayLogNode to allow the jvstm GC algorithm
     * to unlink the body.
     */
    static final private class VArrayLogNodeBody<E> {
        final VArrayLogNode<E> next;
        final int[] logEntryIndexes;
        final E[] logEntryValues;

        VArrayLogNodeBody(VArrayLogNode<E> next, int[] logEntryIndexes, E[] logEntryValues) {
            this.next = next;
            this.logEntryIndexes = logEntryIndexes;
            this.logEntryValues = logEntryValues;
        }
    }

    final int version;
    final VArrayLogNodeBody<E> body;

    VArrayLogNode(int[] logEntryIndexes, E[] logEntryValues, int version, VArrayLogNode<E> next) {
        this.version = version;
        this.body = new VArrayLogNodeBody<E>(next, logEntryIndexes, logEntryValues);
    }

    /* Algorithm:
     * - Recurse until we find the VArrayLogNode with the SMALLEST version still >= minVersion
     * - Look for the index we want from that version forward towards the beginning of the list
     * - Return the first value found; otherwise return null
     */
    public E getLogValue(int index, int minVersion) {
        if (version < minVersion) return null;

        E value = null;

        if (body.next != null) value = body.next.getLogValue(index, minVersion);

        if (value == null) {
            // If no value was found in previous nodes, try to find it on the current node
            int pos = Arrays.binarySearch(body.logEntryIndexes, index);
            if (pos >= 0) {
                // A special case may occur here: we might read NULL_VALUE, meaning that the value
                // was truly a null, or we might read null, meaning that we are on a partially-initialized
                // log, and we ignore what we found.
                value = body.logEntryValues[pos];
            }
        }

        return value;
    }

    // this static field is used to change the non-static final field "body"
    // see the comments on the clearPrevious method
    private static final Field BODY_FIELD;

    static {
        try {
            BODY_FIELD = VArrayLogNode.class.getDeclaredField("body");
            BODY_FIELD.setAccessible(true);
        } catch (NoSuchFieldException nsfe) {
            throw new Error("JVSTM error: couldn't get access to the VArrayLogNode.body field");
        }
    }

    public void clearPrevious() {
        // Copied from VBoxBody.clearPrevious()

        // we set the body field to null via reflection because it is
        // a final field

        // making the field final is crucial to ensure that the field
        // is properly initialized (and visible to other threads)
        // after an instance of VArrayLogNode is constructed, as
        // per the new Java Memory Model (JSR133)

        // also, according to the Java specification, we may change a
        // final field only via reflection and in some specific cases
        // (such as object reconstruction after deserialization)

        // even though this use is not the case, the potential
        // problems that may occur do not affect the correcteness of
        // the system: we just want to set the field to null to allow
        // the garbage collector to do its thing...
        try {
            BODY_FIELD.set(this, null);
        } catch (IllegalAccessException iae) {
            throw new Error("JVSTM error: cannot set the VArrayLogNode.body field to null");
        }
    }
}

final class VArrayEntry<E> implements Comparable<VArrayEntry<E>> {
    final VArray<E> array;
    final int index;

    // This field is used for two purposes:
    // 1) If this VArrayEntry is being used inside a read-set, it contains the read value
    // 2) If this VarrayEntry is being used inside a write-set, it contains the value to be
    //    written during the Tx commit operation
    private E object;

    // This field is used for parallel nested writes in the array so that
    // a nested tx may safely read VArrayEntries that were written in the nesting tree
    // -- Only used when VArrayEntry is in the read-set of a parallel nested transaction
    volatile int nestedVersion;

    // Also used for nesting, represents the owner of the entry when it was read.
    // -- Only used when VArrayEntry is in the read-set of a parallel nested transaction
    ReadWriteTransaction owner;

    VArrayEntry(VArray<E> array, int index) {
        this.array = array;
        this.index = index;
    }

    @Override
    public int hashCode() {
        return array.hashCode() + index;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof VArrayEntry<?>) {
            VArrayEntry<?> other = (VArrayEntry<?>) o;
            return (array == other.array) && (index == other.index);
        }
        return false;
    }

    @Override
    public int compareTo(VArrayEntry<E> other) {
        if (array != other.array) {
            throw new AssertionError("Cannot compare with a VArrayEntry belonging to different VArray");
        }
        return index - other.index;
    }

    public E getValue(int maxVersion) {
        // Keep read value for later validation
        object = getInternalValue(maxVersion);
        return object;
    }

    private E getInternalValue(int maxVersion) {
        // Read value from array (volatile read)
        E value = array.values.get(index);
        // Read array version
        int version = array.version;

        // If version <= maxVersion, array hasn't changed since we started the current transaction
        if (version <= maxVersion) return value;

        // Otherwise, check the log for the value
        E logValue = array.log.getLogValue(index, maxVersion);

        return logValue != null ?
                (logValue == NULL_VALUE ? null : logValue)
                : value;
    }

    // Only used when VArrayEntry is part of the read-set
    public boolean validate() {
        return object == array.values.get(index);
    }

    public void setReadOwner(ReadWriteTransaction owner) {
        this.owner = owner;
    }

    // Only used when VArrayEntry is part of the write-set
    public void setWriteValue(E value, int nestedVersion) {
        object = value;
        this.nestedVersion = nestedVersion;
    }

    // Only used when VArrayEntry is part of the write-set
    public E getWriteValue() {
        return object;
    }
}

public class VArray<E> {
    public final AtomicReferenceArray<E> values;
    public int version;
    public final int length;
    public VArrayLogNode<E> log;

    final ReentrantLock writebackLock = new ReentrantLock();

    public VArray(int size) {
        values = new AtomicReferenceArray<E>(size);
        length = size;
    }

    @SuppressWarnings("static-access")
    public E get(int index) {
        rangeCheck(index);

        // TODO: Apply the same optimization as in VBox.get()
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin(true);
            E value = tx.getArrayValue(new VArrayEntry<E>(this, index));
            tx.commit();
            return value;
        } else {
            return tx.getArrayValue(new VArrayEntry<E>(this, index));
        }
    }

    @SuppressWarnings("static-access")
    public void put(int index, E newE) {
        rangeCheck(index);

        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            tx.setArrayValue(new VArrayEntry<E>(this, index), newE);
            tx.commit();
        } else {
            tx.setArrayValue(new VArrayEntry<E>(this, index), newE);
        }
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException();
    }

    @SuppressWarnings("unchecked")
    public GarbageCollectable commit(int txNumber, VArrayEntry[] writesToCommit, int[] logEntryIndexes) {
        // Prepare arrays for log
        E[] logEntryValues = (E[]) new Object[writesToCommit.length];

        // Create and place log node
        log = new VArrayLogNode<E>(logEntryIndexes, logEntryValues, txNumber - 1, log);
        // Bump array version
        version = txNumber;

        // Proceed with normal writeback and populating log values
        int i = 0;
        for (VArrayEntry<E> entry : writesToCommit) {
            // Read old value from the array, and copy it to the log
            E oldValue = values.get(entry.index);
            if (oldValue == null) oldValue = (E) NULL_VALUE;
            logEntryValues[i++] = oldValue;

            // Write the new value
            // Using a lazySet because we don't need the new value to be seen by other threads
            // as soon as possible. In fact, it would be nice if only threads with transactions
            // created after we finish our commit see the new value.
            // For more details see the java.util.concurrent.atomic package description javadoc.
            values.lazySet(entry.index, entry.getWriteValue());    // Volatile write!
        }

        return log;
    }
}
