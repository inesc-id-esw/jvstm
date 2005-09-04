package jvstm;

public class MultiVersionBoxBody<E> extends VBoxBody<E> {
    volatile MultiVersionBoxBody<E> next = null;
    
    VBoxBody<E> getBody(int maxVersion) {
        return ((version > maxVersion) 
                ? next.getBody(maxVersion)
                : this);
    }

    void commit(VBoxBody<E> previous) {
	next = (MultiVersionBoxBody<E>)previous;
    }
}
