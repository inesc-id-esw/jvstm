package simple;

import jvstm.Atomic;
import jvstm.VBox;
import jvstm.atomic.Combiner;
import jvstm.atomic.ParNest;
import jvstm.atomic.ParallelSpawn;

/**
 * The main thread runs a top-level transaction, which will increment a counter in parallel.
 * This parallelization is related to the number of 'times' the "increment()" method is 
 * called within the ParallelIncrement class. The bytecode rewriting changes the "exec()" 
 * method such that the invocations to @ParNest methods are performed in parallel.
 * @author nmld
 *
 */
public class SimpleParallelAnnotationsTest {

    public static void main(String[] args) {
	int result = new SimpleParallelAnnotationsTest(0).start();
	if (result != 10) {
	    throw new AssertionError("Expected: 10; Obtained: " + result);
	}
	System.out.println("Expected: 10; Obtained: " + result);
    }

    protected final VBox<Integer> vbox;
    
    public SimpleParallelAnnotationsTest(int arg) {
	this.vbox = new VBox<Integer>(arg);
    }

    @Atomic(speculativeReadOnly = false)
    public int start() {
	return new ParallelIncrement(4).exec();
    }

    protected class ParallelIncrement implements ParallelSpawn<Integer> {
	private int times;
	public ParallelIncrement(int times) { this.times = times; }

	@ParNest
	public int increment() {
	    vbox.put(vbox.get() + 1);
	    return vbox.get();
	}

	@Combiner
	public Integer combine(java.util.List<Integer> arg) {
	    int result = 0;
	    for (int i = 0; i < arg.size(); i++) {
		Integer current = arg.get(i);
		result += current;
	    }
	    return result;
	}

	@Override
	public Integer exec() {
	    for (int i = 0; i < this.times; i++) {
		increment();
	    }
	    return null;
	}
    }

}
