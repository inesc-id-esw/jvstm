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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jvstm.util.Cons;

public class NestedCommitRecord {

    private final ParallelNestedTransaction committer;
    private final Cons<ParallelNestedTransaction> childrenToPropagate;
    private final Cons<ParallelNestedTransaction> expectedParentOrecs;
    private final Cons<VArrayEntry<?>> varrayReadsToPropagate;
    private final Map<VArrayEntry<?>, VArrayEntry<?>> arrayWritesToPropagate;
    private final Map<VArray<?>, Integer> arrayWritesCount;
    protected final AtomicReference<NestedCommitRecord> next = new AtomicReference<NestedCommitRecord>(null);
    protected final int commitNumber;
    protected volatile boolean recordCommitted = false;

    public NestedCommitRecord() {
        this.committer = null;
        this.childrenToPropagate = Cons.empty();
        this.expectedParentOrecs = Cons.empty();
        this.varrayReadsToPropagate = Cons.empty();
        this.arrayWritesToPropagate = ReadWriteTransaction.EMPTY_MAP;
        this.arrayWritesCount = ReadWriteTransaction.EMPTY_MAP;
        this.commitNumber = 0;
        this.recordCommitted = true;
    }

    public NestedCommitRecord(ParallelNestedTransaction committer, Cons<ParallelNestedTransaction> children,
            Cons<ParallelNestedTransaction> parentOrecs, Cons<VArrayEntry<?>> varrayReadsToPropagate,
            Map<VArrayEntry<?>, VArrayEntry<?>> arrayWrites, Map<VArray<?>, Integer> arrayWritesCount, int commitNumber) {
        this.committer = committer;
        this.childrenToPropagate = children;
        this.varrayReadsToPropagate = varrayReadsToPropagate;
        this.arrayWritesToPropagate = arrayWrites;
        this.arrayWritesCount = arrayWritesCount;
        this.commitNumber = commitNumber;
        this.expectedParentOrecs = parentOrecs;
    }

    public void helpCommit() {
        ReadWriteTransaction parent = committer.getRWParent();

        Cons<ParallelNestedTransaction> currentParentOrecs = parent.mergedTxs;
        if (currentParentOrecs == expectedParentOrecs) {
            committer.orec.nestedVersion = commitNumber;
            committer.orec.owner = parent;
            currentParentOrecs = currentParentOrecs.cons(committer);
            for (ParallelNestedTransaction childrenCommit : childrenToPropagate) {
                childrenCommit.orec.nestedVersion = commitNumber;
                childrenCommit.orec.owner = parent;
                currentParentOrecs = currentParentOrecs.cons(childrenCommit);
            }

            propagateVArraysFootprint();
            parent.CASmergedTxs(expectedParentOrecs, currentParentOrecs);
        }

    }

    // VArrays' support introduces locking in this commit procedure
    private void propagateVArraysFootprint() {
        if (this.arrayWritesToPropagate == ReadWriteTransaction.EMPTY_MAP && this.varrayReadsToPropagate.isEmpty()) {
            // nothing to propagate
            return;
        }

        ReadWriteTransaction parent = committer.getRWParent();

        synchronized (parent) {
            if (!this.varrayReadsToPropagate.isEmpty()) {
                parent.arraysRead = this.varrayReadsToPropagate.reverseInto(parent.arraysRead);
            }
            if (parent.arrayWrites == ReadWriteTransaction.EMPTY_MAP) {
                parent.arrayWrites = this.arrayWritesToPropagate;
                parent.arrayWritesCount = this.arrayWritesCount;
                for (VArrayEntry<?> entry : this.arrayWritesToPropagate.values()) {
                    entry.nestedVersion = this.commitNumber;
                }
            } else {
                // Propagate arrayWrites and correctly update the parent's
                // arrayWritebacks counter
                for (VArrayEntry<?> entry : this.arrayWritesToPropagate.values()) {
                    // Update the array write entry nested version
                    entry.nestedVersion = this.commitNumber;

                    if (parent.arrayWrites.put(entry, entry) != null)
                        continue;

                    // Count number of writes to the array
                    Integer writeCount = parent.arrayWritesCount.get(entry.array);
                    if (writeCount == null)
                        writeCount = 0;
                    parent.arrayWritesCount.put(entry.array, writeCount + 1);
                }
            }
        }
    }

}
