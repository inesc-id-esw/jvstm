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

public class VBox<E> {
    public volatile VBoxBody<E> body;

    public VBox() {
        this((E)null);
    }
    
    public VBox(E initial) {
        VBoxBody<E> body = makeNewBody();
        body.value = initial;

        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            tx.register(this, body);
            tx.commit();
        } else {
            tx.register(this, body);
        }
    }

    // used for persistence support
    protected VBox(VBoxBody<E> body) {
	this.body = body;
    }

    public E get() {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            E result = tx.getBodyForRead(this).value;
            tx.commit();
            return result;
        } else {
            return tx.getBodyForRead(this).value;
        }
    }

    public void put(E newE) {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            tx.getBodyForWrite(this).value = newE;
            tx.commit();
        } else {
            tx.getBodyForWrite(this).value = newE;            
        }
    }

    public VBoxBody<E> makeNewBody() {
	return new MultiVersionBoxBody<E>();
    }

    public void commit(VBoxBody<E> newBody) {
	newBody.setPrevious(this.body);
	this.body = newBody;
    }
}
