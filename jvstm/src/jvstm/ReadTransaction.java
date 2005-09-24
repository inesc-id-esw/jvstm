package jvstm;

class ReadTransaction extends Transaction {

    ReadTransaction(int number) {
        super(number);
    }

    ReadTransaction(Transaction parent) {
	super(parent);
    }

    protected Transaction makeNestedTransaction() {
	return new ReadTransaction(this);
    }

    protected <T> void register(VBox<T> vbox, VBoxBody<T> body) {
        throw new WriteOnReadException();
    }

    protected <T> VBoxBody<T> getBodyForRead(VBox<T> vbox) {
        return vbox.body.getBody(number);
    }

    protected <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox) {
        throw new WriteOnReadException();
    }

    protected <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	return initial;
    }
    
    protected <T> void setPerTxValue(PerTxBox<T> box, T value) {
        throw new WriteOnReadException();
    }

    protected void doCommit() {
        // do nothing
    }
}
