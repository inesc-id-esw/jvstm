package jvstm;

import java.util.Map;
import java.util.IdentityHashMap;

public abstract class Transaction {
    private static int commited = 0;

    protected static final ThreadLocal<Transaction> current = new ThreadLocal<Transaction>();

    public static Transaction current() {
        return current.get();
    }

    public static synchronized int getCommitted() {
        return commited;
    }

    public static synchronized void setCommitted(int number) {
        commited = Math.max(number, commited);
    }

    public static Transaction beginReadOnly() {
        Transaction currentTx = current.get();
        if (currentTx != null) {
            throw new Error("Can't have non top-level read-only txs");
        }
        Transaction tx = new ReadTransaction(getCommitted());
        current.set(tx);
        return tx;
    }

    public static Transaction begin() {
        return begin(-1);
    }

    protected static Transaction begin(int txNumber) {
        Transaction parent = current.get();
        Transaction tx = null;
        if (parent == null) {
            int num = ((txNumber == -1) ? getCommitted() : txNumber);
            tx = new TopLevelTransaction(num);
        } else {
            tx = new NestedTransaction(parent);
        }        
        current.set(tx);
        return tx;
    }

    public static void abort() {
        current.set(current.get().getParent());
    }

    public static int commit() {
        Transaction tx = current.get();
        tx.tryCommit();
        current.set(tx.getParent());
        return tx.getNumber();
    }

    public static Transaction checkpoint() {
        int txNumber = commit();
        return begin(txNumber);
    }

    protected int number;
    protected Transaction parent;

    protected Map<VBox,VBoxBody> bodiesRead = new IdentityHashMap<VBox,VBoxBody>();
    protected Map<VBox,VBoxBody> bodiesWritten = new IdentityHashMap<VBox,VBoxBody>();

    protected Map<PerTxBox,PerTxBoxBody> perTxBodies = new IdentityHashMap<PerTxBox,PerTxBoxBody>();

    
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

    protected void renumber(int number) {
	setNumber(number);
    }

    <T> PerTxBoxBody<T> getPerTxBody(PerTxBox<T> box, T initial) {
        PerTxBoxBody<T> body = (PerTxBoxBody<T>)perTxBodies.get(box);
        if (body == null) {
            body = new PerTxBoxBody<T>(initial);
            perTxBodies.put(box, body);
        }

        return body;
    }


    <T> void register(VBox<T> vbox, VBoxBody<T> body) {
        bodiesWritten.put(vbox, body);
    }

    protected <T> VBoxBody<T> getBodyWritten(VBox<T> vbox) {
        VBoxBody<T> body = bodiesWritten.get(vbox);
        if ((body == null) && (parent != null)) {
            body = parent.getBodyWritten(vbox);
        }
        
        return body;
    }

    protected <T> VBoxBody<T> getBodyRead(VBox<T> vbox) {
        VBoxBody<T> body = bodiesRead.get(vbox);
        if ((body == null) && (parent != null)) {
            body = parent.getBodyRead(vbox);
        }
        
        return body;
    }

    <T> VBoxBody<T> getBodyForRead(VBox<T> vbox) {
        VBoxBody<T> body = getBodyWritten(vbox);
        if (body == null) {
            body = getBodyRead(vbox);
        }
        if (body == null) {
            body = vbox.body.getBody(number);
            bodiesRead.put(vbox, body);
        }
        return body;
    }

    <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox) {
        VBoxBody<T> body = (VBoxBody<T>)bodiesWritten.get(vbox);
        if (body == null) {
            body = vbox.makeNewBody();
            register(vbox, body);
        }

        return body;
    }
    
    protected abstract void tryCommit();

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
