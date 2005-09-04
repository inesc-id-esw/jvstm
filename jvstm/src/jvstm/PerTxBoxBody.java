package jvstm;

class PerTxBoxBody<E> {
    E value;

    PerTxBoxBody(E initial) {
        value = initial;
    }
}
