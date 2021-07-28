package com.ysq.persistencebtree.mvstore;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A cursor to iterate over elements in ascending or descending order.
 *
 */
public final class Cursor implements Iterator<String> {
    private final boolean reverse;
    private final String to;
    private CursorPos cursorPos;
    private CursorPos keeper;
    private String current;
    private String last;
    private String lastValue;
    private Page lastPage;


    public Cursor(RootReference rootReference, String from, String to) {
        this(rootReference, from, to, false);
    }

    /**
     * @param rootReference of the tree
     * @param from starting key (inclusive), if null start from the first / last key
     * @param to ending key (inclusive), if null there is no boundary
     * @param reverse true if tree should be iterated in key's descending order
     */
    public Cursor(RootReference rootReference, String from, String to, boolean reverse) {
        this.lastPage = rootReference.root;
        this.cursorPos = traverseDown(lastPage, from, reverse);
        this.to = to;
        this.reverse = reverse;
    }

    @Override
    public boolean hasNext() {
        if (cursorPos != null) {
            int increment = reverse ? -1 : 1;
            while (current == null) {
                Page page = cursorPos.page;
                int index = cursorPos.index;
                if (reverse ? index < 0 : index >= upperBound(page)) {
                    // traversal of this page is over, going up a level or stop if at the root already
                    CursorPos tmp = cursorPos;
                    cursorPos = cursorPos.parent;
                    if (cursorPos == null) {
                        return false;
                    }
                    tmp.parent = keeper;
                    keeper = tmp;
                } else {
                    // traverse down to the leaf taking the leftmost path
                    while (!page.isLeaf()) {
                        page = page.getChildPage(index);
                        index = reverse ? upperBound(page) - 1 : 0;
                        if (keeper == null) {
                            cursorPos = new CursorPos(page, index, cursorPos);
                        } else {
                            CursorPos tmp = keeper;
                            keeper = keeper.parent;
                            tmp.parent = cursorPos;
                            tmp.page = page;
                            tmp.index = index;
                            cursorPos = tmp;
                        }
                    }
                    if (reverse ? index >= 0 : index < page.getKeyCount()) {
                        String key = page.getKey(index);
                        if (to != null && Integer.signum(page.map.compare(key, to)) == increment) {
                            return false;
                        }
                        current = last = key;
                        lastValue = page.getValue(index);
                        lastPage = page;
                    }
                }
                cursorPos.index += increment;
            }
        }
        return current != null;
    }

    @Override
    public String next() {
        if(!hasNext()) {
            throw new NoSuchElementException();
        }
        current = null;
        return last;
    }

    /**
     * Get the last read key if there was one.
     *
     * @return the key or null
     */
    public String getKey() {
        return last;
    }

    /**
     * Get the last read value if there was one.
     *
     * @return the value or null
     */
    public String getValue() {
        return lastValue;
    }

    /**
     * Get the page where last retrieved key is located.
     *
     * @return the page
     */
    @SuppressWarnings("unused")
    Page getPage() {
        return lastPage;
    }

    /**
     * Skip over that many entries. This method is relatively fast (for this map
     * implementation) even if many entries need to be skipped.
     *
     * @param n the number of entries to skip
     */
    public void skip(long n) {
        if (n < 10) {
            while (n-- > 0 && hasNext()) {
                next();
            }
        } else if(hasNext()) {
            assert cursorPos != null;
            CursorPos cp = cursorPos;
            CursorPos parent;
            while ((parent = cp.parent) != null) cp = parent;
            Page root = cp.page;
            MVMap map = root.map;
            long index = map.getKeyIndex(next());
            last = map.getKey(index + (reverse ? -n : n));
            this.cursorPos = traverseDown(root, last, reverse);
        }
    }

    /**
     * Fetch the next entry that is equal or larger than the given key, starting
     * from the given page. This method retains the stack.
     *
     * @param page to start from as a root
     * @param key to search for, null means search for the first available key
     */
    static  CursorPos traverseDown(Page page, String key, boolean reverse) {
        CursorPos cursorPos = key != null ? CursorPos.traverseDown(page, key) :
                reverse ? page.getAppendCursorPos(null) : page.getPrependCursorPos(null);
        int index = cursorPos.index;
        if (index < 0) {
            index = ~index;
            if (reverse) {
                --index;
            }
            cursorPos.index = index;
        }
        return cursorPos;
    }

    private static <K,V> int upperBound(Page page) {
        return page.isLeaf() ? page.getKeyCount() : page.map.getChildPageCount(page);
    }
}
