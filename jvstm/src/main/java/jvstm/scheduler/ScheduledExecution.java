package jvstm.scheduler;

public class ScheduledExecution {

    protected final OperationStats transactionStats;
    protected final SchedulerWorker thread;

    public ScheduledExecution(OperationStats transactionStats, SchedulerWorker thread) {
	this.transactionStats = transactionStats;
	this.thread = thread;
    }

}
