package simple;

import java.util.List;

import jvstm.Atomic;
import jvstm.VBox;
import jvstm.atomic.Combiner;
import jvstm.atomic.ParNest;
import jvstm.atomic.UnsafeSpawn;

/**
 * The main thread runs a transaction that attempts to increment two counters as an 
 * atomic action.
 * This example makes direct use of the JVSTM API for both top-level and unsafe parallel 
 * nested transactions.
 * @author nmld
 *
 */
public class SimpleUnsafeAnnotationsTest {

    public static void main(String[] args) {
	int result = new SimpleUnsafeAnnotationsTest(0, 0).start();
	if (result != 2) {
	    throw new AssertionError("Expected: 2; Obtained: " + result);
	}
	System.out.println("Expected: 2; Obtained: " + result);
    }

    protected final VBox<Integer> vbox1;
    protected final VBox<Integer> vbox2;

    public SimpleUnsafeAnnotationsTest(int arg1, int arg2) {
	this.vbox1 = new VBox<Integer>(arg1);
	this.vbox2 = new VBox<Integer>(arg2);
    }

    @Atomic(speculativeReadOnly = false)
    public int start() {
	return new ParallelIncrement().exec();
    }
    
    public class ParallelIncrement implements UnsafeSpawn<Integer> {

	@ParNest(readOnly = false)
	public Integer increment(VBox<Integer> vbox) {
	    vbox.put(vbox.get() + 1);
	    return vbox.get();
	}

	@Combiner
	public Integer combine(List<Integer> results) {
	    int sum = 0;
	    for (Integer res : results) {
		sum += res;
	    }
	    return sum;
	}
	
	@Override
	public Integer exec() {
	    increment(vbox1);
	    increment(vbox2);
	    return null;
	}
	
    }

}
