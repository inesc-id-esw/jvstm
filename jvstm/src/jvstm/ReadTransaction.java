package jvstm;

class ReadTransaction extends Transaction {

    ReadTransaction(int number) {
        super(number);
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

    protected void tryCommit() {
        // do nothing
    }
}
