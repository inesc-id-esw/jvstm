package jvstm;

/**
 * Used at commit time to stop a helper transaction that is going through the
 * PerTxBoxes. The reason for this is that the helper detects that some other
 * helper already finished that commit part and freed the Orecs. Consequently it
 * would no longer be safe to continue processing the PerTxBoxes as some writes
 * of the committing transaction would no longer be available (as the inplace
 * slot was released).
 * 
 * @author nmld
 * 
 */
public class StopPerTxBoxesCommitException extends Error {

    private static final long serialVersionUID = -7466777258797205659L;

}
