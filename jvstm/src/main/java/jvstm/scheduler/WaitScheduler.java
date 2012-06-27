package jvstm.scheduler;

import jvstm.scheduler.TaskList.Node;

/**
 * Scheduler based on David Burgo's thesis.
 * @author nmld
 *
 */
public class WaitScheduler extends Scheduler {

    @Override
    public <T extends ScheduledTask> T getNextScheduledTask() {
	while (true) {
	    synchronized (taskList) {
		boolean possibleConflict = false;
		Node nodeIter = taskList.head;
		while (nodeIter != null) {
		    ScheduledTask task = nodeIter.task;

		    OperationStats startingStats = transactionsTable[task.getTaskId()];
		    double[] conflictTable = startingStats.conflictTable;

		    synchronized (runningTransactions) {
			for (ScheduledExecution execution : runningTransactions) {
			    OperationStats concurrentTxStats = execution.transactionStats;
			    if (conflictTable[concurrentTxStats.transactionId] > 0.0) {
				possibleConflict = true;
				break;
			    }
			}

			if (!possibleConflict) {
			    taskList.deleteNode(nodeIter);
			    ScheduledExecution newExec = new ScheduledExecution(startingStats, null);
			    currentExecution.set(newExec);
			    runningTransactions.add(newExec);
			    return (T) task;
			}
		    }

		    nodeIter = nodeIter.next;
		}
		if (!possibleConflict) {
		    return null;
		}
	    }
	    Thread.yield();
	}
    }

    // Returns true if should run with inner parallelism
    @Override
    public boolean startTx(int startingTx, Object work, SchedulerWorker runner) {
	if (startingTx == -1) {
	    return true;
	}
	OperationStats startingStats = transactionsTable[startingTx];
	double[] conflictTable = startingStats.conflictTable;
	boolean possibleConflict = false;
	while (true) {
	    synchronized (runningTransactions) {
		for (ScheduledExecution execution : runningTransactions) {
		    OperationStats concurrentTxStats = execution.transactionStats;
		    if (concurrentTxStats.transactionId == startingTx) {
			continue;
		    }
		    if (conflictTable[concurrentTxStats.transactionId] > 0.0) {
			possibleConflict = true;
			break;
		    }
		}
	    }
	    if (!possibleConflict) {
		return (runningTransactions.size() + 1) < (maxThreads - 2);
	    }
	    Thread.yield();
	}
    }

    @Override
    public void conflictTx(int abortingTx, int commitNumberOfConflicter) {
	if (abortingTx == NOT_USED || commitNumberOfConflicter == NOT_USED) {
	    return;
	}
	int conflicterId = getTransactionId(commitNumberOfConflicter);
	transactionsTable[abortingTx].conflictTable[conflicterId]++;
	transactionsTable[conflicterId].conflictTable[abortingTx]++;
    }

    @Override
    public void commitTx(int committingTx) {
	if (committingTx == NOT_USED) {
	    return;
	}
	synchronized (runningTransactions) {
	    runningTransactions.remove(currentExecution.get());
	}
    }

}
