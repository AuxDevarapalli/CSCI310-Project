package com.team04.splitsmart.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A set of registered users who share expenses (FR 1.4). The creator is the owner and the only
 * user who may edit or delete the group or add members (FR 1.5, FR 1.7).
 */
public class Group {
    public static final int MAX_PHOTO_BYTES = 32 * 1024;
    private final String id;
    private final String name;
    private final String description;
    private final String ownerId;
    private final List<String> memberIds;
    private final long createdAt;
    private final byte[] photo;

    public Group(String id, String name, String description, String ownerId,
                 List<String> memberIds, long createdAt) {
        this(id, name, description, ownerId, memberIds, createdAt, null);
    }

    public Group(String id, String name, String description, String ownerId,
                 List<String> memberIds, long createdAt, byte[] photo) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.ownerId = ownerId;
        this.memberIds = memberIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(memberIds));
        this.createdAt = createdAt;
        this.photo = photo == null || photo.length == 0 || photo.length > MAX_PHOTO_BYTES
                ? null : photo.clone();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasDescription() {
        return !description.trim().isEmpty();
    }

    public String getOwnerId() {
        return ownerId;
    }

    /** Member ids in the order they joined; the owner is always first. */
    public List<String> getMemberIds() {
        return memberIds;
    }

    public int getMemberCount() {
        return memberIds.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public byte[] getPhoto() {
        return photo == null ? null : photo.clone();
    }

    public Group withPhoto(byte[] photo) {
        return new Group(id, name, description, ownerId, memberIds, createdAt, photo);
    }

    public boolean isMember(String uid) {
        return uid != null && memberIds.contains(uid);
    }

    public boolean isOwner(String uid) {
        return uid != null && uid.equals(ownerId);
    }

    /** Returns a copy with an updated name and description (FR 1.7). */
    public Group withDetails(String newName, String newDescription) {
        return new Group(id, newName, newDescription, ownerId, memberIds, createdAt, photo);
    }

    /** Returns a copy with one more member appended (FR 1.5). */
    public Group withMember(String uid) {
        if (isMember(uid)) {
            return this;
        }
        List<String> ids = new ArrayList<>(memberIds);
        ids.add(uid);
        return new Group(id, name, description, ownerId, ids, createdAt, photo);
    }

    /** Returns a copy without {@code uid} (a member was removed or left). */
    public Group withoutMember(String uid) {
        List<String> ids = new ArrayList<>(memberIds);
        ids.remove(uid);
        return new Group(id, name, description, ownerId, ids, createdAt, photo);
    }

    /** Returns a copy owned by {@code newOwnerId}, who must already be a member. */
    public Group withOwner(String newOwnerId) {
        return new Group(id, name, description, newOwnerId, memberIds, createdAt, photo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Group)) return false;
        Group other = (Group) o;
        return createdAt == other.createdAt
                && Objects.equals(id, other.id)
                && name.equals(other.name)
                && description.equals(other.description)
                && Objects.equals(ownerId, other.ownerId)
                && memberIds.equals(other.memberIds)
                && Arrays.equals(photo, other.photo);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(id, name, description, ownerId, memberIds, createdAt)
                + Arrays.hashCode(photo);
    }
}
