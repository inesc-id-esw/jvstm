package jvstm;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class TopLevelTransaction extends ReadWriteTransaction {
    private static final Object COMMIT_LOCK = new Object();

    protected List<VBoxBody> bodiesCommitted = null;

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
		    setCommitted(performValidCommit());
		} else {
		    throw new CommitException();
		}
            }
        }
    }

    protected int performValidCommit() {
	int newTxNumber = getCommitted() + 1;
        
	// renumber the TX to the new number
	renumber(newTxNumber);
	for (Map.Entry<PerTxBox,Object> entry : perTxValues.entrySet()) {
	    entry.getKey().commit(entry.getValue());
	}
	
	doCommit(newTxNumber);
	return newTxNumber;
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
	    vbox.commit(body);
        }

	// save them for future GC
	if (bodiesCommitted == null) {
	    bodiesCommitted = new ArrayList<VBoxBody>(bodiesWritten.values());
	} else {
	    bodiesCommitted.addAll(bodiesWritten.values());
	}
    }

    protected void gcTransaction() {
	if (bodiesCommitted != null) {
	    // clean old versions of committed bodies
	    for (VBoxBody body : bodiesCommitted) {
		body.clearPrevious();
	    }
	    
	    bodiesCommitted = null;
	}
    }
}
