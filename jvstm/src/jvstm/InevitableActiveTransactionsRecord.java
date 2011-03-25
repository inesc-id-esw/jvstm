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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.Map;

import jvstm.util.Cons;

/* An InevitableActiveTransactionsRecord is an ActiveTransactionsRecord especially designed to be
 * used with InevitableTransactions.  This record blocks whenever an attempt is made to obtain the
 * write-set until the record is in the committed state. */
public class InevitableActiveTransactionsRecord extends ActiveTransactionsRecord {
    private final Object WRITE_SET_MONITOR = new Object();
    
    public InevitableActiveTransactionsRecord(int txNumber, Cons<VBoxBody> bodiesToGC) {
	super(txNumber, bodiesToGC, null);
    }

    // anyone doing this will have to be delayed until this transaction sets the write-set
    @Override
    protected Map<VBox, Object> getWriteSet() {
	synchronized (WRITE_SET_MONITOR) {
	    while (writeSet == null) {
		try {
		    WRITE_SET_MONITOR.wait();
		} catch (InterruptedException ie) {
		    // ignore and continue to wait
		}
	    }
	}
	return writeSet;
    }

    protected void setWriteSet(Map<VBox, Object> writeSet) {
	synchronized(WRITE_SET_MONITOR) {
	    this.writeSet = writeSet;
	    WRITE_SET_MONITOR.notifyAll();
	}
    }

    protected boolean clean() {
	writeSet = null;
	return super.clean();
    }
}
