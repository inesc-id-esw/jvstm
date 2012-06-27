package jvstm.scheduler;


public class SerializerScheduler extends Scheduler {

    // Returns true if should run with inner parallelism
    @Override
    public boolean startTx(int startingTx, Object work, SchedulerWorker runner) {
	if (startingTx == -1) {
	    return true;
	}
	OperationStats startingStats = transactionsTable[startingTx];
	double[] conflictTable = startingStats.conflictTable;
	int i = 0;
	for (ScheduledExecution execution : runningTransactions) {
	    SchedulerWorker thread = execution.thread;
	    OperationStats concurrentTxStats = execution.transactionStats;
	    if (conflictTable[concurrentTxStats.transactionId] > 0.0) {
		thread.acceptTask(work);
		throw new SerializedOperationException();
	    }
	    i++;
	}
	boolean parNest = (i + 1) < (maxThreads - 2);
	ScheduledExecution newExec = new ScheduledExecution(startingStats, runner);
	currentExecution.set(newExec);
	runningTransactions.add(newExec);

	return parNest;
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
	currentExecution.set(null);
    }

    @Override
    public void commitTx(int committingTx) {
	if (committingTx == NOT_USED) {
	    return;
	}
	runningTransactions.remove(currentExecution.get());
	currentExecution.set(null);
    }

    public static void printReport() {
	String result = "";
	for (int i = 0; i < Scheduler.scheduler.transactionsTable.length; i++) {
	    double[] conflicts = Scheduler.scheduler.transactionsTable[i].conflictTable;
	    result += "\n" + i + " can run with:";
	    for (int k = 0; k < conflicts.length; k++) {
		if (conflicts[k] == 0.0) {
		    result += " " + k;
		}
	    }
	}
	System.err.println(result);
    }

}
