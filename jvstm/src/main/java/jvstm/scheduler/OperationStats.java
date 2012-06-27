package jvstm.scheduler;

import jvstm.VBox;

public class OperationStats {

    protected final int transactionId;
    protected final double[] conflictTable;
    protected OperationStats serializedAfter;

    public OperationStats(int transactionId, int numberTransactions) {
	this.transactionId = transactionId;
	this.conflictTable = new double[numberTransactions];
    }

}
