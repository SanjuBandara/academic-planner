package com.academicplanner.planning;

import com.academicplanner.planning.model.PlanningItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Allocates available study hours proportionally across {@link PlanningItem}s based on their weights.
 *
 * <h2>Formula</h2>
 * <pre>
 *   totalWeight = sum of all item weights
 *   allocatedHours = totalAvailableHours × (itemWeight / totalWeight)
 * </pre>
 *
 * <p>Supports optional caps on items (e.g. {@link PlanningItem#getAllocationCap()}),
 * automatically redistributing surplus hours to uncapped items.
 *
 * <p>Invariants guaranteed:
 * <ul>
 *   <li>Σ(allocatedHours) ≤ totalAvailableHours.</li>
 *   <li>For capped items: allocatedHours ≤ item.getAllocationCap().</li>
 *   <li>When totalAvailableHours == 0 or items is empty, all allocations are 0.</li>
 * </ul>
 */
@Component
public class TimeAllocator {

    /**
     * Allocates available study hours across the given planning items.
     * Modifies {@link PlanningItem#setAllocatedHours(double)} in-place.
     *
     * @param items               list of items with weights already computed
     * @param totalAvailableHours total hours available to schedule
     */
    public void allocate(List<PlanningItem> items, double totalAvailableHours) {
        if (items == null || items.isEmpty() || totalAvailableHours <= 0) {
            if (items != null) {
                items.forEach(i -> i.setAllocatedHours(0.0));
            }
            return;
        }

        // Reset all allocations
        items.forEach(i -> i.setAllocatedHours(0.0));

        double totalWeight = items.stream().mapToDouble(PlanningItem::getWeight).sum();
        if (totalWeight <= 0) {
            return;
        }

        List<PlanningItem> activeItems = new ArrayList<>(items);
        double remainingHours = totalAvailableHours;

        // Multi-pass redistribution to respect allocation caps
        boolean capHit = true;
        while (capHit && !activeItems.isEmpty() && remainingHours > 0.001) {
            capHit = false;
            double currentWeight = activeItems.stream().mapToDouble(PlanningItem::getWeight).sum();
            if (currentWeight <= 0) break;

            List<PlanningItem> newlyCapped = new ArrayList<>();

            for (PlanningItem item : activeItems) {
                double rawShare = remainingHours * (item.getWeight() / currentWeight);
                Double cap = item.getAllocationCap();

                if (cap != null && rawShare >= cap) {
                    item.setAllocatedHours(cap);
                    remainingHours -= cap;
                    newlyCapped.add(item);
                    capHit = true;
                }
            }

            activeItems.removeAll(newlyCapped);

            if (!capHit) {
                // Distribute remaining hours among uncapped items
                for (PlanningItem item : activeItems) {
                    double share = remainingHours * (item.getWeight() / currentWeight);
                    item.setAllocatedHours(share);
                }
                break;
            }
        }

        // Round to 15-minute (0.25h) increments, ensuring we don't exceed totalAvailable or caps
        roundAllocations(items, totalAvailableHours);
    }

    /**
     * Rounds allocated hours to 0.25h (15-min) increments while strictly respecting caps and total available hours.
     */
    private void roundAllocations(List<PlanningItem> items, double totalAvailableHours) {
        double allocatedSum = 0.0;
        for (PlanningItem item : items) {
            double raw = item.getAllocatedHours();
            double rounded = Math.round(raw * 4.0) / 4.0;
            Double cap = item.getAllocationCap();
            if (cap != null && rounded > cap) {
                rounded = Math.floor(cap * 4.0) / 4.0;
            }
            item.setAllocatedHours(Math.max(0.0, rounded));
            allocatedSum += item.getAllocatedHours();
        }

        // If slight rounding overflow occurred, adjust the largest uncapped item
        if (allocatedSum > totalAvailableHours + 0.001) {
            double excess = allocatedSum - totalAvailableHours;
            PlanningItem largest = items.stream()
                    .max((a, b) -> Double.compare(a.getAllocatedHours(), b.getAllocatedHours()))
                    .orElse(null);
            if (largest != null && largest.getAllocatedHours() >= excess) {
                double adjusted = Math.max(0.0, Math.floor((largest.getAllocatedHours() - excess) * 4.0) / 4.0);
                largest.setAllocatedHours(adjusted);
            }
        }
    }
}
