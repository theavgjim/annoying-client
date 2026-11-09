package com.annoyingclient.util;

/** Counts clicks in the last second using fixed ring buffers (no allocation per click). */
public final class CpsTracker {

    private static final int CAPACITY = 64;

    private final long[] left = new long[CAPACITY];
    private final long[] right = new long[CAPACITY];
    private int leftHead;
    private int rightHead;

    public void onClick(boolean leftButton) {
        long now = System.currentTimeMillis();
        if (leftButton) {
            left[leftHead] = now;
            leftHead = (leftHead + 1) % CAPACITY;
        } else {
            right[rightHead] = now;
            rightHead = (rightHead + 1) % CAPACITY;
        }
    }

    public int getLeft() {
        return count(left);
    }

    public int getRight() {
        return count(right);
    }

    private static int count(long[] buffer) {
        long cutoff = System.currentTimeMillis() - 1000;
        int c = 0;
        for (long t : buffer) {
            if (t > cutoff) {
                c++;
            }
        }
        return c;
    }
}
