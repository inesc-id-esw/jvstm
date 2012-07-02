package jvstm;

import java.util.HashMap;
import java.util.Map;

public class CommitTimeTransaction extends Transaction {

    protected static final StopPerTxBoxesCommitException STOP_PER_TX_BOX_COMMIT_EXCEPTION = new StopPerTxBoxesCommitException();

    // Note: these are not meant to be modified!
    private ReadWriteTransaction committingTx;
    private Map<VBox, Object> writeSet;
    private Map<PerTxBox, Object> perTxValues;
    private WriteSet writeSetReification;

    private Map<VBox, Object> writeSetProduced = ReadWriteTransaction.EMPTY_MAP;

    private final Transaction transactionHelping;

    public CommitTimeTransaction(WriteSet writeSetToCommit) {
	super(writeSetToCommit.committer.getNumber());
	this.writeSetReification = writeSetToCommit;
	this.committingTx = writeSetToCommit.committer;
	this.writeSet = committingTx.boxesWritten;
	this.perTxValues = committingTx.perTxValues;

	this.transactionHelping = Transaction.current();
	Transaction.current.set(this);
    }

    public Map<VBox, Object> finishExecution() {
	Map<VBox, Object> result = this.writeSetProduced;

	this.committingTx = null;
	this.writeSet = null;
	this.perTxValues = null;
	this.writeSetProduced = null;
	this.writeSetReification = null;

	Transaction.current.set(this.transactionHelping);

	return result;
    }

    public void finishEarly() {
	this.committingTx = null;
	this.writeSet = null;
	this.perTxValues = null;
	this.writeSetProduced = null;
	this.writeSetReification = null;

	Transaction.current.set(this.transactionHelping);
    }

    protected <T> T getLocalValue(VBox<T> vbox) {
	InplaceWrite<T> inplace = vbox.inplace;
	ReadWriteTransaction currentOwner = inplace.orec.owner;
	if (writeSetReification.boxesWrittenDueToPerTxBoxes != null) {
	    throw STOP_PER_TX_BOX_COMMIT_EXCEPTION;
	}
	if (currentOwner == committingTx) {
	    return inplace.tempValue;
	}
	if (writeSetProduced != ReadWriteTransaction.EMPTY_MAP) {
	    T result = (T) writeSetProduced.get(vbox);
	    if (result == null && writeSet != ReadWriteTransaction.EMPTY_MAP) {
		return (T) writeSet.get(vbox);
	    }
	    return result;
	}
	return null;
    }

    @Override
    public <T> T getBoxValue(VBox<T> vbox) {
	OwnershipRecord currentOwner = vbox.inplace.orec;
	if (currentOwner.version > 0 && currentOwner.version <= this.number) {
	    return vbox.body.getBody(this.number).value;
	} else {
	    T value = getLocalValue(vbox);
	    if (value == null) { // no local value exists
		return vbox.body.getBody(this.number).value;
	    }
	    // else
	    return (value == ReadWriteTransaction.NULL_VALUE) ? null : value;
	}
    }

    @Override
    public <T> void setBoxValue(VBox<T> vbox, T value) {
	if (writeSetProduced == ReadWriteTransaction.EMPTY_MAP) {
	    writeSetProduced = new HashMap<VBox, Object>();
	}
	writeSetProduced.put(vbox, value == null ? ReadWriteTransaction.NULL_VALUE : value);
    }

    @Override
    public <T> T getPerTxValue(PerTxBox<T> box, T initial) {
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public <T> void setPerTxValue(PerTxBox<T> box, T value) {
	// TODO Auto-generated method stub
    }

    @Override
    public <T> T getArrayValue(VArrayEntry<T> entry) {
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public <T> void setArrayValue(VArrayEntry<T> entry, T value) {
	// TODO Auto-generated method stub
    }

    private static final String UNSUPPORTED_MESSAGE = "The CommitTimeTransaction does not implement this operation";

    @Override
    protected Transaction commitAndBeginTx(boolean readOnly) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Transaction makeNestedTransaction(boolean readOnly) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    protected void doCommit() {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Transaction makeUnsafeMultithreaded() {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Transaction makeParallelNestedTransaction(boolean readOnly) {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public boolean isWriteTransaction() {
	throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

}