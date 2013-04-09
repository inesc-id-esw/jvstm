package jvstm.test.jwormbench;

import jvstm.AomBarriers;
import jvstm.Transaction;
import jvstm.UtilUnsafe;
import jvstm.VBoxAom;
import jwormbench.core.ICoordinate;

/**
 * Although the instances of Coordinate are thread-local,
 * we should transactify this class because this is the
 * behavior of a transparent instrumentation engine such
 * as the Deuce Stm.
 */
public class JvstmCoordinate extends VBoxAom<JvstmCoordinate> implements ICoordinate {

    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*
     *------------       AOM INFRA-STRUCTURE    -----------------*
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    private static final long x__ADDRESS__;
    private static final long y__ADDRESS__;
    static{
        try {
        x__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(JvstmCoordinate.class.getDeclaredField("x"));
        y__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(JvstmCoordinate.class.getDeclaredField("y"));
        } catch (SecurityException e) {
        throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
        }
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // ---------------------- FIELDS ---------------------
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private int x;
    private int y;

    public JvstmCoordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int getX() {
        return AomBarriers.get(Transaction.current(), this).x;
    }

    @Override
    public void setX(int x) {
        // AomBarriers.put((ReadWriteTransaction) Transaction.current(), this, x, x__ADDRESS__);
        /*
         * In the JWormBench the coordinates are updated out of the scope
         * of a transaction. In the Deuce this means that the corresponding
         * fields will be updated in pace.
         * Yet, in JVSTM and through AomBarriers that is not possible, because
         * even when the current transaction is null a new Inevitable one will
         * be created to perform the update.
         * So, to avoid the overhead of the write Barrier we do not it here.
         */
        this.x = x;
    }

    @Override
    public int getY() {
        return AomBarriers.get(Transaction.current(), this).y;
    }

    @Override
    public void setY(int y) {
        // AomBarriers.put((ReadWriteTransaction) Transaction.current(), this, y, y__ADDRESS__);
        this.y = y;
    }

    @Override
    public JvstmCoordinate replicate() {
     return new JvstmCoordinate(x, y);
    }

    @Override
    public void toCompactLayout(JvstmCoordinate from) {
        this.x = from.x;
        this.y = from.y;
    }
}
