package simple;

import java.util.ArrayList;
import java.util.List;

import jvstm.CommitException;
import jvstm.Transaction;
import jvstm.DisjointParallelTask;
import jvstm.VBox;

/**
 * The main thread runs a transaction that attempts to increment two counters as an 
 * atomic action.
 * This example makes direct use of the JVSTM API for both top-level and disjoint parallel 
 * nested transactions.
 * @author nmld
 *
 */
public class SimpleDisjointTest {

    public static void main(String[] args) {
	int result = new SimpleDisjointTest(0, 0).start();
	if (result != 2) {
	    throw new AssertionError("Expected: 2; Obtained: " + result);
	}
	System.out.println("Expected: 2; Obtained: " + result);
    }

    protected final VBox<Integer> vbox1;
    protected final VBox<Integer> vbox2;

    public SimpleDisjointTest(int arg1, int arg2) {
	this.vbox1 = new VBox<Integer>(arg1);
	this.vbox2 = new VBox<Integer>(arg2);
    }

    public int start() {
	while (true) {
	    Transaction.begin(false);
	    boolean finished = false;
	    try {

		List<DisjointParallelTask<Integer>> tasks = new ArrayList<DisjointParallelTask<Integer>>();
		
		tasks.add(new DisjointWorker(vbox1));
		tasks.add(new DisjointWorker(vbox2));
		
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
    
    public class DisjointWorker extends DisjointParallelTask<Integer> {

	private VBox<Integer> vbox;
	
	public DisjointWorker(VBox<Integer> vbox) {
	    this.vbox = vbox;
	}
	
	@Override
	public Integer execute() throws Throwable {
	    vbox.put(vbox.get() + 1);
	    return vbox.get();
	}
	
    }

}
