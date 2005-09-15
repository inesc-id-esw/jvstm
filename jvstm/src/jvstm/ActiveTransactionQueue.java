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
	while (txs.isEmpty() || (! txs.peek().isFinished())) {
	    wait();
	}

	Transaction oldestTx = txs.peek();
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
