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

import java.util.concurrent.locks.ReentrantLock;

public class ActiveTransactionQueue {
    private static final long MONITORING_SLEEP_INTERVAL = 10 * 1000;
    private static final long REPORT_LONG_TRANSACTION_THRESHOLD = 120 * 1000;

    public final ReentrantLock LOCK = new ReentrantLock(true);

    protected Queue<Transaction> txs = new PriorityQueue<Transaction>();

    // used for reporting long-running transactions
    protected Transaction previousTransaction = null;
    protected long firstNoticedTime = 0;

    protected int previousOldestTxNumber = 0;
    protected List<TxQueueListener> listeners = new ArrayList<TxQueueListener>();

    ActiveTransactionQueue() {
 	Thread monitorThread = new Thread() {
 		public void run() {
                    while (true) {
                        try {
                            monitorQueue();
                            sleep(MONITORING_SLEEP_INTERVAL);
                        } catch (Throwable t) {
                            // ignore all errors, so that the thread never dies
                        }
                    }
 		}
 	    };

 	monitorThread.setDaemon(true);
 	monitorThread.start();
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

    public void add(Transaction tx) {
        LOCK.lock();
        try {
            txs.offer(tx);
        } finally {
            LOCK.unlock();
        }
    }

    public int getQueueSize() {
        LOCK.lock();
        try {
            return txs.size();
        } finally {
            LOCK.unlock();
        }
    }

    public int getOldestTxNumber() {
        LOCK.lock();
        try {
            return (txs.isEmpty() ? Transaction.getCommitted() : txs.peek().getNumber());
        } finally {
            LOCK.unlock();
        }
    }

    public Transaction getOldestTx() {
        LOCK.lock();
        try {
            return txs.peek();
        } finally {
            LOCK.unlock();
        }
    }

    public void renumberTransaction(Transaction tx, int txNumber) {
        LOCK.lock();
        try {
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
        } finally {
            LOCK.unlock();
        }
    }

    public void noteTxFinished(Transaction tx) {
        cleanOldTransactions();
    }


    protected void cleanOldTransactions() {
        LOCK.lock();
        try {
            Transaction oldestTx = txs.peek();

            int newOldest = previousOldestTxNumber;

            while (oldestTx != null) {
                newOldest = oldestTx.getNumber();
                if (mayRemoveTransaction(oldestTx)) {
                    oldestTx.gcTransaction();
                    txs.poll();
                    oldestTx = txs.peek();
                } else {
                    oldestTx = null;
                }
            }

            if (previousOldestTxNumber != newOldest) {
                notifyListeners(previousOldestTxNumber, newOldest);
                previousOldestTxNumber = newOldest;
            }
        } finally {
            LOCK.unlock();
        }
    }

    private static boolean mayRemoveTransaction(Transaction tx) {
        if (tx.isFinished()) {
            return true;
        }

        if (! tx.getThread().isAlive()) {
            // if the tx's thread is no longer alive, then the tx will never finish
            System.out.println("JVSTM: Discarding a transaction with a dead thread.");
            return true;
        }

        return false;
    }

    
    protected void monitorQueue() {
        LOCK.lock();
        try {
            Transaction oldestTx = txs.peek();

            if (oldestTx != null) {
                long currentTime = System.currentTimeMillis();

                if (previousTransaction == oldestTx) {
                    long txTime = currentTime - firstNoticedTime;
                    if (txTime > REPORT_LONG_TRANSACTION_THRESHOLD) {
                        Thread txThread = oldestTx.getThread();
                        // the tx may have finished meanwhile...
                        if (txThread != null) {
                            Throwable t = new Throwable("JVSTM: Found a long-running transaction (" + txTime + "ms)");
                            t.setStackTrace(txThread.getStackTrace());
                            // and, even if it did not finished above, it may have now...
                            // in that case, don't print the stack trace
                            if (oldestTx.getThread() == txThread) {
                                t.printStackTrace();
                            }
                        }
                    }
                } else {
                    previousTransaction = oldestTx;
                    firstNoticedTime = currentTime;
                }
            }
        } finally {
            LOCK.unlock();
        }
    }
}
