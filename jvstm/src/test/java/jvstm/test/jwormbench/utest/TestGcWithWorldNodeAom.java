package jvstm.test.jwormbench.utest;

import junit.framework.Assert;
import jvstm.Transaction;
import jvstm.VBox;
import jvstm.test.jwormbench.JvstmBenchNodeFactory;
import jwormbench.core.INode;

import org.junit.Test;

public class TestGcWithWorldNodeAom {
    
    @Test
    public void performTest(){
	JvstmBenchNodeFactory fac = new JvstmBenchNodeFactory();
	INode node = fac.make(7);
	int trxNumber = Transaction.mostRecentCommittedRecord.transactionNumber;
	
	// The factory instantiates a WorlNode object and the 
	// constructor does not invoke any barrier, so the
	// node must remain in the compact layout.	
	Assert.assertSame(null, ((VBox) node).body);

	// The following update is made inside an explicit transaction.
	Transaction.begin();
	node.setValue(node.getValue() + 1);
	Transaction.commit();
	Assert.assertEquals(++trxNumber, ((VBox) node).body.version);
	Assert.assertEquals(0, ((VBox) node).body.next.version);
	Assert.assertEquals(8, node.getValue()); // the body contains the new value
    }
}
