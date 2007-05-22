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
import java.util.IdentityHashMap;


public abstract class ReadWriteTransaction extends Transaction {
    protected static final Object NULL_VALUE = new Object();

    protected Map<VBox,VBoxBody> bodiesRead = new IdentityHashMap<VBox,VBoxBody>();
    protected Map<VBox,Object> boxesWritten = new IdentityHashMap<VBox,Object>();
    protected Map<PerTxBox,Object> perTxValues = new IdentityHashMap<PerTxBox,Object>();


    public ReadWriteTransaction(int number) {
        super(number);
    }

    public ReadWriteTransaction(ReadWriteTransaction parent) {
        super(parent);
    }

    public Transaction makeNestedTransaction() {
	return new NestedTransaction(this);
    }

    ReadWriteTransaction getRWParent() {
	return (ReadWriteTransaction)getParent();
    }

    protected void finish() {
	super.finish();
	// to allow garbage collecting the collections
	bodiesRead = null;
	boxesWritten = null;
	perTxValues = null;
    }

    protected void doCommit() {
	tryCommit();
	// if commit is successful, then clear all records
	bodiesRead.clear();
	boxesWritten.clear();
	perTxValues.clear();
    }

    protected abstract void tryCommit();

    protected <T> T getLocalValue(VBox<T> vbox) {
        T value = (T)boxesWritten.get(vbox);
        if ((value == null) && (parent != null)) {
            value = getRWParent().getLocalValue(vbox);
        }
        
        return value;
    }

    protected <T> VBoxBody<T> getBodyRead(VBox<T> vbox) {
        VBoxBody<T> body = bodiesRead.get(vbox);
        if ((body == null) && (parent != null)) {
            body = getRWParent().getBodyRead(vbox);
        }
        
        return body;
    }

    public <T> T getBoxValue(VBox<T> vbox) {
        T value = getLocalValue(vbox);
        if (value == null) {
            VBoxBody<T> body = getBodyRead(vbox);

            if (body == null) {
                body = vbox.body.getBody(number);
                bodiesRead.put(vbox, body);
            }

            value = body.value;
        }
        return value;
    }

    public <T> void setBoxValue(VBox<T> vbox, T value) {
        boxesWritten.put(vbox, value == null ? NULL_VALUE : value);
    }

    protected <T> T getPerTxValue(PerTxBox<T> box) {
        T value = (T)perTxValues.get(box);
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
	perTxValues.put(box, value);
    }
}
