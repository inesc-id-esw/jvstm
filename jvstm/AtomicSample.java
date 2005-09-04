public class AtomicSample {

    public void atomicVoid(int arg0, boolean arg1, Object arg2) {
        while (true) {
            Transaction.begin();
            boolean txFinished = false;
            try {
                internalAtomicVoid(arg0, arg1, arg2);
                Transaction.commit();
                txFinished = true;
                return;
            } catch (CommitException ce) {
                Transaction.abort();
                txFinished = true;
            } finally {
                if (! txFinished) {
                    Transaction.abort();
                }
            }
        }
    }

    private void internalAtomicVoid(int arg0, boolean arg1, Object arg2) {}


}
