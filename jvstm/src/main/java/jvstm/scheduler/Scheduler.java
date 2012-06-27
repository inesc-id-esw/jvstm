package jvstm.scheduler;

import java.util.concurrent.ConcurrentLinkedQueue;

import jvstm.ActiveTransactionsRecord;
import jvstm.Transaction;

public class Scheduler {

    protected int maxThreads = Runtime.getRuntime().availableProcessors();
    protected final int NOT_USED = -1;

    protected OperationStats[] transactionsTable;
   public final ThreadLocal<ScheduledExecution> currentExecution = new ThreadLocal<ScheduledExecution>();
    protected final ConcurrentLinkedQueue<ScheduledExecution> runningTransactions = new ConcurrentLinkedQueue<ScheduledExecution>();

    protected int nextTask = -1;
    protected ScheduledTask[] tasksToSchedule;
    protected TaskList taskList;

    // Singleton Pattern
    protected static Scheduler scheduler = new Scheduler();
    
    protected static void setScheduler(Scheduler scheduler) {
	Scheduler.scheduler = scheduler;
    }

    public static Scheduler getScheduler() {
	return scheduler;
    }

    /*
     * The following are empty on purpose: empty scheduler!
     */
    public <T extends ScheduledTask> T getNextScheduledTask() {
	synchronized (tasksToSchedule) {
	    nextTask++;
	    if (nextTask >= tasksToSchedule.length) {
		return null;
	    }
	    return (T) tasksToSchedule[nextTask];
	}
    }

    // Returns true if transaction should run with inner parallelism
    public boolean startTx(int startingTx, Object work, SchedulerWorker runner) {
	return true;
    }

    public void conflictTx(int abortingTx, int commitNumberOfConflicter) {

    }

    public void commitTx(int committingTx) {

    }

    public void init(int numberTransactionsIds, int numberProcessors) {
	maxThreads = numberProcessors;
	init(numberTransactionsIds);
    }
    
    public void init(int numberTransactionsIds) {
	this.transactionsTable = new OperationStats[numberTransactionsIds];
	for (int i = 0; i < Scheduler.scheduler.transactionsTable.length; i++) {
	    Scheduler.scheduler.transactionsTable[i] = new OperationStats(i, numberTransactionsIds);
	}
    }
    
    protected int getTransactionId(int commitNumber) {
	ActiveTransactionsRecord atr = Transaction.gcTask.lastCleanedRecord;
	while (atr.transactionNumber != commitNumber) {
	    atr = atr.getNext();
	}
	return atr.transactionId;
    }
    
    public void submitTasks(DoublyLinkedList<ScheduledTask> tasks) {
	this.nextTask = -1;
	this.tasksToSchedule = new ScheduledTask[tasks.size()];
	this.taskList = new TaskList();
	int i = 0;
	for (ScheduledTask task : tasks) {
	    this.taskList.add(task);
	    this.tasksToSchedule[i] = task;
	    i++;
	}
    }
    
    
}
