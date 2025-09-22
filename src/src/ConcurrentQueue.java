import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentQueue<T> {

    private static class Node<T> {
        private T atom;
        private final AtomicReference<Node<T>> next; // use atomic to make thread safe

        Node(T atom) {
            this.atom = atom;
            this.next = new AtomicReference<>(null);
        }
    }

    AtomicReference<Node<T>> head;
    AtomicReference<Node<T>> tail;
    ReentrantLock lock = new ReentrantLock();

    public ConcurrentQueue() {
        head = new AtomicReference<>(null);
        tail = new AtomicReference<>(null);
    }


    // Queue
    public void offer(T atom) {
        lock.lock();
        try {
            Node<T> newNode = new Node<>(atom);
            Node<T> currTail = tail.get();

            if (currTail == null) {
                head.set(newNode);
                tail.set(newNode);
                return;
            }
            currTail.next.set(newNode);
            tail.compareAndSet(currTail, newNode);
        } finally {
            lock.unlock();
        }
    }

    // Pop
    public T poll() {
        lock.lock();
        try {
            Node<T> currHead = head.get();
            if (currHead == null) {
                return null;
            }

            T atom = currHead.atom;
            Node<T> newHead = currHead.next.get();

            if (newHead == null) {
                head.set(null);
                tail.set(null);
            } else {
                head.compareAndSet(currHead, newHead);
            }

            return atom;

        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return head.get() == null;
        } finally {
            lock.unlock();
        }
    }
}
