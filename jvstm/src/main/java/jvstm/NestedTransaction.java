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

import java.util.Iterator;
import java.util.Map;

public class NestedTransaction extends ReadWriteTransaction {

    public NestedTransaction(ReadWriteTransaction parent) {
        super(parent);
        // start with parent's read-set
        this.bodiesRead = parent.bodiesRead;
        this.next = parent.next;
        // start with parent write-set of boxes written in place (useful to commit to parent a little faster)
        this.boxesWrittenInPlace = parent.boxesWrittenInPlace;
    }

    @Override
    protected Transaction commitAndBeginTx(boolean readOnly) {
        commitTx(true);
        return beginWithActiveRecord(readOnly, null);
    }

    @Override
    protected void finish() {
        // do not returnToPool the read-set arrays

        bodiesRead = null;
        boxesWritten = null;
        boxesWrittenInPlace = null;
        perTxValues = null;
    }

    @Override
    protected void doCommit() {
        tryCommit();

        // do not returnToPool the read-set arrays

        // reset read-set, write-set and perTxValues
        // bodiesRead = parent.bodiesRead; // already ensured in tryCommit()
        boxesWritten = EMPTY_MAP;
        // boxesWrittenInPlace = parent.boxesWrittenInPlace; // already ensured in tryCommit()
        perTxValues = EMPTY_MAP;
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
            if (vbox.currentOwner == this.orec) { // if we also wrote directly to the box, we just skip this value
                continue; // it will be handled in the next 'update ownership of boxesWrittenInPlace'
            }
            Object value = entry.getValue();
            if (vbox.currentOwner == parent.orec) {
                vbox.tempValue = value;
            } else {
                parent.boxesWritten.put(vbox, value);
            }
        }

        // now, update ownership of boxesWrittenInPlace

        // null, means that the test condition will never match
        VBox vboxAlreadyInParent = parent.boxesWrittenInPlace.isEmpty() ? null : parent.boxesWrittenInPlace.first();
        for (VBox vbox : this.boxesWrittenInPlace) {
            if (vbox == vboxAlreadyInParent) {
                break;
            }
            vbox.currentOwner = parent.orec; // vbox.CASsetOwner(this.orec, parent.orec) is not needed because we only have linear nesting
        }
        parent.boxesWrittenInPlace = this.boxesWrittenInPlace;

        if (parent.perTxValues == EMPTY_MAP) {
            parent.perTxValues = perTxValues;
        } else {
            parent.perTxValues.putAll(perTxValues);
        }
    }
}
