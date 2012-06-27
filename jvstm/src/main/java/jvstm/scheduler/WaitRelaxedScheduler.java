package jvstm.scheduler;


/**
 * Relaxation of David Burgo's scheduler.
 * @author nmld
 *
 */
public class WaitRelaxedScheduler extends Scheduler {

    public <T extends ScheduledTask> T getNextScheduledTask() {
	while (true) {
	    synchronized (tasksToSchedule) {
		boolean possibleConflict = false;
		int i = -1;
		for (ScheduledTask task : tasksToSchedule) {
		    i++;
		    if (task == null) {
			continue;
		    }
		    OperationStats startingStats = transactionsTable[task.getTaskId()];
		    double[] conflictTable = startingStats.conflictTable;
		    for (ScheduledExecution execution : runningTransactions) {
			OperationStats concurrentTxStats = execution.transactionStats;
			if (conflictTable[concurrentTxStats.transactionId] > 0.0) {
			    possibleConflict = true;
			    break;
			}
		    }
		    if (!possibleConflict) {
			tasksToSchedule[i] = null;
			ScheduledExecution newExec = new ScheduledExecution(startingStats, null);
			currentExecution.set(newExec);
			runningTransactions.add(newExec);
			return (T)task;
		    }
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
	return (runningTransactions.size() + 1) < (maxThreads - 2);
    }

    @Override
    public void conflictTx(int abortingTx, int commitNumberOfConflicter) {
	if (abortingTx == NOT_USED || commitNumberOfConflicter == NOT_USED) {
	    return;
	}
	int conflicterId = getTransactionId(commitNumberOfConflicter);
	transactionsTable[abortingTx].conflictTable[conflicterId]++;
	transactionsTable[conflicterId].conflictTable[abortingTx]++;
	runningTransactions.remove(currentExecution.get());
    }

    @Override
    public void commitTx(int committingTx) {
	if (committingTx == NOT_USED) {
	    return;
	}
	runningTransactions.remove(currentExecution.get());
    }

}
