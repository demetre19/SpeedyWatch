package com.speedywatch.app;

final class OmniButtonGesture {
    enum Direction {
        NONE("none", "None"),
        UP("up", "Up"),
        UP_RIGHT("up_right", "Up-right"),
        RIGHT("right", "Right"),
        DOWN_RIGHT("down_right", "Down-right"),
        DOWN("down", "Down"),
        DOWN_LEFT("down_left", "Down-left"),
        LEFT("left", "Left"),
        UP_LEFT("up_left", "Up-left");

        final String id;
        final String label;

        Direction(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static Direction[] configurableValues() {
            return new Direction[]{
                    UP,
                    UP_RIGHT,
                    RIGHT,
                    DOWN_RIGHT,
                    DOWN,
                    DOWN_LEFT,
                    LEFT,
                    UP_LEFT
            };
        }

    }

    private OmniButtonGesture() {
    }

    static Direction direction(float deltaX, float deltaY, float minimumDistance) {
        if (!Float.isFinite(deltaX)
                || !Float.isFinite(deltaY)
                || !Float.isFinite(minimumDistance)
                || minimumDistance <= 0f) {
            return Direction.NONE;
        }
        float horizontal = Math.abs(deltaX);
        float vertical = Math.abs(deltaY);
        if (Math.max(horizontal, vertical) < minimumDistance) {
            return Direction.NONE;
        }

        double angle = Math.toDegrees(Math.atan2(deltaY, deltaX));
        if (angle >= -22.5 && angle < 22.5) {
            return Direction.RIGHT;
        }
        if (angle >= 22.5 && angle < 67.5) {
            return Direction.DOWN_RIGHT;
        }
        if (angle >= 67.5 && angle < 112.5) {
            return Direction.DOWN;
        }
        if (angle >= 112.5 && angle < 157.5) {
            return Direction.DOWN_LEFT;
        }
        if (angle >= 157.5 || angle < -157.5) {
            return Direction.LEFT;
        }
        if (angle >= -157.5 && angle < -112.5) {
            return Direction.UP_LEFT;
        }
        if (angle >= -112.5 && angle < -67.5) {
            return Direction.UP;
        }
        return Direction.UP_RIGHT;
    }

    static int nextTapCount(int currentCount, long previousTapAt, long tapAt, long maximumGap) {
        if (tapAt < 0L || maximumGap <= 0L) {
            return 1;
        }
        if (currentCount <= 0
                || previousTapAt < 0L
                || tapAt < previousTapAt
                || tapAt - previousTapAt > maximumGap) {
            return 1;
        }
        return Math.min(3, currentCount + 1);
    }
}
