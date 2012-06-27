package jvstm;

import java.util.concurrent.Callable;

public abstract class TransactionalTask<T> implements Callable<T> {

    protected final Transaction parent;

    public TransactionalTask() {
	this.parent = Transaction.current();
    }
    
    public abstract T execute() throws Throwable;
    
    protected boolean isReadOnly() {
	return false;
    }
}
