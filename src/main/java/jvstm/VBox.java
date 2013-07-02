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
package jvstm;

import static jvstm.UtilUnsafe.UNSAFE;

public class VBox<E> {

    /**
     * We moved here all VBox constants that are initialized with Unsafe operations,
     * due to the JVSTM integration in Deuce.
     * To support the JVSTM all the transactional classes are instrumented by the Deuce
     * to inherit from the VBox class. Yet, if a transactional class is part of the JRE
     * it can be loaded during the bootstrap, but the JVM bootstrap does not allow the use of
     * Unsafe operations.
     * Once the original VBox implementation uses the Unsafe class in its static constructor
     * then any inherited class from VBox will perform also an unsafe operation when it is
     * loaded, which is forbidden by the JVM bootstrap. For this reason we moved all these
     * constants into a separate class.
     */
    static class Offsets {

        // --- Setup to use Unsafe
        static final long bodyOffset = UtilUnsafe.objectFieldOffset(VBox.class, "body");
        static final long inplaceOffset = UtilUnsafe.objectFieldOffset(VBox.class, "inplace");

    }

    /**
     * This is a special auxiliary type to distinguish the overloaded constructor
     * that is required by the VBoxAom class.
     */
    protected static class AOMMarker {}

    public VBoxBody<E> body;
    protected InplaceWrite<E> inplace;

    public VBox() {
        this((E)null);
    }

    public VBox(E initial) {
        inplace = new InplaceWrite<E>();
        put(initial);
    }

    /**
     * This is a specific constructor required by the VBoxAom class.
     * Any transactional class defined in the AOM (Adaptive Object Metadata)
     * should inherit from VBoxAom, which in turn inherits from VBox, and
     * should initialize the versioned history with null, corresponding
     * to the compact layout.
     */
    protected VBox(AOMMarker x) {
        inplace = new InplaceWrite<E>();
        body = null;
    }

    /**
     * In this case the object will be instantiated in captured memory,
     * corresponding to memory  allocated inside a transaction that
     * cannot escape (i.e., is captured by) its allocating transaction.
     */
    protected VBox(AOMMarker x, Transaction owner) {
        inplace = new InplaceWrite<E>(owner);
        body = null;
    }

    // used for persistence support
    protected VBox(VBoxBody<E> body) {
        this.inplace = new InplaceWrite<E>();
        this.body = body;
    }

    public OwnershipRecord getOrec(){
        return inplace.orec;
    }

    public E get() {
        Transaction tx = Transaction.current();
        if (tx == null) {
            // Access the box body without creating a full transaction, while
            // still preserving ordering guarantees by 'piggybacking' on the
            // version from the latest commited transaction.
            // If the box body is GC'd before we can reach it, the process
            // re-starts with a newer transaction.
            while (true) {
                int transactionNumber = Transaction.mostRecentCommittedRecord.transactionNumber;
                VBoxBody<E> boxBody = this.body;
                do {
                    if (boxBody.version <= transactionNumber) {
                        return boxBody.value;
                    }
                    boxBody = boxBody.next;
                } while (boxBody != null);
            }
        } else {
            return tx.getBoxValue(this);
        }
    }

    public void put(E newE) {
        Transaction tx = Transaction.current();
        if (tx == null) {
            tx = Transaction.beginInevitable();
            tx.setBoxValue(this, newE);
            tx.commit();
        } else {
            tx.setBoxValue(this, newE);
        }
    }

    public VBoxBody<?> commit(E newValue, int txNumber) {
        VBoxBody<E> currentHead = this.body;

        VBoxBody<E> existingBody = null;
        if (currentHead != null) {
            existingBody = currentHead.getBody(txNumber);

            // Commented by FMC@17-09-2012 => it causes a crash in JVM for
            // transactional classes that inherit fom the VBox and loaded
            // during the bootstrap.
            // assert(existingBody == null || existingBody.version <= txNumber);
        }

        if (existingBody == null || existingBody.version < txNumber) {
            VBoxBody<E> newBody = makeNewBody(newValue, txNumber, currentHead);
            existingBody = CASbody(currentHead, newBody);
        }
        // return the existingBody, regardless of whether the CAS succeeded
        return existingBody;
    }

    /* Atomically replace the body with the new one iff the current body is the expected.
     *
     * Return the body that was actually kept.
     */
    protected VBoxBody<E> CASbody(VBoxBody<E> expected, VBoxBody<E> newValue) {
        /* In the pure JVSTM the CAS can only fail because another thread already
        committed this value.  However, when used together with Fenix Framework,
        it is possible that the body changes because of reloads.  We identify
        this by testing whether our commit did make it (this.body.version must
        be >= newValue.version).  If not, we retry the CAS.*/

        while (true) {
            if (UNSAFE.compareAndSwapObject(this, Offsets.bodyOffset, expected, newValue)) {
                return newValue;
            } else { // if the CAS failed the new value must already be there unless FenixFramework was doing a reload!
                expected = this.body; // update expected in case we need to loop

                if (expected.version < newValue.version) {
                    // retry;
                    continue;
                } else {
                    return this.body.getBody(newValue.version);
                }
            }
        }
    }

    protected boolean CASinplace(InplaceWrite<E> prevBackup, InplaceWrite<E> newBackup) {
        return UNSAFE.compareAndSwapObject(this, Offsets.inplaceOffset, prevBackup, newBackup);
    }

    public InplaceWrite<E> getInplace() {
        return this.inplace;
    }

    // in the future, if more than one subclass of body exists, we may
    // need a factory here but, for now, it's simpler to have it like
    // this
    public static <T> VBoxBody<T> makeNewBody(T value, int version, VBoxBody<T> next) {
        return new VBoxBody<T>(value, version, next);
    }

    /*===========================================================================*
     *~~~~~~~~~~~~~     METHODS of the AOM approach     ~~~~~~~~~~~~~~~~~~~~~~~~~*
     *===========================================================================*/

    private static final String ILLEGAL_AOM_USE = "this method is part of the AOM (Adaptive Object Metadata) approach and " +
            "should be overriden by VBox inherited classes.";

    public E replicate(){
        throw new UnsupportedOperationException("Illegal use of the replicate method - " + ILLEGAL_AOM_USE);
    }
    public void toCompactLayout(E from){
        throw new UnsupportedOperationException("Illegal use of the toCompactLayout method - " + ILLEGAL_AOM_USE);
    }

}
