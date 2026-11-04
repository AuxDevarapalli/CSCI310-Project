package com.team04.splitsmart.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class GroupPhotoModelTest {
    private final Group group = new Group("group", "Trip", "", "alice",
            Arrays.asList("alice", "bob"), 1);

    @Test
    public void photoCopiesSurviveGroupEditsAndMembershipChanges() {
        byte[] original = {1, 2, 3};
        Group photographed = group.withPhoto(original);
        original[0] = 9;
        Group updated = photographed.withDetails("Weekend", "Plan")
                .withMember("carol").withoutMember("carol").withOwner("bob");
        updated.getPhoto()[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, updated.getPhoto());
        assertNotEquals(group, photographed);
        assertNotEquals(photographed, photographed.withPhoto(new byte[]{4}));
    }

    @Test
    public void oldGroupsAndRemovedOrOversizedPhotosFallBackToInitials() {
        assertNull(group.getPhoto());
        assertNull(group.withPhoto(new byte[]{1}).withPhoto(new byte[0]).getPhoto());
        assertNull(group.withPhoto(new byte[Group.MAX_PHOTO_BYTES + 1]).getPhoto());
    }
}
