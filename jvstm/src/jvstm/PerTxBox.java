package jvstm;

public class PerTxBox<E> {
    E initial;

    public PerTxBox(E initial) {
        this.initial = initial;
    }

    public E get() {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            E result = tx.getPerTxValue(this, initial);
            tx.commit();
            return result;
        } else {
            return tx.getPerTxValue(this, initial);
        }
    }

    public void put(E newE) {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.begin();
            tx.setPerTxValue(this, newE);
            tx.commit();
        } else {
            tx.setPerTxValue(this, newE);
        }
    }

    public void commit(E value) {
    }
}
