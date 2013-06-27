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

/* An InevitableActiveTransactionsRecord is an ActiveTransactionsRecord especially designed to be
 * used with InevitableTransactions.  This record blocks whenever an attempt is made to obtain the
 * write-set until the write-set is known (set via setWriteSet()). */
public class InevitableActiveTransactionsRecord extends ActiveTransactionsRecord {
    private final Object WRITE_SET_MONITOR = new Object();

    public InevitableActiveTransactionsRecord(int txNumber) {
        super(txNumber, null);
    }

    // anyone doing this will have to be delayed until this transaction sets the write-set
    @Override
    public WriteSet getWriteSet() {
        synchronized (WRITE_SET_MONITOR) {
            while (this.writeSet == null) {
                try {
                    WRITE_SET_MONITOR.wait();
                } catch (InterruptedException ie) {
                    // ignore and continue to wait
                }
            }
        }
        return this.writeSet;
    }

    protected void setWriteSet(WriteSet writeSet) {
        synchronized(WRITE_SET_MONITOR) {
            this.writeSet = writeSet;
            WRITE_SET_MONITOR.notifyAll();
        }
    }

    // protected boolean clean() {
    //  return super.clean();
    // }
}
