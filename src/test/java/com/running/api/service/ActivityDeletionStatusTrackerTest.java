package com.running.api.service;

import com.running.api.dto.ActivityDeletionStatusDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityDeletionStatusTrackerTest {

    private final ActivityDeletionStatusTracker tracker = new ActivityDeletionStatusTracker();

    @Test
    void createsAPendingDeletion() {
        ActivityDeletionStatusDto status = tracker.create(1L);

        assertThat(status.getDeleteId()).isNotBlank();
        assertThat(status.getStatus()).isEqualTo("PENDING");
        assertThat(status.getTotalActivities()).isZero();
        assertThat(status.getDeletedActivities()).isZero();
        assertThat(status.getProgressPercent()).isZero();
        assertThat(tracker.getStatus(status.getDeleteId())).isNotNull();
    }

    @Test
    void followsTheProgressUntilCompletion() {
        String deleteId = tracker.create(1L).getDeleteId();

        tracker.markProcessing(deleteId, 4);
        assertThat(tracker.getStatus(deleteId).getStatus()).isEqualTo("PROCESSING");
        assertThat(tracker.getStatus(deleteId).getTotalActivities()).isEqualTo(4);

        tracker.updateProgress(deleteId, 2);
        assertThat(tracker.getStatus(deleteId).getProgressPercent()).isEqualTo(50.0);

        tracker.markCompleted(deleteId, 4);
        ActivityDeletionStatusDto status = tracker.getStatus(deleteId);
        assertThat(status.getStatus()).isEqualTo("COMPLETED");
        assertThat(status.getDeletedActivities()).isEqualTo(4);
        assertThat(status.getProgressPercent()).isEqualTo(100.0);
        assertThat(status.getMessage()).contains("vaciadas");
    }

    @Test
    void marksTheDeletionAsFailedWithTheError() {
        String deleteId = tracker.create(1L).getDeleteId();

        tracker.markFailed(deleteId, "boom");

        ActivityDeletionStatusDto status = tracker.getStatus(deleteId);
        assertThat(status.getStatus()).isEqualTo("FAILED");
        assertThat(status.getError()).isEqualTo("boom");
    }

    @Test
    void keepsOnlyTheLatestDeletionOfEachUser() {
        String first = tracker.create(1L).getDeleteId();
        String second = tracker.create(1L).getDeleteId();

        assertThat(tracker.getStatus(first)).isNull();
        assertThat(tracker.getStatus(second)).isNotNull();
    }

    @Test
    void keepsTheDeletionOfOtherUsers() {
        String otherUser = tracker.create(2L).getDeleteId();

        tracker.create(1L);

        assertThat(tracker.getStatus(otherUser)).isNotNull();
    }

    @Test
    void reportsZeroProgressWhenThereIsNothingToDelete() {
        String deleteId = tracker.create(1L).getDeleteId();

        tracker.markProcessing(deleteId, 0);
        tracker.markCompleted(deleteId, 0);

        assertThat(tracker.getStatus(deleteId).getProgressPercent()).isZero();
    }

    @Test
    void ignoresUpdatesForUnknownDeletions() {
        tracker.markProcessing("unknown", 3);
        tracker.updateProgress("unknown", 1);
        tracker.markCompleted("unknown", 3);
        tracker.markFailed("unknown", "boom");

        assertThat(tracker.getStatus("unknown")).isNull();
    }
}
