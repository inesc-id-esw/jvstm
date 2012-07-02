import jvstm.Transaction;

public class TestCounter {

    public static void main(String[] args) {
	final Counter counter = new CFOCounter();

	new Thread() {
	    @Override
	    public void run() {
		while (true) {
		    Transaction.begin();
		    counter.inc();
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
		    Transaction.begin(true);
		    System.out.println("Value = " + counter.getCount());
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
