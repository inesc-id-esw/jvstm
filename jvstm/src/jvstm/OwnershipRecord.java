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


/*
 * Each ReadWriteTransaction uses an instance of this class to represent that
 * it owns the write access to the temporary value of a given VBox in which it
 * successfully sets this instance.
 *
 * Even if (for a while) other transactions do not see any updates to the
 * default initial value (zero), they will behave as if the current owner is
 * still running, which is a safe behavior.  Eventually, the owner will (only
 * once) set either -1 or a value greater than zero.  When other transactions
 * see this value they will act accordingly.  We leverage on the fact the in
 * the Java Memory Model, values do not appear "out of the blue", i.e., a
 * reader will either see the default value or a value that was actually set.
 */
public class OwnershipRecord {
    public static final int ABORTED = -1;
    public static final int RUNNING = 0;
    
    protected static final OwnershipRecord DEFAULT_COMMITTED_OWNER = new OwnershipRecord() { { version = 1;} };

    // version = 0 is reserved for the state RUNNING
    // version = -1 is reserved for the state ABORTED
    // version > 0  is the version in which the owning transaction committed
    public int version = RUNNING;
}
