package jvstm;

class NestedTransaction extends Transaction {

    NestedTransaction(Transaction parent) {
        super(parent);
    }

    protected void tryCommit() {
        getParent().bodiesRead.putAll(bodiesRead);
        getParent().bodiesWritten.putAll(bodiesWritten);
    }
}
