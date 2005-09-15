package jvstm;

class NestedTransaction extends ReadWriteTransaction {

    NestedTransaction(ReadWriteTransaction parent) {
        super(parent);
    }

    protected void tryCommit() {
        getRWParent().bodiesRead.putAll(bodiesRead);
        getRWParent().bodiesWritten.putAll(bodiesWritten);
        getRWParent().perTxValues.putAll(perTxValues);
    }
}
