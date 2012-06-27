package jvstm;

import java.util.concurrent.atomic.AtomicInteger;


public class ReadBlock {

    protected boolean free;
    protected VBox[] entries;
    protected AtomicInteger freeBlocks;
    
    public ReadBlock(AtomicInteger freeBlocks) {
	this.free = false;
	this.entries = new VBox[1000];
	this.freeBlocks = freeBlocks;
    }
    
}
