package jvstm;


public abstract class UnsafeParallelTask<T> extends TransactionalTask<T> {

    @Override
    public T call() throws Exception {
	super.parent.start();
	Transaction tx = Transaction.beginUnsafeMultithreaded();
	try {
	    T value = execute();
	    tx.commit();
	    tx = null;
	    return value;
	} catch (EarlyAbortException eae) { 
	    tx.abort();
	    throw eae;
	} catch (CommitException ce) {
	    tx.abort();
	    throw ce;
	} catch (Throwable t) {
	    if (t instanceof Exception) {
		throw (Exception) t;
	    } else {
		t.printStackTrace();
		System.exit(0);
		return null;
	    }
	}
    }

}
