import java.net.SocketAddress;
import java.util.*;

public class MapCopies {
    private final HashMap<SocketAddress, Long> copies;

    MapCopies() {
        copies = new HashMap<>();
    }

    public void add(SocketAddress address, long time) {
        if (copies.containsKey(address)) {
            copies.put(address, time);
            return;
        }
        copies.put(address, time);
        printMap();
    }

    public void delete(int interval) {
        copies.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue() + 3L * interval);
    }

    public void printMap() {
        copies.forEach((key, value) -> {System.out.println( key + "  " + value);});
        System.out.println();
    }
}
