package simple;

import java.util.ArrayList;
import java.util.List;

import jvstm.ParallelTask;
import jvstm.Transaction;
import jvstm.VBox;

public class NestedValidationTest {

    public static final VBox<Integer> vbox = new VBox<Integer>(0);
    
    public static void main(String[] args) {
	Transaction topLevel = Transaction.begin(false);
	
	vbox.put(1);
	
	List<ParallelTask<Void>> topNested = new ArrayList<ParallelTask<Void>>();
	
	topNested.add(new ParallelTask<Void>() {
	    @Override
	    public Void execute() throws Throwable {
		int t = vbox.get();
		Thread.sleep(1000);
		vbox.put(t + 1);

		return null;
	    }
	});
	
	topNested.add(new ParallelTask<Void>() {
	    @Override
	    public Void execute() throws Throwable {
		Thread.sleep(500);
		vbox.put(10);
		return null;
	    }
	});
	
	Transaction.current().manageNestedParallelTxs(topNested);
	
	topLevel.commit();
	
	System.out.println(vbox.get());
    }
    
}
