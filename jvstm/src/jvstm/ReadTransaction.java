package jvstm;

class ReadTransaction extends Transaction {

    ReadTransaction(int number) {
        super(number);
    }

    ReadTransaction(Transaction parent) {
	super(parent);
    }

    Transaction makeNestedTransaction() {
	return new ReadTransaction(this);
    }

    <T> void register(VBox<T> vbox, VBoxBody<T> body) {
        throw new WriteOnReadException();
    }

    <T> VBoxBody<T> getBodyForRead(VBox<T> vbox) {
        return vbox.body.getBody(number);
    }

    <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox) {
        throw new WriteOnReadException();
    }

    <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	return initial;
    }
    
    <T> void setPerTxValue(PerTxBox<T> box, T value) {
        throw new WriteOnReadException();
    }

    protected void doCommit() {
        // do nothing
    }
}
