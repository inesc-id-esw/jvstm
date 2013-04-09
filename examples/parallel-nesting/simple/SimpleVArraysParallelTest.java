package simple;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jvstm.CommitException;
import jvstm.ParallelTask;
import jvstm.Transaction;
import jvstm.VArray;

/**
 * The main thread runs a top-level transaction, which will increment a counter in parallel.
 * This example makes direct use of the JVSTM API for both top-level and parallel nested 
 * transactions.
 * @author nmld
 *
 */
public class SimpleVArraysParallelTest {

    protected static final int ARRAY_SIZE = 10000;
    
    public static void main(String[] args) {
	int result = new SimpleVArraysParallelTest().start();
	if (result != (ARRAY_SIZE * 8)) {
	    throw new AssertionError("Expected: " + (ARRAY_SIZE*8) + "; Obtained: " + result);
	}
	System.out.println("Expected: " + (ARRAY_SIZE*8) + "; Obtained: " + result);
    }

    protected final VArray<Integer> varray;

    public SimpleVArraysParallelTest() {
	this.varray = new VArray<Integer>(ARRAY_SIZE);
	for (int j = 0; j < ARRAY_SIZE; j++) {
	    varray.put(j, 0);
	}
    }

    public int start() {
	while (true) {
	    Transaction.begin(false);
	    boolean finished = false;
	    try {

		List<ParallelTask<Void>> tasks = new ArrayList<ParallelTask<Void>>();
		
		for (int i = 0; i < 8; i++) {
		    tasks.add(new ParallelTask<Void>(){
			@Override
			public Void execute() throws Throwable {
			    for (int j = 0; j < ARRAY_SIZE; j++) {
				varray.put(j, varray.get(j) + 1);
			    }
			    return null;
			}
		    });
		}
	
		
		Transaction.current().manageNestedParallelTxs(tasks);
		
		int sum = 0;
		for (int j = 0; j < ARRAY_SIZE; j++) {
		    sum += varray.get(j);
		}

		Transaction.commit();
		finished = true;
		return sum;
	    } catch (CommitException ce) {
		Transaction.abort();
		finished = true;
	    } finally {
		if (!finished) {
		    Transaction.abort();
		}
	    }
	}
    }

}
