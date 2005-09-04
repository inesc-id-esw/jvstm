package jvstm;

public class PerTxBox<E> {
    E initial;

    public PerTxBox(E initial) {
        this.initial = initial;
    }

    private PerTxBoxBody<E> getBody() {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            PerTxBoxBody<E> result = tx.getPerTxBody(this, initial);
            tx.commit();
            return result;
        } else {
            return tx.getPerTxBody(this, initial);
        }
    }

    public E get() {
        return getBody().value;
    }

    public void put(E newE) {
        getBody().value = newE;
    }

    public void commit(E value) {
    }
}
