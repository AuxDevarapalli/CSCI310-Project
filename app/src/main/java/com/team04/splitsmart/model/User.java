package com.team04.splitsmart.model;

import java.util.Objects;

/**
 * A registered SplitSmart user (FR 1.1). The USC email and USC ID are fixed at registration;
 * only the name can change (FR 1.3), which produces a new immutable instance.
 */
public class User {
    private final String uid;
    private final String name;
    private final String uscEmail;
    private final String uscId;

    public User(String uid, String name, String uscEmail, String uscId) {
        this.uid = uid;
        this.name = name == null ? "" : name;
        this.uscEmail = uscEmail == null ? "" : uscEmail;
        this.uscId = uscId == null ? "" : uscId;
    }

    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getUscEmail() {
        return uscEmail;
    }

    public String getUscId() {
        return uscId;
    }

    /** Returns a copy of this user with a new name (FR 1.3). */
    public User withName(String newName) {
        return new User(uid, newName, uscEmail, uscId);
    }

    /** Up to two upper-case initials used for avatar circles, e.g. "Aux Devarapalli" -> "AD". */
    public String getInitials() {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return uscEmail.isEmpty() ? "?" : uscEmail.substring(0, 1).toUpperCase();
        }
        String[] parts = trimmed.split("\\s+");
        StringBuilder initials = new StringBuilder();
        initials.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) {
            initials.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return initials.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User other = (User) o;
        return Objects.equals(uid, other.uid)
                && name.equals(other.name)
                && uscEmail.equals(other.uscEmail)
                && uscId.equals(other.uscId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, name, uscEmail, uscId);
    }

    @Override
    public String toString() {
        return name + " <" + uscEmail + ">";
    }
}
