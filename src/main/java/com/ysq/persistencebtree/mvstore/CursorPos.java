package com.ysq.persistencebtree.mvstore;

/**
 * A position in a cursor.
 * Instance represents a node in the linked list, which traces path
 * from a specific (target) key within a leaf node all the way up to te root
 * (bottom up path).
 */
public final class CursorPos {

    /**
     * The page at the current level.
     */
    public Page page;

    /**
     * Index of the key (within page above) used to go down to a lower level
     * in case of intermediate nodes, or index of the target key for leaf a node.
     * In a later case, it could be negative, if the key is not present.
     */
    public int index;

    /**
     * Next node in the linked list, representing the position within parent level,
     * or null, if we are at the root level already.
     */
    public CursorPos parent;


    public CursorPos(Page page, int index, CursorPos parent) {
        this.page = page;
        this.index = index;
        this.parent = parent;
    }

    /**
     * Searches for a given key and creates a breadcrumb trail through a B-tree
     * rooted at a given Page. Resulting path starts at "insertion point" for a
     * given key and goes back to the root.
     *
     * @param page      root of the tree
     * @param key       the key to search for
     * @return head of the CursorPos chain (insertion point)
     */
    static  CursorPos traverseDown(Page page, String key) {
        CursorPos cursorPos = null;
        while (!page.isLeaf()) {
            int index = page.binarySearch(key) + 1;
            if (index < 0) {
                index = -index;
            }
            cursorPos = new CursorPos(page, index, cursorPos);
            page = page.getChildPage(index);
        }
        return new CursorPos(page, page.binarySearch(key), cursorPos);
    }

    /**
     * Calculate the memory used by changes that are not yet stored.
     *
     * @param version the version
     * @return the amount of memory
     */
    int processRemovalInfo(long version) {
        int unsavedMemory = 0;
        for (CursorPos head = this; head != null; head = head.parent) {
            unsavedMemory += head.page.removePage(version);
        }
        return unsavedMemory;
    }

    @Override
    public String toString() {
        return "CursorPos{" +
                "page=" + page +
                ", index=" + index +
                ", parent=" + parent +
                '}';
    }
}
