package com.speedywatch.app;

final class WatchPathPlayback {
    static final class Action {
        static final Action NONE = new Action(Double.NaN, false);
        static final Action COMPLETE = new Action(Double.NaN, true);

        final double seekSeconds;
        final boolean completed;

        private Action(double seekSeconds, boolean completed) {
            this.seekSeconds = seekSeconds;
            this.completed = completed;
        }

        static Action seek(double seconds) {
            return new Action(seconds, false);
        }

        boolean shouldSeek() {
            return Double.isFinite(seekSeconds);
        }
    }

    private final WatchPathPlan plan;
    private int segmentIndex;
    private boolean active = true;
    private boolean reviewingSkippedSection;
    private int resumeSegmentIndex = -1;
    private double undoSeconds = Double.NaN;

    WatchPathPlayback(WatchPathPlan plan) {
        if (plan == null || plan.segments.isEmpty()) {
            throw new IllegalArgumentException("WatchPath plan is empty");
        }
        this.plan = plan;
    }

    Action start() {
        return Action.seek(plan.segments.get(0).startSeconds);
    }

    Action onPlaybackTime(double seconds) {
        if (!active || !Double.isFinite(seconds)) {
            return Action.NONE;
        }
        if (reviewingSkippedSection) {
            WatchPathPlan.Segment resume = plan.segments.get(resumeSegmentIndex);
            if (seconds >= resume.startSeconds) {
                reviewingSkippedSection = false;
                segmentIndex = resumeSegmentIndex;
                resumeSegmentIndex = -1;
            }
            return Action.NONE;
        }

        WatchPathPlan.Segment current = plan.segments.get(segmentIndex);
        if (seconds < current.endSeconds) {
            return Action.NONE;
        }
        if (segmentIndex >= plan.segments.size() - 1) {
            active = false;
            return Action.COMPLETE;
        }

        undoSeconds = current.endSeconds;
        segmentIndex++;
        return Action.seek(plan.segments.get(segmentIndex).startSeconds);
    }

    Action previous() {
        if (!active) {
            return Action.NONE;
        }
        if (reviewingSkippedSection) {
            reviewingSkippedSection = false;
            segmentIndex = Math.max(0, resumeSegmentIndex - 1);
            resumeSegmentIndex = -1;
        } else {
            segmentIndex = Math.max(0, segmentIndex - 1);
        }
        undoSeconds = Double.NaN;
        return Action.seek(plan.segments.get(segmentIndex).startSeconds);
    }

    Action next() {
        if (!active) {
            return Action.NONE;
        }
        if (reviewingSkippedSection) {
            segmentIndex = resumeSegmentIndex;
            reviewingSkippedSection = false;
            resumeSegmentIndex = -1;
            undoSeconds = Double.NaN;
            return Action.seek(plan.segments.get(segmentIndex).startSeconds);
        }
        if (segmentIndex >= plan.segments.size() - 1) {
            return Action.NONE;
        }
        WatchPathPlan.Segment current = plan.segments.get(segmentIndex);
        undoSeconds = current.endSeconds;
        segmentIndex++;
        return Action.seek(plan.segments.get(segmentIndex).startSeconds);
    }

    Action undo() {
        if (!active
                || reviewingSkippedSection
                || !Double.isFinite(undoSeconds)
                || segmentIndex <= 0) {
            return Action.NONE;
        }
        reviewingSkippedSection = true;
        resumeSegmentIndex = segmentIndex;
        double target = undoSeconds;
        undoSeconds = Double.NaN;
        return Action.seek(target);
    }

    void stop() {
        active = false;
    }

    boolean isActive() {
        return active;
    }

    boolean canUndo() {
        return active && !reviewingSkippedSection && Double.isFinite(undoSeconds);
    }

    boolean canGoNext() {
        return active && (reviewingSkippedSection || segmentIndex < plan.segments.size() - 1);
    }

    boolean isReviewingSkippedSection() {
        return reviewingSkippedSection;
    }

    int segmentIndex() {
        return segmentIndex;
    }

    int segmentCount() {
        return plan.segments.size();
    }

    String currentTitle() {
        int index = reviewingSkippedSection ? resumeSegmentIndex : segmentIndex;
        return plan.segments.get(index).title;
    }

    String sourceUrl() {
        return plan.sourceUrl;
    }
}
