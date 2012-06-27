package jvstm;


public abstract class ParallelTask<T> extends TransactionalTask<T> {

    @Override
    public T call() throws Exception {
	try {
	    // parent may be null if this is a "top-level" task
	    if (super.parent != null) {
		super.parent.start();
	    }
	    while (true) {
		Transaction tx;
		if (super.parent != null) {
		    // we force the creation of a parallel nested, otherwise it would be a linear nested
		    tx = Transaction.beginParallelNested(isReadOnly());
		} else {
		    tx = Transaction.begin(isReadOnly());
		}
		try {
		    T result = execute();
		    tx.commit();
		    tx = null;
		    return result;
		} catch(EarlyAbortException eae) {
		    tx.abort();
		    tx = null;
		    if (super.parent != null) {
			throw eae;
		    }
		} catch (CommitException ce) {
		    final Transaction causedConflict = ce.getTransactionCausedConflict();
		    tx.abort();

		    tx = null;
		    if (causedConflict != super.parent) {
			throw ce;
		    }
		} finally {
		    if (tx != null) {
			tx.abort();
		    }
		}
	    }
	} catch (Throwable t) {
	    if (t instanceof Exception) {
		throw (Exception) t;
	    } else {
		throw new RuntimeException(t);
	    }
	}
    }

}