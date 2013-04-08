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


/**
 * This class replaces the get() and put() methods of the VBox and it provides
 * the barriers implementation to be used in the Deuce integration of the JVSTM.
 * This integration follows the AOM approach (Adaptive Object Metadata) and
 * according to this model the T type parameter passed in all barriers represents
 * the object itself, once the AOM follows an object-level conflict detection
 * granularity.
 *
 * Another difference from the AomBarriers to the put/get barriers of the VBox is
 * in the additional parameter Transaction. Instead of getting the current transaction
 * from the ThreadLocal context, the invoker (i.e. the Deuce's ContextDelegator) is
 * responsible for passing the current transaction to the AomBarriers methods.
 * The jvstm.Context object that is present in all transactional scopes keeps track
 * of the current transaction object.
 */
public class AomBarriers {
    /**
     * The correct constraint for T should be: T extends E & VBoxAom<E>.
     * Yet, Java generics does not allow the previous constraint.
     * In the Deuce we will not invoke this method and we call directly
     * tx.getBoxValue(ref) because it is not possible to access an
     * StmBarrier out of a transactional scope.
     */
    public static <T extends VBoxAom<T>> T get(Transaction tx, T ref) {
        if (tx == null) {
            VBoxBody<T> vbody = ref.body;
            if(vbody == null){
                return ref;
            }
            // Access the box body without creating a full transaction, while
            // still preserving ordering guarantees by 'piggybacking' on the
            // version from the latest commited transaction.
            // If the box body is GC'd before we can reach it, the process
            // re-starts with a newer transaction.
            while (true) {
                int transactionNumber = Transaction.mostRecentCommittedRecord.transactionNumber;
                do {
                    if (vbody.version <= transactionNumber) {
                        return vbody.value;
                    }
                    vbody = vbody.next;
                } while (vbody!= null);
            }
        } else {
            return tx.getBoxValue(ref);
        }
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, int newValue, long fieldOffset) {
        if(trx != null)
            UtilUnsafe.UNSAFE.putInt(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, long newValue, long fieldOffset) {
        if(trx != null)
            UtilUnsafe.UNSAFE.putLong(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, boolean newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putBoolean(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, char newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putChar(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, byte newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putByte(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, short newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putShort(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, float newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putFloat(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, double newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putDouble(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> void put(ReadWriteTransaction trx, T ref, Object newValue, long fieldOffset){
        if(trx != null)
            UtilUnsafe.UNSAFE.putObject(getTarget(trx, ref), fieldOffset, newValue);
        else
            putInInevitableTrx(ref, newValue, fieldOffset);
    }

    public static <T extends VBoxAom<T>> T getTarget(ReadWriteTransaction trx, T ref){
        /*
         * Check if the transaction trx is the current writer and owner of the
         * ref object.
         */
        InplaceWrite<T> inplaceWrite = ref.inplace;
        OwnershipRecord currentOwner = inplaceWrite.orec;
        if (currentOwner.owner == trx) { // we are already the current writer
            return inplaceWrite.tempValue;
        }
        /*
         * We will check the standard write-set for an object that we could
         * have written in a previous invocation to the rwTrx.setBoxValue().
         */
        T value = (T) trx.boxesWritten.get(ref);
        if(value == null){
            value = trx.readFromBody(ref).replicate(); // update the read-set
            trx.setBoxValue(ref, value);
        }
        /*
         * !!!!! Instead of trying to acquire the ownership over the ref object on every
         * put operation to the same object, as happens in the standard JVSTM, we
         * will just try it on the first write to an object.
         * !!!!! On the next put invocations we will write into the replica stored in the
         * standard write-set. We could take a different approach and invoke again the
         * trx.setBoxValue, but this option has overheads when it fails to acquire the
         * ownership and it will insert again the same replica into the standard write-set.
         */
        return value;
    }

    /*===========================================================================*
     *~~~~~~~~~~~~~   BARRIERS for INEVITABLE transactions   ~~~~~~~~~~~~~~~~~~~~*
     * !!!! Regarding the use of AOM in Deuce, maybe we do not require
     * InevitableTransactions when the barriers are accessed out of a transactional
     * scope, because in Deuce we cannot invoke an STM barrier without a transaction.
     * So in this case maybe we could update memory in place and without taking
     * precautions to preserve consistency.
     *===========================================================================*/

    /**
     * This method has some redundant tasks with the setBoxValue of the InevitableTransaction.
     * Yet, this is the most simple way of preserving the original InevitableTransaction and
     * without requiring any subclass specialization.
     */
    private static <T extends VBoxAom<T>> T getTargetForInnevitable(T ref, int txNumber){
        VBoxBody<T> vbody = ref.body;
        if ((vbody != null) && (vbody.version == txNumber)) {
            // In this case we already have written to this VBox during
            // current Inevitable transaction (strange in this scenario -
            // maybe possible in others).
            // So we already have replicated the last value.
            return vbody.value;
        } else {
            VBoxBody<T> body = ref.body;

            if (body!= null && body.version > txNumber) {
                TransactionSignaller.SIGNALLER.signalCommitFail();
                throw new AssertionError("Impossible condition - Commit fail signalled!");
            }
            if(body == null)
                return ref.replicate();
            else
                return body.value.replicate();
        }
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, int newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putInt(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, long newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putLong(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, boolean newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putBoolean(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }


    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, byte newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putByte(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, short newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putShort(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, char newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putChar(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }
    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, float newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putFloat(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, double newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putDouble(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

    private static <T extends VBoxAom<T>> void putInInevitableTrx(T ref, Object newValue, long fieldOffset){
        Transaction tx = Transaction.beginInevitable();
        T newT = getTargetForInnevitable(ref, tx.number);
        UtilUnsafe.UNSAFE.putObject(newT, fieldOffset, newValue);
        tx.setBoxValue(ref, newT);
        tx.commit();
    }

}
