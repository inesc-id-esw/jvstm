package jvstm;

import java.util.concurrent.atomic.AtomicBoolean;

import jvstm.util.Cons;

public class BoxesToCommit {

    public static final BoxesToCommit EMPTY_BOXES = new BoxesToCommit(); 
    
    /*
     * There are nBlocks of VBoxes ranged from 0 to nBlocks-1. Any i-th block
     * (except the last one) manages VBoxes from i*blockSize (including) to
     * i*blockSize+blockSize (excluding). The last block manages the range from
     * i*blockSize (including) to writeSetLength (excluding).
     */
    protected final int nBlocks;
    /* The number of VBoxes per block */
    protected final int blockSize;
    /* All the VBoxes lined-up in a fixed array for direct access */
    protected final VBox[] allWrittenVBoxes;
    protected final Object[] allWrittenValues;
    // the previous arrays may be allocated in a larger size than required.
    // This happens when the same box is first written to the standard
    // write-set and later re-written in-place. For this reason we should
    // never use the arrays 'length' attribute; use instead writeSetLength.
    protected final int writeSetLength;
    /* The VBoxBodies created when writing-back each block of VBoxes */
    protected final Cons<GarbageCollectable>[] bodiesPerBlock;
    /* A write-back status for each bucket */
    protected final AtomicBoolean[] blocksDone;
    
    private BoxesToCommit() {
	this.nBlocks = 0;
	this.blockSize = 0;
	this.allWrittenVBoxes = new VBox[0];
	this.allWrittenValues = new Object[0];
	this.writeSetLength = 0;
	this.bodiesPerBlock = new Cons[0];
	this.blocksDone = new AtomicBoolean[0];
    }
    
    public BoxesToCommit(int nBlocks, int blockSize, VBox[] allWrittenVBoxes, Object[] allWrittenValues, int writeSetLength,
	    Cons<GarbageCollectable>[] bodiesPerBlock, AtomicBoolean[] blocksDone) {
	this.nBlocks = nBlocks;
	this.blockSize = blockSize;
	this.allWrittenVBoxes = allWrittenVBoxes;
	this.allWrittenValues = allWrittenValues;
	this.writeSetLength = writeSetLength;
	this.bodiesPerBlock = bodiesPerBlock;
	this.blocksDone = blocksDone;
    }
    
}
