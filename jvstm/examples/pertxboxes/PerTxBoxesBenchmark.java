import jvstm.CommitException;
import jvstm.Transaction;
import jvstm.VBox;

// Create some false conflicts with perTxBoxes and check performance
// args: #texts, #counters, #threads, #txs, #interval-of-txs-between-conflict
public class PerTxBoxesBenchmark {

    public static final String TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Duis vehicula cursus libero, vitae ullamcorper dui volutpat at. Integer laoreet sapien ac velit imperdiet id venenatis massa iaculis. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Mauris nec enim mi. Nulla nisl augue, dapibus sit amet blandit ac, consectetur id enim. Phasellus pellentesque scelerisque eros, vitae pellentesque dui dignissim vitae. Fusce placerat nibh vel ligula ultrices pretium. Pellentesque at erat odio, nec faucibus nisi. Sed mattis dui et sem sodales vel mattis tortor ultricies. Vivamus nec nulla libero, et porttitor ipsum. Quisque at elit velit, nec dictum lorem. Sed quis sapien in mauris sagittis laoreet.";
    public final Worker[] workers;
    public final CounterPerTxBox[] counters;
    public final VBox<Long> contention;

    public static void main(String[] args) {
	new PerTxBoxesBenchmark(args).execute();
    }

    public PerTxBoxesBenchmark(String[] args) {
	int documentsSize = Integer.parseInt(args[0]);
	this.counters = new CounterPerTxBox[Integer.parseInt(args[1])];
	this.workers = new Worker[Integer.parseInt(args[2])];
	this.contention = new VBox<Long>(0L);

	for (int i = 0; i < this.counters.length; i++) {
	    this.counters[i] = new CounterPerTxBox(0);
	}
	for (int i = 0; i < this.workers.length; i++) {
	    VBox<String>[] documents = new VBox[documentsSize];
	    for (int j = 0; j < documents.length; j++) {
		documents[j] = new VBox<String>(TEXT);
	    }
	    this.workers[i] = new Worker(documents, Integer.parseInt(args[3]), Integer.parseInt(args[4]));
	}
    }

    public void execute() {
	long start = System.currentTimeMillis();
	try {
	    for (Worker worker : workers) {
		worker.start();
	    }
	    for (Worker worker : workers) {
		worker.join();
	    }		
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    System.exit(1);
	}
	long total = System.currentTimeMillis() - start;
	System.out.println(total);
    }

    public class Worker extends Thread {
	private final VBox<String>[] documents;
	private final int txs;
	private final int interval;

	public Worker(VBox<String>[] documents, int txs, int interval) {
	    this.documents = documents;
	    this.txs = txs;
	    this.interval = interval;
	}

	public void run() {
	    boolean swap = false;
	    for (int i = 0; i < txs; i++) {
		while (true) {
		    Transaction tx = Transaction.begin();
		    try {
			for (VBox<String> vbox : documents) {
			    if (swap) {
				vbox.put(vbox.get().replace("mauris", "maurus"));
			    } else {
				vbox.put(vbox.get().replace("maurus", "mauris"));
			    }
			}
			for (CounterPerTxBox counter : counters) {
			    counter.inc();
			}
			if ((i % interval) == 0) {
			    contention.put(contention.get() + 1);
			}
			Transaction.commit();
		    } catch(CommitException ce) {
			if (tx != null) {
			    tx.abort();
			    tx = null;
			}
		    }
		    if (tx != null) {
			break;
		    }
		}
	    }
	}
    }

}
