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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.PriorityQueue;

public class ActiveTransactionQueue {
    protected Queue<Transaction> txs = new PriorityQueue<Transaction>();

    protected int previousOldestTxNumber = 0;
    protected List<TxQueueListener> listeners = new ArrayList<TxQueueListener>();

    ActiveTransactionQueue() {
	Thread gcThread = new Thread() {
		public void run() {
		    try {
			while (true) {
			    cleanOldTransactions();
			}
		    } catch (InterruptedException ie) {
			// die silently...
		    }
		}
	    };

	gcThread.setDaemon(true);
	gcThread.start();
    }

    public void addListener(TxQueueListener listener) {
	synchronized (listeners) {
	    listeners.add(listener);
	}
    }

    public void removeListener(TxQueueListener listener) {
	synchronized (listeners) {
	    listeners.remove(listener);
	}
    }

    protected void notifyListeners(int previousOldest, int newOldest) {
	synchronized (listeners) {
	    for (TxQueueListener l : listeners) {
		try {
		    l.noteOldestTransaction(previousOldest, newOldest);
		} catch (Throwable t) {
		    t.printStackTrace();
		    // don't throw error, or else it would kill the tx-gc thread
		}
	    }
	}
    }

    public synchronized void add(Transaction tx) {
	txs.offer(tx);
    }

    public synchronized int getQueueSize() {
	return txs.size();
    }

    public synchronized int getOldestTxNumber() {
	return (txs.isEmpty() ? Transaction.getCommitted() : txs.peek().getNumber());
    }

    public synchronized Transaction getOldestTx() {
	return txs.peek();
    }

    public synchronized void renumberTransaction(Transaction tx, int txNumber) {
	// First remove
	// The following does not work because the remove(Object) removes the first element 
	// that comparesTo == 0 to the argument, rather than the element == to the argument
	//txs.remove(tx);
	for (Iterator iter = txs.iterator(); iter.hasNext(); ) {
	    if (iter.next() == tx) {
		iter.remove();
		break;
	    }
	}

	// renumber
	tx.setNumber(txNumber);

	// add again
	txs.offer(tx);
    }

    public synchronized void noteTxFinished(Transaction tx) {
	notifyAll();
    }


    protected synchronized void cleanOldTransactions() throws InterruptedException {
	while (txs.isEmpty()) {
	    wait();
	}

        // is not empty now...
	Transaction oldestTx = txs.peek();

	while (! oldestTx.isFinished()) {
            Thread txThread = oldestTx.getThread();

            if (! txThread.isAlive()) {
                System.out.println("Aborting a transaction with a dead thread.");
                oldestTx.abortTx();
            } else if (oldestTx.hasPassedTimeout()) {
                System.out.println("Dumping stack for thread with a timedout transaction and aborting that transaction...");
                Throwable t = new Throwable();
                t.setStackTrace(txThread.getStackTrace());
                t.printStackTrace();
                oldestTx.abortTx();
            } else {
                wait();
                // after the wait, the peek may return a different
                // value because of renumbering of transactions.  So,
                // update the value of the variable oldestTx
                oldestTx = txs.peek();
            }
            //debug("cleanOldTransactions alive");
	}

	int newOldest = oldestTx.getNumber();
	while ((oldestTx != null) && oldestTx.isFinished()) {
	    txs.poll();
	    oldestTx.gcTransaction();
	    oldestTx = txs.peek();
	    if (oldestTx != null) {
		newOldest = oldestTx.getNumber();
	    }
	}

	if (previousOldestTxNumber != newOldest) {
	    notifyListeners(previousOldestTxNumber, newOldest);
	}

	previousOldestTxNumber = newOldest;
    }
}
