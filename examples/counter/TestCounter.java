import jvstm.Transaction;
import jvstm.VBoxLong;

public class TestCounter {

    public static void main(String[] args) {
	final VBoxLong vbox = new VBoxLong(0);
	final Counter counter = new CFOCounter();

	new Thread() {
	    @Override
	    public void run() {
		while (true) {
		    Transaction.begin();
		    counter.inc();
		    vbox.put(1L);
		    Transaction.commit();
		    try {
			Thread.sleep(100);
		    } catch (Exception e) {
			// ok
		    }
		}
	    }
	}.start();

	new Thread() {
	    @Override
	    public void run() {
		while (true) {
		    Transaction.begin();
		    counter.inc();
		    vbox.put(1L);
		    Transaction.commit();
		    try {
			Thread.sleep(100);
		    } catch (Exception e) {
			// ok
		    }
		}
	    }
	}.start();

	new Thread() {
	    @Override
	    public void run() {
		try {
		    Thread.sleep(1000);
		} catch (Exception e) {
		    // ok
		}
		while (true) {
		    Transaction.begin(true);
		    System.out.println("Value = " + counter.getCount() + " " + vbox.get());
		    Transaction.commit();
		    try {
			Thread.sleep(100);
		    } catch (Exception e) {
			// ok
		    }
		}
	    }
	}.start();
    }
}
