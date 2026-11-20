public class BottomUpMergeSort implements SortAlgorithm {

    @Override
    public String getName() {
        return "Bottom-Up Merge Sort";
    }

    @Override
    public void sort(SortArray arr) {
        int n = arr.size();
        Item[] temp = new Item[n];

        for (int size = 1; size < n; size *= 2) {
            for (int lo = 0; lo < n - size; lo += 2 * size) {
                int mid = lo + size - 1;
                int hi = Math.min(lo + 2 * size - 1, n - 1);
                merge(arr, temp, lo, mid, hi);
            }
        }
    }

    private void merge(SortArray arr, Item[] temp, int lo, int mid, int hi) {
        for (int k = lo; k <= hi; k++) {
            temp[k] = arr.get(k);
        }

        int i = lo;
        int j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) {
                arr.set(k, temp[j++]);
            } else if (j > hi) {
                arr.set(k, temp[i++]);
            } else if (arr.less(temp[j], temp[i])) {
                arr.set(k, temp[j++]);
            } else {
                arr.set(k, temp[i++]);
            }
        }
        arr.mark("merge " + lo + "-" + hi);
    }
}
