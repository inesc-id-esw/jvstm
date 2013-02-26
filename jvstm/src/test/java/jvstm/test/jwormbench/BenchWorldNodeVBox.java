package jvstm.test.jwormbench;

import jvstm.UtilUnsafe;

import jvstm.ReadWriteTransaction;
import jvstm.Transaction;
import jvstm.VBox;
import jvstm.AomBarriers;
import jwormbench.core.INode;
import jwormbench.core.IWorm;

/**
 * Abstracts the node object within the BenchWorld.
 * 
 * @author F. Miguel Carvalho mcarvalho[@]cc.isel.pt 
 */
public class BenchWorldNodeVBox implements INode{
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // ---------------------- FIELDS --------------------- 
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    
    protected VBox<Integer> value = new VBox<Integer>();
    protected IWorm worm;

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -------------------   CONSTRUCTOR ----------------- 
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public BenchWorldNodeVBox(int value){
	this.value.put(value);
	worm = null;
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -------------------   PROPERTIES  ----------------- 
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    /**
     * @see wormbench.INode#getValue()
     */
    public int getValue() {
        return this.value.get();
    }
    /**
     * @see wormbench.INode#setValue(int)
     */
    public void setValue(int value) {
	this.value.put(value);
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
}
