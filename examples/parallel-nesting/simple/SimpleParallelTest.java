package simple;

import java.util.ArrayList;
import java.util.List;

import jvstm.CommitException;
import jvstm.ParallelTask;
import jvstm.Transaction;
import jvstm.VBox;

/**
 * The main thread runs a top-level transaction, which will increment a counter in parallel.
 * This example makes direct use of the JVSTM API for both top-level and parallel nested 
 * transactions.
 * @author nmld
 *
 */
public class SimpleParallelTest {

    public static void main(String[] args) {
	int result = new SimpleParallelTest(0).start();
	if (result != 10) {
	    throw new AssertionError("Expected: 10; Obtained: " + result);
	}
	System.out.println("Expected: 10; Obtained: " + result);
    }

    protected final VBox<Integer> vbox;

    public SimpleParallelTest(int arg) {
	this.vbox = new VBox<Integer>(arg);
    }

    public int start() {
	while (true) {
	    Transaction.begin(false);
	    boolean finished = false;
	    try {

		List<ParallelTask<Integer>> tasks = new ArrayList<ParallelTask<Integer>>();
		
		for (int i = 0; i < 4; i++) {
		    tasks.add(new ParallelTask<Integer>(){
			@Override
			public Integer execute() throws Throwable {
			    vbox.put(vbox.get() + 1);
			    return vbox.get();
			}
		    });
		}
		
		List<Integer> results = Transaction.current().manageNestedParallelTxs(tasks);
		
		int sum = 0;
		for (Integer res : results) {
		    sum += res;
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
