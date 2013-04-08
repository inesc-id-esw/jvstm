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

public class VBoxAom<E> extends VBox<E>{

    private static final AOMMarker DEFAULT_MARKER = new AOMMarker();

    public VBoxAom(){
        /**
         * The super constructor will initialize the body with null,
         * corresponding to the compact layout.
         */
        super(DEFAULT_MARKER);
    }

    /**
     * In this case the object will be instantiated in captured memory,
     * corresponding to memory  allocated inside a transaction that
     * cannot escape (i.e., is captured by) its allocating transaction.
     */
    public VBoxAom(Transaction owner){
        /**
         * The super constructor will initialize the body with null,
         * corresponding to the compact layout.
         */
        super(DEFAULT_MARKER, owner);
    }

    /**
     * The correct constraint for T should be: T extends E & VBox<E>.
     * Yet, Java generics does not allow the previous constraint.
     *
     * During the write-back phase of the commit, when adding a new version to a transactional
     * object, AOM must first check if the object is in the extended layout. If it is, then it
     * just creates a new version and appends it at the head of the history, as JVSTM did.
     * If the object is in the compact layout, however, it must extend the object and add
     * the new version. This is done by executing the following three steps:
     *
     * - (1) create a new VBoxBody instance tagged with version zero, containing the current
     * values of the object's fields;
     *
     * - (2) create a second VBoxBody instance containing the new values produced during the
     * transaction and pointing to the previous VBoxBody instance;
     *
     * - (3) update the object's header so that it points to the previous VBoxBody instance.
     *
     * The 3rd step is performed with a CAS operation and it could fail in two differents situations:
     *
     * - (1) an helper transaction already committed the new value.
     *
     * - (2) the reversion process converted the vbox to the compact layout. But in this case it will try
     * to install again the new history overwriting the body field (see the CASbody method).
     *
     * A problematic situation of ABA could happen when a transaction Tw1 sees the vbox in the compact layout
     * and a concurrent helper transaction Tw2 committed first to this vbox and the reversion process Tr reverts
     * it back to the compact layout:
     *
     *   Tw1(reads vbox) -> Tw2(commits vbox) -> Tr(revert vbox) -> Tw1(commits)
     *
     * In this case the CAS performed by Tw1 would succeed because the vbox's body is null, but an ABA
     * problem could arise. Yet, it cannot happen the ABA situation because while Tw1 is in execution
     * then the corresponding ActiveTxRecord cannot be cleaned by the GC.
     *
     */
    @Override
    public VBoxBody<?> commit(E newValue, int txNumber) {
        VBoxBody<E> currentHead = this.body;

        VBoxBody<E> existingBody = null;
        if (currentHead != null) {
            existingBody = currentHead.getBody(txNumber);
        }
        if (existingBody == null || existingBody.version < txNumber) {
            VBoxBody<E> newBody = null;
            if(currentHead == null){
                /*
                 * This object is still in the compact layout and it must be extended
                 * before committing the new value:
                 * - 1st - The replicate method will instantiate and initialize a new object
                 * with the corresponding values of the object in the compact layout - like a clone.
                 * - 2nd - Instantiate a VBoxBody object containing the previously
                 * initialized object.
                */
                newBody = makeNewBody(newValue, txNumber, new VBoxBody<E>(this.replicate(), 0, null));
            }
            else{
                newBody = makeNewBody(newValue, txNumber, currentHead);
            }
            /*
             * - 3rd - if this object was extended in the previous if, then
             * this task corresponds to the 3rd step of the object extension.
             */
            existingBody = CASbody(currentHead, newBody);
        }
        // return the existingBody, regardless of whether the CAS succeeded
        return existingBody;
    }

    /* Atomically replace the body with the new one if the current body is the expected.
     *
     * Return the body that was actually kept.
     */
    @Override
    protected VBoxBody<E> CASbody(VBoxBody<E> expected, VBoxBody<E> newValue){
        do{
            if (UNSAFE.compareAndSwapObject(this, Offsets.bodyOffset, expected, newValue)) {
                expected = newValue;
            } else {
                /*
                 * If the CAS failed the new value must already be there!
                 * Or the object may be reverted by a concurrent GCTask and
                 * we will retry to commit the new body.
                 */
                expected = this.body;
                if(expected != null)
                    expected = expected.getBody(newValue.version);
            }
        }while(expected == null);
        return expected;
    }
}
