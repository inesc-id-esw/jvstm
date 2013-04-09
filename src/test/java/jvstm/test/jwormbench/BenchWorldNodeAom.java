package jvstm.test.jwormbench;

import jvstm.UtilUnsafe;

import jvstm.ReadWriteTransaction;
import jvstm.Transaction;
import jvstm.VBox;
import jvstm.AomBarriers;
import jvstm.VBoxAom;
import jwormbench.core.INode;
import jwormbench.core.IWorm;

/**
 * Abstracts the node object within the BenchWorld.
 *
 * @author F. Miguel Carvalho mcarvalho[@]cc.isel.pt
 */
public class BenchWorldNodeAom extends VBoxAom<BenchWorldNodeAom> implements INode{
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*
     *------------       AOM INFRA-STRUCTURE    -----------------*
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    private static final long value__ADDRESS__;
    static{
        try {
            value__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(BenchWorldNodeAom.class.getDeclaredField("value"));
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // ---------------------- FIELDS ---------------------
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    protected int value;
    protected IWorm worm;

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -------------------   CONSTRUCTOR -----------------
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public BenchWorldNodeAom(int value){
        this.value = value;
        worm = null;
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -------------------   PROPERTIES  -----------------
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    /**
     * @see wormbench.INode#getValue()
     */
    public int getValue() {
        return AomBarriers.get(Transaction.current(), this).value;
    }
    /**
     * @see wormbench.INode#setValue(int)
     */
    public void setValue(int value) {
        AomBarriers.put((ReadWriteTransaction) Transaction.current(), this, value, value__ADDRESS__);
    }
    /**
     * @see wormbench.INode#getWorm()
     */
    public IWorm getWorm() {
        return worm;
    }
    /**
     * @see wormbench.INode#setWorm(IWorm)
     */
    public void setWorm(IWorm w) {
        this.worm = w;
        /*
  if(worm.get() != w && worm.get() != null && w!= null)
    throw new NodeAlreadyOccupiedException(
        String.format("Worm %s can not move to node with worm %s", w.getName(), worm.get().getName()));
  worm.put(w);
         */
    }
    @Override
    public BenchWorldNodeAom replicate() {
        return new BenchWorldNode__FIELDS__(value);
    }
    @Override
    public void toCompactLayout(BenchWorldNodeAom from) {
        this.value = from.value;
    }
}
