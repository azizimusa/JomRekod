package g.jom.rejod;

import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {
    private static final String TAG = "EventManager";

    // --- Event Definitions ---
    public interface Event {}

    public static class StopRecordingEvent implements Event {
        public StopRecordingEvent() {

        }
    }
    public static class StartRecordingEvent implements Event {
        public StartRecordingEvent() {}
    }

    // --- Listener Definition ---
    public interface Listener {
        void onEvent(Event event);
    }

    // --- Singleton Implementation ---
    private static final EventManager instance = new EventManager();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private EventManager() {}

    public static EventManager getInstance() {
        return instance;
    }

    public void register(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(Listener listener) {
        listeners.remove(listener);
    }

    public void post(Event event) {
        if (event == null) {
            Log.w(TAG, "Posting a null event is not allowed.");
            return;
        }
        AppExecutors.getInstance().mainThread().execute(() -> {
            for (Listener listener : listeners) {
                listener.onEvent(event);
            }
        });
    }
}
