package ez.pogdog.yescom.ui.event;

import ez.pogdog.yescom.api.event.Emitter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Wrapped {@link Emitter} for Python access.
 */
public class PyEmitter<T> extends Emitter<T> {

    public final Queue<T> queuedEvents = new LinkedList<>(); // Mfw can't insert null into ArrayDeque :nathan:
    public final List<Consumer<T>> pyListeners = new ArrayList<>();

    private final Emitter<T> wrapped;

    public PyEmitter(Emitter<T> emitter) {
        super(emitter.getClazz());

        wrapped = emitter;
        wrapped.connect(object -> { // Internal callback so we get the emitted objects
            if (pyListeners.isEmpty()) return;
            synchronized (PyEmitter.this) {
                queuedEvents.add(object);
            }
        });
    }

    @Override
    public void connect(Consumer<T> listener) {
        if (!pyListeners.contains(listener)) pyListeners.add(listener);
    }

    @Override
    public void disconnect(Consumer<T> listener) {
        pyListeners.remove(listener);
    }

    @Override
    public void emit(T object) {
        wrapped.emit(object);
    }

    /**
     * Flushes the event queue to all Python listeners.
     * @return Were any elements flushed?
     */
    public synchronized boolean flush() { // FIXME: Uh, doesn't work?
        if (queuedEvents.isEmpty()) return false;

        while (!queuedEvents.isEmpty()) {
            T object = queuedEvents.remove();
            for (Consumer<T> callable : pyListeners) callable.accept(object);
        }

        return true;
    }
}
