package dev.gtmatterpacker;

/** Complete a whole phase before any block in the next phase can observe the region. */
final class RegionTransfer {

    interface Step {

        void run(int index) throws Exception;
    }

    private RegionTransfer() {}

    static void remove(int count, Step detach, Step clear) throws Exception {
        for (int i = 0; i < count; i++) detach.run(i);
        for (int i = count - 1; i >= 0; i--) clear.run(i);
    }

    static void restore(int count, Step blocks, Step tiles, Step connections) throws Exception {
        for (int i = 0; i < count; i++) blocks.run(i);
        for (int i = 0; i < count; i++) tiles.run(i);
        for (int i = 0; i < count; i++) connections.run(i);
    }
}
