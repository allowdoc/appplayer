package android.util;

import java.io.IOException;
import java.util.Collection;

public class EventLog {
    public static class Event {
        private final Object mData;
        public Event(byte[] data) { mData = data; }
        public Object getData() { return mData; }
    }

    public static int writeEvent(int tag, int value) { return 0; }
    public static int writeEvent(int tag, long value) { return 0; }
    public static int writeEvent(int tag, float value) { return 0; }
    public static int writeEvent(int tag, String str) { return 0; }
    public static int writeEvent(int tag, Object... list) { return 0; }

    public static void readEvents(int[] tags, Collection<Event> output) throws IOException {}
    public static void readEventsOnWrapping(int[] tags, long timestamp, Collection<Event> output) throws IOException {}

    public static String getTagName(int tag) { return ""; }
    public static int getTagCode(String name) { return 0; }
}
