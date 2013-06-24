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
 * All the exceptions related to the transaction control flow, such as the
 * jvstm.CommitException and the jvstm.EarlyAbortException, must be instantiated
 * and thrown by a concrete implementation of this signaller.
 *
 * We added this requirement to the JVSTM due to its integration in Deuce STM.
 * According to the Deuce STM these kind of exceptions must inherit from the
 * class org.deuce.transaction.TransactionException.
 *
 * You should use the signaling methods immediately followed by a
 * <tt>throw new AssertionError();</tt> to avoid compilation errors when it is
 * expected to be returned something in the place where you want to throw a
 * transaction exception.
 *
 * Here is an example of signalling a commit fail:
 *
 * <pre>
 * {@code
 * TransactionSignaller.SIGNALLER.signalCommitFail();
 * throw new AssertionError("Impossible condition - Commit fail signalled!");
 * }
 * </pre>
 */
public abstract class TransactionSignaller {

    public static TransactionSignaller SIGNALLER = new DefaultTransactionSignaller();

    public static void setSignaller(
            TransactionSignaller signaller) {
        SIGNALLER = signaller;
    }

    public abstract void signalCommitFail();

    public abstract void signalCommitFail(Transaction tx);

    public abstract void signalEarlyAbort();

}
