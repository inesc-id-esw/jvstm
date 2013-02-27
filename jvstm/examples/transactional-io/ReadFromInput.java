import java.io.IOException;

import jvstm.util.TransactionalInputStream;
import jvstm.VBox;

import pt.ist.esw.atomicannotation.Atomic;

class ReadFromInput {
    static VBox<Integer> counter = new VBox<Integer>(0);
    static boolean first = true;

    @Atomic
    private static void getInput(TransactionalInputStream txInput, int[] bArray) {
        try {
            bArray[0] = txInput.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * readOnce is a method that reads a byte from the InputStream, but, by
     * doing that the transactions abort because it was started at
     * speculatively Read-Only but by using the TxInput it changes a box so
     * it aborts and runs again as Read-Write. However, on the second run,
     * the semantic of the method changes and it doesn't ask for input
     * anymore, the transaction commits but there are no values written on
     * boxes to commit. Nevertheless, a byte was read and written to the
     * buffer of the TxInput and is there ready to be reused
     */
    @Atomic
    private static void readOnce(TransactionalInputStream txInput) {
        if (first) {
            first = false;
            for (int i = 0; i < 1; ++i) {
                try {
                    txInput.read();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        final TransactionalInputStream txInput = new TransactionalInputStream(System.in);
        readOnce(txInput);

        /*
         * The following thread ask for a byte, it should then reuse the byte
         * that was previously asked but not used by readOnce
         */

        new Thread() {
            public void run() {
                for (int g = 0; g < 1; ++g) {
                    int[] bArray = new int[1];
                    getInput(txInput, bArray);
                    System.out.println((char) bArray[0]);
                }
            }
        }.start();
    }
}
