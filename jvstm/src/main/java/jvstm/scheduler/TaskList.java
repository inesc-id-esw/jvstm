package jvstm.scheduler;

public class TaskList {

    public class Node {
	public ScheduledTask task;
	public Node prev;
	public Node next;

	public Node(ScheduledTask task, Node prev, Node next) {
	    this.task = task;
	    this.prev = prev;
	    this.next = next;
	}

    }

    public TaskList() {

    }

    public Node head;

    public void add(ScheduledTask task) {
	if (head == null) {
	    head = new Node(task, null, null);
	} else {
	    Node newNode = new Node(task, null, head);
	    head.prev = newNode;
	    head = newNode;
	}
    }

    public void deleteNode(Node node) {
	if (node.prev == null) {
	    head = node.next;
	    if (head != null) {
		head.prev = null;
	    }
	    return;
	}

	node.prev.next = node.next;
	if (node.next != null) {
	    node.next.prev = node.prev;
	}

	return;
    }
}
