package com.team04.splitsmart.logic.split;

import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Share;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Divides an expense total into per-participant shares (FR 3.1 to FR 3.5). All math is done in
 * integer cents. The iteration order of the participants defines "first to last" for the
 * rounding rule, so callers pass ordered collections (a {@link List} or {@link LinkedHashMap}).
 */
public class SplitCalculator {

    /**
     * Equal split (FR 3.2). Each share is the total divided by the number of participants,
     * rounded down to the cent; leftover cents go one at a time to the first participants
     * (FR 3.5). $10.01 split three ways is $3.34, $3.34 and $3.33.
     */
    public List<Share> splitEqual(long totalCents, List<String> participantIds) {
        List<Share> shares = new ArrayList<>();
        if (participantIds == null || participantIds.isEmpty()) {
            return shares;
        }
        requireNonNegative(totalCents);
        int count = participantIds.size();
        long base = totalCents / count;
        long leftover = totalCents % count;
        for (int i = 0; i < count; i++) {
            long amount = base + (i < leftover ? 1 : 0);
            shares.add(new Share(participantIds.get(i), amount));
        }
        return shares;
    }

    /**
     * Checks percentage inputs (FR 3.4): every value must be 0% or more and together they must
     * be exactly 100%.
     */
    public ValidationResult checkPercentages(Map<String, Integer> percentBasisPoints) {
        if (percentBasisPoints == null || percentBasisPoints.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                    "Enter a percentage for every participant");
        }
        int total = 0;
        for (Integer basisPoints : percentBasisPoints.values()) {
            if (basisPoints == null) {
                return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                        "Enter a percentage for every participant");
            }
            if (basisPoints < 0) {
                return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                        "Percentages cannot be negative");
            }
            total += basisPoints;
        }
        if (total != Money.FULL_PERCENT_BASIS_POINTS) {
            return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                    "Percentages add up to " + Money.formatPercent(total) + ", not 100%");
        }
        return ValidationResult.ok();
    }

    /**
     * Percentage split (FR 3.4). Each share is total x percent rounded down to the cent. When
     * the percentages add up to exactly 100%, leftover cents are handed out one at a time from
     * the first to the last participant (FR 3.5), skipping anyone assigned 0% so they never
     * pay for a rounding cent. $10.01 split 50/50 is $5.01 and $5.00.
     */
    public List<Share> splitByPercent(long totalCents,
                                      LinkedHashMap<String, Integer> percentBasisPoints) {
        List<Share> shares = new ArrayList<>();
        if (percentBasisPoints == null || percentBasisPoints.isEmpty()) {
            return shares;
        }
        requireNonNegative(totalCents);
        List<String> ids = new ArrayList<>(percentBasisPoints.keySet());
        long[] amounts = new long[ids.size()];
        int[] basisPoints = new int[ids.size()];
        long allocated = 0;
        int totalBasisPoints = 0;
        for (int i = 0; i < ids.size(); i++) {
            Integer value = percentBasisPoints.get(ids.get(i));
            basisPoints[i] = value == null ? 0 : Math.max(0, value);
            totalBasisPoints += basisPoints[i];
            amounts[i] = totalCents * basisPoints[i] / Money.FULL_PERCENT_BASIS_POINTS;
            allocated += amounts[i];
        }
        long leftover = totalCents - allocated;
        if (totalBasisPoints == Money.FULL_PERCENT_BASIS_POINTS && leftover > 0) {
            while (leftover > 0) {
                for (int i = 0; i < ids.size() && leftover > 0; i++) {
                    if (basisPoints[i] > 0) {
                        amounts[i]++;
                        leftover--;
                    }
                }
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            shares.add(new Share(ids.get(i), amounts[i], basisPoints[i]));
        }
        return shares;
    }

    /**
     * Exact-amount split (FR 3.3): the creator typed each participant's amount, so the shares
     * are exactly those amounts. {@link #checkSum} verifies they add up to the total.
     */
    public List<Share> splitExact(LinkedHashMap<String, Long> amounts) {
        List<Share> shares = new ArrayList<>();
        if (amounts == null) {
            return shares;
        }
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            long amount = entry.getValue() == null ? 0 : entry.getValue();
            shares.add(new Share(entry.getKey(), amount));
        }
        return shares;
    }

    /**
     * FR 3.6: the expense can only be saved when the shares add up exactly to the total. The
     * error message shows the difference, e.g. "Amounts add up to $28.00, not $30.00".
     */
    public ValidationResult checkSum(long totalCents, List<Share> shares) {
        long sum = sum(shares);
        if (sum != totalCents) {
            return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                    "Amounts add up to " + Money.format(sum) + ", not " + Money.format(totalCents));
        }
        return ValidationResult.ok();
    }

    /** Sum of all share amounts in cents. */
    public static long sum(List<Share> shares) {
        long sum = 0;
        if (shares != null) {
            for (Share share : shares) {
                sum += share.getAmountCents();
            }
        }
        return sum;
    }

    private static void requireNonNegative(long totalCents) {
        if (totalCents < 0) {
            throw new IllegalArgumentException("Total cannot be negative: " + totalCents);
        }
    }
}
