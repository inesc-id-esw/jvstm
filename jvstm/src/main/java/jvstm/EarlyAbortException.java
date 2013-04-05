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

/**
 * An instance of <code>EarlyAbortException</code> is thrown within
 * a ReadWriteTransaction, whenever a read attempt is made to a VBox
 * which already has a newer committed version available (and as such
 * the transaction is doomed to abort at this point).
 *
 * An application should never catch instances of this class, as the
 * purpose of throwing an instance of this class is to make a
 * non-local exit from the currently running transaction, and restart
 * it. This is done by the JVSTM runtime and should not be masked by
 * the application code in any way.
 *
 * This class inherits from <code>CommitException</code> so as to not
 * break compatibility with other JVSTM versions, and legacy
 * applications using the Transaction API directly. In the future,
 * it should become instead a subclass of <code>Error</code>,
 * even though it is a "normal occurrence", because many applications
 * catch all occurrences of <code>Exception</code> and then discard
 * the exception.
 *
 * This class should be abstract and must be instantiated and thrown by a
 * concrete implementation of the TransactionSignaller interface.
 */
public abstract class EarlyAbortException extends CommitException {
    private static final long serialVersionUID = 1L;
    protected EarlyAbortException() { super(); }
}
