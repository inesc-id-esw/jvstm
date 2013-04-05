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

public class DefaultTransactionSignaller extends TransactionSignaller {

    private static class DefaultCommitException extends CommitException {
        private static final long serialVersionUID = 1L;

        public DefaultCommitException() {
        }

        public DefaultCommitException(Transaction tx) {
            super(tx);
        }

    }

    private static class DefaultEarlyAbortException extends EarlyAbortException {
        private static final long serialVersionUID = 1L;
    }

    private static final CommitException COMMIT_EXCEPTION = new DefaultCommitException();
    private static final EarlyAbortException EARLYABORT_EXCEPTION = new DefaultEarlyAbortException();

    @Override
    public void signalCommitFail() {
        throw COMMIT_EXCEPTION;
    }

    @Override
    public void signalCommitFail(Transaction tx) {
        throw new DefaultCommitException(tx);
    }

    @Override
    public void signalEarlyAbort() {
        throw EARLYABORT_EXCEPTION;
    }

}
