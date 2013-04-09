package jvstm.test.jwormbench;

import jwormbench.core.IWorm;

/**
 * Abstracts the node object within the BenchWorld.
 *
 * @author F. Miguel Carvalho mcarvalho[@]cc.isel.pt
 */
public class BenchWorldNode__FIELDS__ extends BenchWorldNodeAom{
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -------------------   CONSTRUCTOR -----------------
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public BenchWorldNode__FIELDS__(int value){
        super(value);
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -------------------   PROPERTIES  -----------------
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    @Override
    public int getValue() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void setValue(int value) {
        throw new UnsupportedOperationException();
    }
    @Override
    public IWorm getWorm() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void setWorm(IWorm w) {
        throw new UnsupportedOperationException();
    }
  }
