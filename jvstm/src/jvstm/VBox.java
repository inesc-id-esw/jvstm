package jvstm;

public class VBox<E> {
    public volatile VBoxBody<E> body;

    public VBox() {
        this((E)null);
    }
    
    public VBox(E initial) {
        VBoxBody<E> body = makeNewBody();
        body.value = initial;

        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            tx.register(this, body);
            tx.commit();
        } else {
            tx.register(this, body);
        }
    }

    // used for persistence support
    protected VBox(VBoxBody<E> body) {
	this.body = body;
    }

    public E get() {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            E result = tx.getBodyForRead(this).value;
            tx.commit();
            return result;
        } else {
            return tx.getBodyForRead(this).value;
        }
    }

    public void put(E newE) {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            tx.getBodyForWrite(this).value = newE;
            tx.commit();
        } else {
            tx.getBodyForWrite(this).value = newE;            
        }
    }

    public VBoxBody<E> makeNewBody() {
	return new MultiVersionBoxBody<E>();
    }

    public void commit(VBoxBody<E> newBody) {
	newBody.setPrevious(this.body);
	this.body = newBody;
    }
}
