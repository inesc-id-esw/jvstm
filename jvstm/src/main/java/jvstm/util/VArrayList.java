/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm.util;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicReferenceArray;

import jvstm.Atomic;
import jvstm.VArray;
import jvstm.VBox;
import jvstm.VBoxInt;

/** Versioned ArrayList implementation. **/
/* Lots of optimizations can still be done. Wherever a method's implementation
 * is reused from its superclass, there might be a potential optimization.
 */
public class VArrayList<E> extends AbstractList<E> implements RandomAccess, Deque<E> {

    private final VBox<VArray<E>> array;

    private final VArray<E> array() {
        return array.get();
    }

    private final VBoxInt size;

    public VArrayList() {
        this(10);
    }

    public VArrayList(Collection<? extends E> c) {
        this(c.size());
        addAll(c);
    }

    public VArrayList(int initialCapacity) {
        array = new VBox<VArray<E>>(new VArray<E>(initialCapacity));
        size = new VBoxInt();
    }

    // List / ArrayList methods

    @Override
    @Atomic(speculativeReadOnly = true)
    public boolean add(E e) {
        int size = size();

        ensureCapacity(size + 1);
        array().put(size, e);
        this.size.putInt(size + 1);

        return true;
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public void add(int index, E element) {
        int size = size();
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException();
        }

        ensureCapacity(size + 1);
        VArray<E> array = array();      // Must be done AFTER ensureCapacity!

        for (int i = size - 1; i >= index; i--) {
            array.put(i + 1, array.get(i));
        }
        array.put(index, element);
        this.size.put(size + 1);
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public boolean addAll(Collection<? extends E> c) {
        if (c.size() == 0) {
            return false;
        }

        int size = size();

        ensureCapacity(size + c.size());
        VArray<E> array = array();      // Must be done AFTER ensureCapacity!

        for (E element : c) {
            array.put(size++, element);
        }

        this.size.putInt(size);
        return true;
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public boolean addAll(int index, Collection<? extends E> c) {
        int size = size();
        if (index > size) {
            throw new IndexOutOfBoundsException();
        }

        ensureCapacity(size + c.size());
        return super.addAll(index, c);
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public void clear() {
        // Let the GC do its job
        array.put(new VArray<E>(array().length));
        size.putInt(0);
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    @Atomic(readOnly = true)
    public boolean containsAll(Collection<?> c) {
        return super.containsAll(c);
    }

    @Atomic
    public void ensureCapacity(int minCapacity) {
        if (minCapacity <= array().length) {
            return;
        }

        // Calculate new size for array
        int minCapHighBit = Integer.highestOneBit(minCapacity);
        int newCapacity = minCapHighBit << 1;
        if (newCapacity < 0) {
            newCapacity = Integer.MAX_VALUE;
        } else if ((minCapacity & (minCapHighBit >> 1)) > 0) {
            newCapacity += minCapHighBit;
        }

        resize(newCapacity);
    }

    @Atomic(speculativeReadOnly = false)
    public void trimToSize() {
        int size = size();

        if (array().length > size) {
            resize(size);
        }
    }

    /* Should only be called from an Atomic context */
    private void resize(int newCapacity) {
        // To resize the array, we create a new one and copy over the elements from the old one
        // As this operation is very time-sensitive, this implementation uses inside knowledge of
        // VArray to go faster
        int size = size();
        VArray<E> oldVArray = array();
        VArray<E> newVArray = new VArray<E>(newCapacity);

        // Get the AtomicReferenceArray from the underlying VArray
        AtomicReferenceArray<E> realArray = newVArray.values;
        // Copy elements over
        for (int i = 0; i < size; i++) {
            realArray.lazySet(i, oldVArray.get(i));
        }
        // Update VBox
        this.array.put(newVArray);
    }

    @Override
    @Atomic(readOnly = true)
    /** Note that this operation is only transactional if both lists are transactional. **/
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public E get(int index) {
        if (index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        return array().get(index);
    }

    @Override
    @Atomic(readOnly = true)
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    @Atomic(readOnly = true)
    public int indexOf(Object o) {
        return super.indexOf(o);
    }

    @Override
    @Atomic(readOnly = true)
    public int lastIndexOf(Object o) {
        return super.lastIndexOf(o);
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public E remove(int index) {
        int size = size();
        if (index >= size) {
            throw new IndexOutOfBoundsException();
        }

        VArray<E> array = array();
        int newSize = size - 1;

        E oldValue = array.get(index);

        for (int i = index; i < newSize; i++) {
            array.put(i, array.get(i + 1));
        }
        array.put(newSize, null);
        this.size.put(newSize);

        return oldValue;
    }

    @Override
    @Atomic
    public boolean remove(Object o) {
        int index = indexOf(o);

        if (index < 0) {
            return false;
        } else {
            remove(index);
            return true;
        }
    }

    @Override
    @Atomic
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    protected void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    @Atomic
    public boolean retainAll(Collection<?> c) {
        return super.retainAll(c);
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public E set(int index, E element) {
        if (index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        VArray<E> array = array();

        E oldValue = array.get(index);
        array.put(index, element);

        return oldValue;
    }

    @Override
    public final int size() {
        return size.getInt();
    }

    @Override
    @Atomic(readOnly = true)
    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Atomic(readOnly = true)
    public <T> T[] toArray(T[] a) {
        VArray<E> array = array();
        int size = size();

        if (a.length < size) {
            // Need to create a bigger array
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        }

        for (int i = 0; i < size; i++) {
            a[i] = (T) array.get(i);
        }

        if (a.length > size) {
            a[size] = null;
        }

        return a;
    }

    @Override
    @Atomic(readOnly = true)
    public String toString() {
        return super.toString();
    }

    // Deque methods

    @Override
    public void addFirst(E e) {
        add(0, e);
    }

    @Override
    public void addLast(E e) {
        add(e);
    }

    @Override
    public Iterator<E> descendingIterator() {
        return new ListIteratorReverser<E>(listIterator(size()));
    }

    @Override
    public E element() {
        return getFirst();
    }

    @Override
    @Atomic(readOnly = true)
    public E getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return peekFirst();
    }

    @Override
    @Atomic(readOnly = true)
    public E getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return peekLast();
    }

    @Override
    public boolean offer(E e) {
        return offerLast(e);
    }

    @Override
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @Override
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    @Override
    public E peek() {
        return peekFirst();
    }

    @Override
    public E peekFirst() {
        try {
            return get(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @Override
    @Atomic(readOnly = true)
    public E peekLast() {
        try {
            return get(size() - 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @Override
    public E poll() {
        return pollFirst();
    }

    @Override
    public E pollFirst() {
        try {
            return remove(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public E pollLast() {
        try {
            return remove(size() - 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @Override
    public E pop() {
        return removeFirst();
    }

    @Override
    public void push(E e) {
        addFirst(e);
    }

    @Override
    public E remove() {
        return removeFirst();
    }

    @Override
    public E removeFirst() {
        try {
            return remove(0);
        } catch (IndexOutOfBoundsException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    @Override
    @Atomic(speculativeReadOnly = false)
    public E removeLast() {
        try {
            return remove(size() - 1);
        } catch (IndexOutOfBoundsException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    @Atomic
    public boolean removeLastOccurrence(Object o) {
        try {
            remove(lastIndexOf(o));
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    // Extra utility stuff

    private static final class ListIteratorReverser<E> implements Iterator<E>, Iterable<E> {
        final ListIterator<E> listIterator;

        ListIteratorReverser(ListIterator<E> listIterator) {
            this.listIterator = listIterator;
        }

        @Override
        public boolean hasNext() {
            return listIterator.hasPrevious();
        }

        @Override
        public E next() {
            return listIterator.previous();
        }

        @Override
        public void remove() {
            listIterator.remove();
        }

        @Override
        public Iterator<E> iterator() {
            return this;
        }
    }

    public VArrayList(Iterator<? extends E> it) {
        this();
        while (it.hasNext()) {
            add(it.next());
        }
    }

    public E first() {
        return getFirst();
    }

    public E last() {
        return getLast();
    }

    /**
     * Returns an Iterable object, suitable for using with foreach to iterate
     * over the current list in reverse order.
     **/
    public Iterable<E> reverseIteration() {
        return new ListIteratorReverser<E>(listIterator(size()));
    }

    /**
     * Returns a new list, with the same elements as the current list, but with
     * a reversed order.
     **/
    @Atomic(readOnly = true)
    public VArrayList<E> reversed() {
        return new VArrayList<E>(descendingIterator());
    }

}
