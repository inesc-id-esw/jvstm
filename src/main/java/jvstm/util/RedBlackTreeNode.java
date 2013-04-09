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

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class RedBlackTreeNode<K,V> {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private static final int MODE_REPLACE = 0;
    private static final int MODE_IF_ABSENT = 1;
    private static final int MODE_ALWAYS = 2;

    public static final RedBlackTreeNode EMPTY = new RedBlackTreeNode(BLACK, null, null, null, null);

    private boolean color;
    private K key;
    private V value;
    private RedBlackTreeNode<K,V> left;
    private RedBlackTreeNode<K,V> right;


    private RedBlackTreeNode(boolean color, K key, V value, RedBlackTreeNode<K,V> left, RedBlackTreeNode<K,V> right) {
        this.color = color;
        this.key = key;
        this.value = value;
        this.left = left;
        this.right = right;
    }

    /**
     * Returns a new node that represents the tree with <tt>key</tt>
     * mapped to <tt>value</tt>.  The node returned is always a new
     * node, regardless of whether the key existed already in the tree
     * or not.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @param comparator the comparator that will be used to sort the
     *                   keys in the tree.  If <tt>null</tt>, the natural
     *                   order of the <tt>key</tt> is used, as per the
     *                   Comparable interface.
     * @return  a new node that represents a tree where the <tt>key</tt>
     *          is mapped to <tt>value</tt>.
     */
    public RedBlackTreeNode<K,V> put(K key, V value, Comparator<? super K> comparator) {
        return insert(key, value, comparator, MODE_ALWAYS).first;
    }

    public Pair<RedBlackTreeNode<K,V>,V> replace(K key, V value, Comparator<? super K> comparator) {
        return insert(key, value, comparator, MODE_REPLACE);
    }

    public Pair<RedBlackTreeNode<K,V>,V> putIfAbsent(K key, V value, Comparator<? super K> comparator) {
        return insert(key, value, comparator, MODE_IF_ABSENT);
    }



    private Pair<RedBlackTreeNode<K,V>,V> insert(K key,
                                                 V value,
                                                 Comparator<? super K> comparator,
                                                 int mode) {
        Pair<RedBlackTreeNode<K,V>,V> result = new Pair<RedBlackTreeNode<K,V>,V>();

        if (comparator == null) {
            insertComparable((Comparable<K>)key, value, result, mode);
        } else {
            insert(key, value, comparator, result, mode);
        }

        if (result.first != null) {
            result.first.color = BLACK;
        }
        return result;
    }


    private void insert(K key,
                        V value,
                        Comparator<? super K> comparator,
                        Pair<RedBlackTreeNode<K,V>,V> result,
                        int mode) {
        if (this == EMPTY) {
            if (mode != MODE_REPLACE) {
                result.first = new RedBlackTreeNode<K,V>(RED, key, value, EMPTY, EMPTY);
            }
        } else {
            int cmp = comparator.compare(key, this.key);
            if (cmp < 0) {
                this.left.insert(key, value, comparator, result, mode);
                if (result.first != null) {
                    lbalance(this, result);
                }
            } else if (cmp > 0) {
                this.right.insert(key, value, comparator, result, mode);
                if (result.first != null) {
                    rbalance(this, result);
                }
            } else {
                // key exists already
                if (mode != MODE_IF_ABSENT) {
                    result.first = new RedBlackTreeNode<K,V>(this.color, key, value, this.left, this.right);
                }
                result.second = this.value;
            }
        }
    }

    private void insertComparable(Comparable<K> key,
                                  V value,
                                  Pair<RedBlackTreeNode<K,V>,V> result,
                                  int mode) {
        if (this == EMPTY) {
            if (mode != MODE_REPLACE) {
                result.first = new RedBlackTreeNode<K,V>(RED, (K)key, value, EMPTY, EMPTY);
            }
        } else {
            int cmp = key.compareTo(this.key);
            if (cmp < 0) {
                this.left.insertComparable(key, value, result, mode);
                if (result.first != null) {
                    lbalance(this, result);
                }
            } else if (cmp > 0) {
                this.right.insertComparable(key, value, result, mode);
                if (result.first != null) {
                    rbalance(this, result);
                }
            } else {
                // key exists already
                if (mode != MODE_IF_ABSENT) {
                    result.first = new RedBlackTreeNode<K,V>(this.color, (K)key, value, this.left, this.right);
                }
                result.second = this.value;
            }
        }
    }

    private void lbalance(RedBlackTreeNode<K,V> node, Pair<RedBlackTreeNode<K,V>,V> result) {
        RedBlackTreeNode<K,V> left = result.first;

        if ((node.color == BLACK) && (left.color == RED)) {
            if (left.left.color == RED) {
                result.first = new RedBlackTreeNode<K,V>(RED,
                                                         left.key,
                                                         left.value,
                                                         new RedBlackTreeNode<K,V>(BLACK, left.left.key, left.left.value, left.left.left, left.left.right),
                                                         new RedBlackTreeNode<K,V>(BLACK, node.key, node.value, left.right, node.right));
                return;
            }

            if (left.right.color == RED) {
                result.first = new RedBlackTreeNode<K,V>(RED,
                                                         left.right.key,
                                                         left.right.value,
                                                         new RedBlackTreeNode<K,V>(BLACK, left.key, left.value, left.left, left.right.left),
                                                         new RedBlackTreeNode<K,V>(BLACK, node.key, node.value, left.right.right, node.right));
                return;
            }
        }

        result.first = new RedBlackTreeNode<K,V>(node.color, node.key, node.value, left, node.right);
    }

    private void rbalance(RedBlackTreeNode<K,V> node, Pair<RedBlackTreeNode<K,V>,V> result) {
        RedBlackTreeNode<K,V> right = result.first;

        if ((node.color == BLACK) && (right.color == RED)) {
            if (right.left.color == RED) {
                result.first = new RedBlackTreeNode<K,V>(RED,
                                                         right.left.key,
                                                         right.left.value,
                                                         new RedBlackTreeNode<K,V>(BLACK, node.key, node.value, node.left, right.left.left),
                                                         new RedBlackTreeNode<K,V>(BLACK, right.key, right.value, right.left.right, right.right));
                return;
            }

            if (right.right.color == RED) {
                result.first = new RedBlackTreeNode<K,V>(RED,
                                                         right.key,
                                                         right.value,
                                                         new RedBlackTreeNode<K,V>(BLACK, node.key, node.value, node.left, right.left),
                                                         new RedBlackTreeNode<K,V>(BLACK, right.right.key, right.right.value, right.right.left, right.right.right));
                return;
            }
        }

        result.first = new RedBlackTreeNode<K,V>(node.color, node.key, node.value, node.left, right);
    }

    public V get(K key, Comparator<? super K> comparator) {
        RedBlackTreeNode<K,V> node = getNode(key, comparator);
        return (node == null) ? null : node.value;
    }

    protected RedBlackTreeNode<K,V> getNode(K key, Comparator<? super K> comparator) {
        if (comparator == null) {
            return findNodeComparable((Comparable<K>)key);
        } else {
            return findNode(key, comparator);
        }
    }

    private RedBlackTreeNode<K,V> findNode(K key, Comparator<? super K> comparator) {
        RedBlackTreeNode<K,V> iter = this;

        while (iter != EMPTY) {
            int cmp = comparator.compare(key, iter.key);
            if (cmp < 0) {
                iter = iter.left;
            } else if (cmp > 0) {
                iter = iter.right;
            } else {
                return iter;
            }
        }

        return null;
    }

    private RedBlackTreeNode<K,V> findNodeComparable(Comparable<K> key) {
        RedBlackTreeNode<K,V> iter = this;

        while (iter != EMPTY) {
            int cmp = key.compareTo(iter.key);
            if (cmp < 0) {
                iter = iter.left;
            } else if (cmp > 0) {
                iter = iter.right;
            } else {
                return iter;
            }
        }

        return null;
    }


    public Iterator<RedBlackTreeNode<K,V>> iterator() {
        return new RBTIterator<K,V>(this);
    }

    static class RBTIterator<K,V> implements Iterator<RedBlackTreeNode<K,V>> {
        protected Cons<RedBlackTreeNode<K,V>> path;
        protected RedBlackTreeNode<K,V> next;

        RBTIterator() {
            this.path = Cons.empty();
        }

        RBTIterator(RedBlackTreeNode<K,V> root) {
            this();
            if (root != EMPTY) {
                findLeftmost(root);
            }
        }

        private void findLeftmost(RedBlackTreeNode<K,V> node) {
            while (node.left != EMPTY) {
                path = path.cons(node);
                node = node.left;
            }
            this.next = node;
        }

        public boolean hasNext() {
            return next != null;
        }

        public RedBlackTreeNode<K,V> next() {
            if (next == null) {
                throw new NoSuchElementException();
            } else {
                RedBlackTreeNode<K,V> result = next;

                if (next.right != EMPTY) {
                    findLeftmost(next.right);
                } else {
                    // no elements to the right, so climb up the tree
                    if (path == Cons.EMPTY) {
                        next = null;
                    } else {
                        next = path.first;
                        path = path.rest;
                    }
                }

                return result;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
