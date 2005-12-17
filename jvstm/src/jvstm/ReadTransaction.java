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

class ReadTransaction extends Transaction {

    ReadTransaction(int number) {
        super(number);
    }

    ReadTransaction(Transaction parent) {
	super(parent);
    }

    protected Transaction makeNestedTransaction() {
	return new ReadTransaction(this);
    }

    protected <T> void register(VBox<T> vbox, VBoxBody<T> body) {
        throw new WriteOnReadException();
    }

    protected <T> VBoxBody<T> getBodyForRead(VBox<T> vbox) {
        return vbox.body.getBody(number);
    }

    protected <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox) {
        throw new WriteOnReadException();
    }

    protected <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	return initial;
    }
    
    protected <T> void setPerTxValue(PerTxBox<T> box, T value) {
        throw new WriteOnReadException();
    }

    protected void doCommit() {
        // do nothing
    }
}
