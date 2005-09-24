package jvstm;

import java.util.Map;
import java.util.IdentityHashMap;


abstract class ReadWriteTransaction extends Transaction {

    protected Map<VBox,VBoxBody> bodiesRead = new IdentityHashMap<VBox,VBoxBody>();
    protected Map<VBox,VBoxBody> bodiesWritten = new IdentityHashMap<VBox,VBoxBody>();
    protected Map<PerTxBox,Object> perTxValues = new IdentityHashMap<PerTxBox,Object>();


    ReadWriteTransaction(int number) {
        super(number);
    }

    ReadWriteTransaction(ReadWriteTransaction parent) {
        super(parent);
    }

    protected Transaction makeNestedTransaction() {
	return new NestedTransaction(this);
    }

    ReadWriteTransaction getRWParent() {
	return (ReadWriteTransaction)getParent();
    }

    protected void finish() {
	super.finish();
	// to allow garbage collecting the collections
	bodiesRead = null;
	bodiesWritten = null;
	perTxValues = null;
    }

    protected void doCommit() {
	tryCommit();
	// if commit is successful, then clear all records
	bodiesRead.clear();
	bodiesWritten.clear();
	perTxValues.clear();
    }

    protected abstract void tryCommit();


    protected <T> void register(VBox<T> vbox, VBoxBody<T> body) {
        bodiesWritten.put(vbox, body);
    }

    protected <T> VBoxBody<T> getBodyWritten(VBox<T> vbox) {
        VBoxBody<T> body = bodiesWritten.get(vbox);
        if ((body == null) && (parent != null)) {
            body = getRWParent().getBodyWritten(vbox);
        }
        
        return body;
    }

    protected <T> VBoxBody<T> getBodyRead(VBox<T> vbox) {
        VBoxBody<T> body = bodiesRead.get(vbox);
        if ((body == null) && (parent != null)) {
            body = getRWParent().getBodyRead(vbox);
        }
        
        return body;
    }

    protected <T> VBoxBody<T> getBodyForRead(VBox<T> vbox) {
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

    protected <T> VBoxBody<T> getBodyForWrite(VBox<T> vbox) {
        VBoxBody<T> body = (VBoxBody<T>)bodiesWritten.get(vbox);
        if (body == null) {
            body = vbox.makeNewBody();
            register(vbox, body);
        }

        return body;
    }

    protected <T> T getPerTxValue(PerTxBox<T> box) {
        T value = (T)perTxValues.get(box);
        if ((value == null) && (parent != null)) {
	    value = getRWParent().getPerTxValue(box);
	}
	return value;
    }

    protected <T> T getPerTxValue(PerTxBox<T> box, T initial) {
        T value = getPerTxValue(box);
        if (value == null) {
            value = initial;
        }

        return value;
    }

    protected <T> void setPerTxValue(PerTxBox<T> box, T value) {
	perTxValues.put(box, value);
    }
}
