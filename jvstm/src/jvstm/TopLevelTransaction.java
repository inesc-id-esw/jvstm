/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import java.util.concurrent.locks.ReentrantLock;


public class TopLevelTransaction extends ReadWriteTransaction {
    private static final ReentrantLock COMMIT_LOCK = new ReentrantLock(true);

    protected List<VBoxBody> bodiesCommitted = null;

    public TopLevelTransaction(int number) {
        super(number);
    }

    protected boolean isWriteTransaction() {
	return (! bodiesWritten.isEmpty()) || (! perTxValues.isEmpty());
    }

    protected void tryCommit() {
        if (isWriteTransaction()) {
            //Thread currentThread = Thread.currentThread();
            //int origPriority = currentThread.getPriority();
            //currentThread.setPriority(Thread.MAX_PRIORITY);
            COMMIT_LOCK.lock();
            try {
		if (validateCommit()) {
		    setCommitted(performValidCommit());
		} else {
		    throw new CommitException();
		}
            } finally {
                COMMIT_LOCK.unlock();
                //currentThread.setPriority(origPriority);
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
