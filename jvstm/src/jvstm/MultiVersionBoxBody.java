package jvstm;

public class MultiVersionBoxBody<E> extends VBoxBody<E> {
    volatile MultiVersionBoxBody<E> next = null;
    
    public VBoxBody<E> getBody(int maxVersion) {
        return ((version > maxVersion) 
                ? next.getBody(maxVersion)
                : this);
    }

    public void setPrevious(VBoxBody<E> previous) {
	next = (MultiVersionBoxBody<E>)previous;
    }

    public void clearPrevious() {
	next = null;
    }
}
