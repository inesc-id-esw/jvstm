package jvstm;

public abstract class Transaction implements Comparable<Transaction> {

    private static int commited = 0;

    protected static final ActiveTransactionQueue ACTIVE_TXS = new ActiveTransactionQueue();

    protected static final ThreadLocal<Transaction> current = new ThreadLocal<Transaction>();

    private static TransactionFactory TRANSACTION_FACTORY = new TransactionFactory() {
	    public Transaction makeTopLevelTransaction(int txNumber) {
		return new TopLevelTransaction(txNumber);
	    }
	};

    public static void setTransactionFactory(TransactionFactory factory) {
	TRANSACTION_FACTORY = factory;
    }

    public static Transaction current() {
        return current.get();
    }

    public static synchronized int getCommitted() {
        return commited;
    }

    public static synchronized void setCommitted(int number) {
        commited = Math.max(number, commited);
    }

    public static void addTxQueueListener(TxQueueListener listener) {
	ACTIVE_TXS.addListener(listener);
    }

    public static Transaction begin() {
        Transaction parent = current.get();
        Transaction tx = null;

	// we need to synchronize on the queue to inhibit the queue clean-up because we 
	// don't want that a transaction that commits between we get the last committed number 
	// and we add the new transaction cleans up the version number that we got before 
	// the new transaction is added to the queue
	synchronized (ACTIVE_TXS) {
	    if (parent == null) {
		tx = TRANSACTION_FACTORY.makeTopLevelTransaction(getCommitted());
	    } else {
		tx = parent.makeNestedTransaction();
	    }
	    current.set(tx);
	    ACTIVE_TXS.add(tx);
	}
        return tx;
    }


    public static void abort() {
	current.get().finish();
    }

    public static void commit() {
        Transaction tx = current.get();
        tx.doCommit();
	tx.finish();
    }

    public static void checkpoint() {
        Transaction tx = current.get();
        tx.doCommit();
    }

    protected int number;
    protected Transaction parent;
    protected boolean finished = false;

    
    public Transaction(int number) {
        this.number = number;
    }

    public Transaction(Transaction parent) {
        this(parent.getNumber());
        this.parent = parent;
    }

    protected Transaction getParent() {
        return parent;
    }

    public int getNumber() {
        return number;
    }

    protected void setNumber(int number) {
        this.number = number;
    }

    public int compareTo(Transaction o) {
	return (this.getNumber() - o.getNumber());
    }

    protected void renumber(int txNumber) {
	// To keep the queue ordered, we have to remove and reinsert the TX when it is renumbered
	ACTIVE_TXS.renumberTransaction(this, txNumber);
    }

    public boolean isFinished() {
	return finished;
    }

    protected void finish() {
	this.finished = true;
        current.set(this.getParent());
	// the eventual setCommitted was already done, then we may clean-up
	ACTIVE_TXS.noteTxFinished(this);
    }

    protected void gcTransaction() {
	// by default, do nothing
    }

    protected abstract Transaction makeNestedTransaction();

    protected abstract <T> void register(VBox<T> vbox, VBoxBody<T> body);

    protected abstract <T> VBoxBody<T> getBodyForRead(VBox<T> vbox);

    protected abstract <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox);
    
    protected abstract <T> T getPerTxValue(PerTxBox<T> box, T initial);

    protected abstract <T> void setPerTxValue(PerTxBox<T> box, T value);

    protected abstract void doCommit();


    public static void transactionallyDo(TransactionalCommand command) {
        while (true) {
            Transaction tx = Transaction.begin();
            try {
                command.doIt();
                tx.commit();
                tx = null;
                return;
            } catch (CommitException ce) {
                tx.abort();
                tx = null;
            } finally {
                if (tx != null) {
                    tx.abort();
                }
            }
        }
    }
}
