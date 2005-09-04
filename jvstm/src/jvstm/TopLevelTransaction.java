package jvstm;

import java.util.Map;

public class TopLevelTransaction extends Transaction {
    private static final Object COMMIT_LOCK = new Object();

    public TopLevelTransaction(int number) {
        super(number);
    }

    protected boolean isWriteTransaction() {
	return (! bodiesWritten.isEmpty());
    }

    protected void tryCommit() {
        if (isWriteTransaction()) {
            synchronized (COMMIT_LOCK) {
		if (validateCommit()) {
		    performValidCommit();
		} else {
		    throw new CommitException();
		}
            }
        }
    }

    protected void performValidCommit() {
	int newTxNumber = getCommitted() + 1;
        
	// renumber the TX to the new number
	renumber(newTxNumber);
	for (Map.Entry<PerTxBox,PerTxBoxBody> entry : perTxBodies.entrySet()) {
	    entry.getKey().commit(entry.getValue().value);
	}
	
	doCommit(newTxNumber);
	setCommitted(newTxNumber);
    }

    protected boolean validateCommit() {
        for (Map.Entry<VBox,VBoxBody> entry : bodiesRead.entrySet()) {
            if (entry.getKey().body != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    protected void doCommit(int newTxNumber) {
        for (Map.Entry<VBox,VBoxBody> entry : bodiesWritten.entrySet()) {
            VBox vbox = entry.getKey();
            VBoxBody body = entry.getValue();

            body.version = newTxNumber;
	    body.commit(vbox.body);
	    vbox.body = body;
        }
    }
}
