/**
 * =====================================================================
 *  DAY 4  -  HEAPSORT
 * =====================================================================
 *
 *  THE IDEA
 *  A MAX-HEAP is a list viewed as a binary tree where every parent is >= its
 *  children, so the biggest item is always at the top (index 0).
 *
 *  With 0-based indexes, for the item at index k:
 *      left child  = 2*k + 1
 *      right child = 2*k + 2
 *      parent      = (k - 1) / 2
 *
 *  SINK (also called "sift down"):  sink(arr, k, size)
 *      While k has at least one child inside the heap (child index < size):
 *        - pick the BIGGER of its children
 *        - if a[k] is already >= that child, stop
 *        - otherwise swap them, and keep sinking from the child's spot
 *
 *  HEAPSORT:
 *   Phase 1 - build the heap: call sink on every parent, starting from the
 *             last parent (index n/2 - 1) and going back to index 0.
 *   Phase 2 - sort: repeatedly swap the top (the max) with the last item in the
 *             heap, shrink the heap by one, and sink the new top.
 *
 *  TOOLS: arr.size(), arr.less(i, j), arr.swap(i, j)
 *
 *  ANALYSIS QUESTIONS
 *   1. Watch phase 1 and phase 2 in the visualizer. Can you see the moment the heap is finished?
 *   2. Compare heapsort and merge sort comparisons for n = 64. Which does more?
 *   3. Heapsort needs no temp array, merge sort does. Why might that matter?
 *   4. Is heapsort stable?
 */
public class HeapSort implements SortAlgorithm {

    @Override
    public String getName() { return "Heapsort"; }


    @Override
    public void sort(SortArray arr) {
        // TODO: write heapsort here (add a sink helper method below)
        int n = arr.size();
        for (int k = n / 2 - 1; k >= 0; k--) {
            sink(arr, k, n);
        }
        for (int end = n - 1; end > 0; end--) {
            arr.swap(0, end);
            sink(arr, 0, end);
        }
    }

    private void sink(SortArray arr, int k, int size) {
        while (2 * k + 1 < size) {
            int child = 2 * k + 1;
            if (child + 1 < size && arr.less(child, child + 1)) {
                child++;
            }
            if (!arr.less(k, child)) {
                break;
            }
            arr.swap(k, child);
            k = child;
        }
    }
}
