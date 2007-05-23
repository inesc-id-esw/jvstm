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
package jvstm.cps;

import jvstm.VBox;
import jvstm.Transaction;
import jvstm.TopLevelTransaction;

import jvstm.util.Cons;

import java.lang.reflect.Method;

import java.util.Set;
import java.util.Iterator;

public class ConsistentTopLevelTransaction extends TopLevelTransaction implements ConsistentTransaction {

    private Cons newObjects = Cons.empty();

    public ConsistentTopLevelTransaction(int number) {
        super(number);
    }

    public void registerNewObject(Object obj) {
        newObjects = newObjects.cons(obj);
    }

    public void registerNewObjects(Cons objs) {
        newObjects = objs.reverseInto(newObjects);
    }

    public Transaction makeNestedTransaction() {
	return new ConsistentNestedTransaction(this);
    }

    protected void tryCommit() {
        if (isWriteTransaction()) {
            checkConsistencyPredicates();
        }
        super.tryCommit();
    }

    protected void checkConsistencyPredicates() {
        // recheck all consistency predicates that may have changed
        Iterator<DependenceRecord> depRecIter = getDependenceRecordsToRecheck();
        while (depRecIter.hasNext()) {
            recheckDependenceRecord(depRecIter.next());
        }

        // check consistency predicates for all new objects
        for (Object obj : newObjects) {
            checkConsistencyPredicates(obj);
        }
    }

    protected void recheckDependenceRecord(DependenceRecord dependence) {
        Set<Depended> newDepended = checkOnePredicate(dependence.getDependent(), dependence.getPredicate());

        Iterator<Depended> oldDeps = dependence.getDepended();
        while (oldDeps.hasNext()) {
            Depended dep = oldDeps.next();
            if (! newDepended.remove(dep)) {
                // if we didn't find the dep in the newDepended, it's
                // because it is no longer a depended, so remove the dependence
                oldDeps.remove();
                dep.removeDependence(dependence);
            }
        }

        // the elements remaining in the set newDepended are new and
        // should be added to the dependence record
        // likewise, the dependence record should be added to those depended
        for (Depended dep : newDepended) {
            dep.addDependence(dependence);
            dependence.addDepended(dep);
        }
    }

    protected void checkConsistencyPredicates(Object obj) {
        for (Method predicate : ConsistencyPredicateSystem.getPredicatesFor(obj)) {
            Set<Depended> depended = checkOnePredicate(obj, predicate);
            if (! depended.isEmpty()) {
                DependenceRecord dependence = makeDependenceRecord(obj, predicate, depended);
                for (Depended dep : depended) {
                    dep.addDependence(dependence);
                }
            }
        }
    }

    protected Set<Depended> checkOnePredicate(Object obj, Method predicate) {
        ConsistencyCheckTransaction tx = makeConsistencyCheckTransaction(obj);
        tx.start();

        boolean finished = false;

        Class<? extends ConsistencyException> excClass = predicate.getAnnotation(ConsistencyPredicate.class).value();
        try {
            if (! ((Boolean)predicate.invoke(obj)).booleanValue()) {
                ConsistencyException exc = excClass.newInstance();
                exc.init(obj, predicate);
                throw exc;
            }
            
            Transaction.commit();

            finished = true;

            return tx.getDepended();
        } catch (Throwable t) {
            ConsistencyException exc;
            try {
                exc = excClass.newInstance();
            } catch (Throwable t2) {
                throw new Error(t2);
            }
            exc.init(obj, predicate);
            exc.initCause(t);
            throw exc;
        } finally {
            if (! finished) {
                Transaction.abort();
            }
        }
    }

    protected ConsistencyCheckTransaction makeConsistencyCheckTransaction(Object obj) {
        return new DefaultConsistencyCheckTransaction(this);
    }

    protected DependenceRecord makeDependenceRecord(Object dependent, Method predicate, Set<Depended> depended) {
        return new DefaultDependenceRecord(dependent, predicate, depended);
    }

    protected Iterator<DependenceRecord> getDependenceRecordsToRecheck() {
        Cons<Iterator<DependenceRecord>> iteratorsList = Cons.empty();

        for (VBox box : boxesWritten.keySet()) {
            Depended dep = DependedVBoxes.getDependedForBoxIfExists(box);
            if (dep != null) {
                iteratorsList = iteratorsList.cons(dep.getDependenceRecords().iterator());
            }
        }

        return new ChainedIterator<DependenceRecord>(iteratorsList.iterator());
    }
}
