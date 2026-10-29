package com.team04.splitsmart.model;

import java.util.Objects;

/**
 * The part of an expense one participant is responsible for. Amounts are integer cents
 * (NFR 2). For percentage splits the entered percentage is kept as basis points
 * (percent x 100, so 33.33% is 3333) so the original input can be shown again when editing.
 */
public class Share {
    private final String participantId;
    private final long amountCents;
    private final int percentBasisPoints;

    public Share(String participantId, long amountCents) {
        this(participantId, amountCents, 0);
    }

    public Share(String participantId, long amountCents, int percentBasisPoints) {
        this.participantId = participantId;
        this.amountCents = amountCents;
        this.percentBasisPoints = percentBasisPoints;
    }

    public String getParticipantId() {
        return participantId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    /** Entered percentage x 100; only meaningful for {@link SplitMethod#PERCENTAGE} expenses. */
    public int getPercentBasisPoints() {
        return percentBasisPoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Share)) return false;
        Share other = (Share) o;
        return amountCents == other.amountCents
                && percentBasisPoints == other.percentBasisPoints
                && Objects.equals(participantId, other.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participantId, amountCents, percentBasisPoints);
    }

    @Override
    public String toString() {
        return participantId + "=" + amountCents;
    }
}
