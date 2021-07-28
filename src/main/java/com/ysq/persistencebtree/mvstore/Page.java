package com.ysq.persistencebtree.mvstore;

/*
 * Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
import com.ysq.persistencebtree.common.Constants;
import com.ysq.persistencebtree.common.DataUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A page (a node or a leaf).
 * <p>
 * For b-tree nodes, the key at a given index is larger than the largest key of
 * the child at the same index.
 * <p>
 * Serialized format:
 * length of a serialized page in bytes (including this field): int
 * check value: short
 * page number (0-based sequential number within a chunk): varInt
 * map id: varInt
 * number of keys: varInt
 * type: byte (0: leaf, 1: node; +2: compressed)
 * children of the non-leaf node (1 more than keys)
 * compressed: bytes saved (varInt)
 * keys
 * values of the leaf node (one for each key)
 */
public abstract class Page implements Cloneable {

    /**
     * Map this page belongs to
     */
    public final MVMap map;

    /**
     * Position of this page's saved image within a Chunk
     * or 0 if this page has not been saved yet
     * or 1 if this page has not been saved yet, but already removed
     * This "removed" flag is to keep track of pages that concurrently
     * changed while they are being stored, in which case the live bookkeeping
     * needs to be aware of such cases.
     * Field need to be volatile to avoid races between saving thread setting it
     * and other thread reading it to access the page.
     * On top of this update atomicity is required so removal mark and saved position
     * can be set concurrently.
     *
     * @see DataUtils#getPagePos(int, int, int, int) for field format details
     */
    private volatile long pos;

    /**
     * Sequential 0-based number of the page within containing chunk.
     */
    public int pageNo = -1;

    /**
     * The last result of a find operation is cached.
     */
    private int cachedCompare;

    /**
     * The estimated memory used in persistent case, IN_MEMORY marker value otherwise.
     */
    private int memory;

    /**
     * Amount of used disk space by this page only in persistent case.
     */
    private int diskSpaceUsed;

    /**
     * The keys.
     */
    private String[] keys;

    /**
     * Updater for pos field, which can be updated when page is saved,
     * but can be concurrently marked as removed
     */
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<Page> posUpdater =
            AtomicLongFieldUpdater.newUpdater(Page.class, "pos");
    /**
     * The estimated number of bytes used per child entry.
     */
    static final int PAGE_MEMORY_CHILD = Constants.MEMORY_POINTER + 16; //  16 = two longs

    /**
     * The estimated number of bytes used per base page.
     */
    private static final int PAGE_MEMORY =
            Constants.MEMORY_OBJECT +           // this
                    2 * Constants.MEMORY_POINTER +      // map, keys
                    Constants.MEMORY_ARRAY +            // Object[] keys
                    17;                       // pos, cachedCompare, memory, removedInMemory
    /**
     * The estimated number of bytes used per empty internal page object.
     */
    static final int PAGE_NODE_MEMORY =
            PAGE_MEMORY +             // super
                    Constants.MEMORY_POINTER +          // children
                    Constants.MEMORY_ARRAY +            // Object[] children
                    8;                        // totalCount

    /**
     * The estimated number of bytes used per empty leaf page.
     */
    static final int PAGE_LEAF_MEMORY =
            PAGE_MEMORY +             // super
                    Constants.MEMORY_POINTER +          // values
                    Constants.MEMORY_ARRAY;             // Object[] values

    /**
     * Marker value for memory field, meaning that memory accounting is replaced by key count.
     */
    private static final int IN_MEMORY = Integer.MIN_VALUE;

    @SuppressWarnings("rawtypes")
    private static final PageReference[] SINGLE_EMPTY = {PageReference.EMPTY};


    Page(MVMap map) {
        this.map = map;
    }

    Page(MVMap map, Page source) {
        this(map, source.keys);
        memory = source.memory;
    }

    Page(MVMap map, String[] keys) {
        this.map = map;
        this.keys = keys;
    }

    /**
     * Create a new, empty leaf page.
     *
     * @param map the map
     * @return the new page
     */
    static Page createEmptyLeaf(MVMap map) {
        return createLeaf(map, map.getKeyType(),
                map.getValueType(), PAGE_LEAF_MEMORY);
    }

    /**
     * Create a new, empty internal node page.
     *
     * @param map the map
     * @return the new page
     */
    @SuppressWarnings("unchecked")
    static Page createEmptyNode(MVMap map) {
        return createNode(map, map.getKeyType(), SINGLE_EMPTY, 0,
                PAGE_NODE_MEMORY + Constants.MEMORY_POINTER + PAGE_MEMORY_CHILD); // there is always one child
    }

    /**
     * Create a new non-leaf page. The arrays are not cloned.
     *
     * @param map        the map
     * @param keys       the keys
     * @param children   the child page positions
     * @param totalCount the total number of keys
     * @param memory     the memory used in bytes
     * @return the page
     */
    public static Page createNode(MVMap map, String[] keys, PageReference[] children,
                                  long totalCount, int memory) {
        assert keys != null;
        Page page = new NonLeaf(map, keys, children, totalCount);
        page.initMemoryAccount(memory);
        return page;
    }

    /**
     * Create a new leaf page. The arrays are not cloned.
     *
     * @param map    the map
     * @param keys   the keys
     * @param values the values
     * @param memory the memory used in bytes
     * @return the page
     */
    static Page createLeaf(MVMap map, String[] keys, String[] values, int memory) {
        assert keys != null;
        Page page = new Leaf(map, keys, values);
        page.initMemoryAccount(memory);
        return page;
    }

    private void initMemoryAccount(int memoryCount) {
        if (!map.isPersistent()) {
            memory = IN_MEMORY;
        } else if (memoryCount == 0) {

        } else {
            addMemory(memoryCount);
            assert memoryCount == getMemory();
        }
    }

    /**
     * Get the value for the given key, or null if not found.
     * Search is done in the tree rooted at given page.
     *
     * @param key the key
     * @param p   the root page
     * @return the value, or null if not found
     */
    static String get(Page p, String key) {
        while (true) {
            int index = p.binarySearch(key);
            if (p.isLeaf()) {
                return index >= 0 ? p.getValue(index) : null;
            } else if (index++ < 0) {
                index = -index;
            }
            p = p.getChildPage(index);
        }
    }

    /**
     * Read a page.
     *
     * @param buff ByteBuffer containing serialized page info
     * @param pos  the position
     * @param map  the map
     * @return the page
     */
    static Page read(ByteBuffer buff, long pos, MVMap map) {
        boolean leaf = (DataUtils.getPageType(pos) & 1) == DataUtils.PAGE_TYPE_LEAF;
        Page p = leaf ? new Leaf(map) : new NonLeaf(map);
        p.pos = pos;
        p.read(buff);
        return p;
    }

//    static <K> int getMemory(DataType<K> keyType, K key) {
//        return keyType.getMemory(key);
//    }

    /**
     * Get the id of the page's owner map
     *
     * @return id
     */
    public final int getMapId() {
        return map.getId();
    }

    /**
     * Create a copy of this page with potentially different owning map.
     * This is used exclusively during bulk map copying.
     * Child page references for nodes are cleared (re-pointed to an empty page)
     * to be filled-in later to copying procedure. This way it can be saved
     * mid-process without tree integrity violation
     *
     * @param map new map to own resulting page
     * @return the page
     */
    abstract Page copy(MVMap map);

    /**
     * Get the key at the given index.
     *
     * @param index the index
     * @return the key
     */
    public String getKey(int index) {
        return keys[index];
    }

    /**
     * Get the child page at the given index.
     *
     * @param index the index
     * @return the child page
     */
    public abstract Page getChildPage(int index);

    /**
     * Get the position of the child.
     *
     * @param index the index
     * @return the position
     */
    public abstract long getChildPagePos(int index);

    /**
     * Get the value at the given index.
     *
     * @param index the index
     * @return the value
     */
    public abstract String getValue(int index);

    /**
     * Get the number of keys in this page.
     *
     * @return the number of keys
     */
    public final int getKeyCount() {
        return keys.length;
    }

    /**
     * Check whether this is a leaf page.
     *
     * @return true if it is a leaf
     */
    public final boolean isLeaf() {
        return getNodeType() == DataUtils.PAGE_TYPE_LEAF;
    }

    public abstract int getNodeType();

    /**
     * Get the position of the page
     *
     * @return the position
     */
    public final long getPos() {
        return pos;
    }

    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        dump(buff);
        return buff.toString();
    }

    /**
     * Dump debug data for this page.
     *
     * @param buff append buffer
     */
    protected void dump(StringBuilder buff) {
        buff.append("id: ").append(System.identityHashCode(this)).append('\n');
        buff.append("pos: ").append(Long.toHexString(pos)).append('\n');
        if (isSaved()) {
            int chunkId = DataUtils.getPageChunkId(pos);
            buff.append("chunk: ").append(Long.toHexString(chunkId)).append('\n');
        }
    }

    /**
     * Create a copy of this page.
     *
     * @return a mutable copy of this page
     */
    public final Page copy() {
        Page newPage = clone();
        newPage.pos = 0;
        return newPage;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final Page clone() {
        Page clone;
        try {
            clone = (Page) super.clone();
        } catch (CloneNotSupportedException impossible) {
            throw new RuntimeException(impossible);
        }
        return clone;
    }

    /**
     * Search the key in this page using a binary search. Instead of always
     * starting the search in the middle, the last found index is cached.
     * <p>
     * If the key was found, the returned value is the index in the key array.
     * If not found, the returned value is negative, where -1 means the provided
     * key is smaller than any keys in this page. See also Arrays.binarySearch.
     *
     * @param key the key
     * @return the value or null
     */
    int binarySearch(String key) {
        int res = map.binarySearch(key, keys, getKeyCount(), cachedCompare);
        cachedCompare = res < 0 ? ~res : res + 1;
        return res;
    }

    /**
     * Split the page. This modifies the current page.
     *
     * @param at the split index
     * @return the page with the entries after the split index
     */
    abstract Page split(int at);

    /**
     * Split the current keys array into two arrays.
     *
     * @param aCount size of the first array.
     * @param bCount size of the second array/
     * @return the second array.
     */
    final String[] splitKeys(int aCount, int bCount) {
        assert aCount + bCount <= getKeyCount();
        String[] aKeys = createKeyStorage(aCount);
        String[] bKeys = createKeyStorage(bCount);
        System.arraycopy(keys, 0, aKeys, 0, aCount);
        System.arraycopy(keys, getKeyCount() - bCount, bKeys, 0, bCount);
        keys = aKeys;
        return bKeys;
    }

    /**
     * Append additional key/value mappings to this Page.
     * New mappings suppose to be in correct key order.
     *
     * @param extraKeyCount number of mappings to be added
     * @param extraKeys     to be added
     * @param extraValues   to be added
     */
    abstract void expand(int extraKeyCount, String[] extraKeys, String[] extraValues);

    /**
     * Expand the keys array.
     *
     * @param extraKeyCount number of extra key entries to create
     * @param extraKeys     extra key values
     */
    final void expandKeys(int extraKeyCount, String[] extraKeys) {
        int keyCount = getKeyCount();
        String[] newKeys = createKeyStorage(keyCount + extraKeyCount);
        System.arraycopy(keys, 0, newKeys, 0, keyCount);
        System.arraycopy(extraKeys, 0, newKeys, keyCount, extraKeyCount);
        keys = newKeys;
    }

    /**
     * Get the total number of key-value pairs, including child pages.
     *
     * @return the number of key-value pairs
     */
    public abstract long getTotalCount();

    /**
     * Get the number of key-value pairs for a given child.
     *
     * @param index the child index
     * @return the descendant count
     */
    abstract long getCounts(int index);

    /**
     * Replace the child page.
     *
     * @param index the index
     * @param c     the new child page
     */
    public abstract void setChild(int index, Page c);

    /**
     * Replace the key at an index in this page.
     *
     * @param index the index
     * @param key   the new key
     */
    public final void setKey(int index, String key) {
        keys = keys.clone();
        if (isPersistent()) {
            String old = keys[index];
        }
        keys[index] = key;
    }

    /**
     * Replace the value at an index in this page.
     *
     * @param index the index
     * @param value the new value
     * @return the old value
     */
    public abstract String setValue(int index, String value);

    /**
     * Insert a key-value pair into this leaf.
     *
     * @param index the index
     * @param key   the key
     * @param value the value
     */
    public abstract void insertLeaf(int index, String key, String value);

    /**
     * Insert a child page into this node.
     *
     * @param index     the index
     * @param key       the key
     * @param childPage the child page
     */
    public abstract void insertNode(int index, String key, Page childPage);

    /**
     * Insert a key into the key array
     *
     * @param index index to insert at
     * @param key   the key value
     */
    final void insertKey(int index, String key) {
        int keyCount = getKeyCount();
        assert index <= keyCount : index + " > " + keyCount;
        String[] newKeys = createKeyStorage(keyCount + 1);
        DataUtils.copyWithGap(keys, newKeys, keyCount, index);
        keys = newKeys;

        keys[index] = key;

    }

    /**
     * Remove the key and value (or child) at the given index.
     *
     * @param index the index
     */
    public void remove(int index) {
        int keyCount = getKeyCount();
        if (index == keyCount) {
            --index;
        }
        String[] newKeys = createKeyStorage(keyCount - 1);
        DataUtils.copyExcept(keys, newKeys, keyCount, index);
        keys = newKeys;
    }

    /**
     * Read the page from the buffer.
     *
     * @param buff the buffer to read from
     */
    private void read(ByteBuffer buff) {
        int chunkId = DataUtils.getPageChunkId(pos);
        int offset = DataUtils.getPageOffset(pos);

        int start = buff.position();
        int pageLength = buff.getInt(); // does not include optional part (pageNo)
        int remaining = buff.remaining() + 4;
        if (pageLength > remaining || pageLength < 4) {
            throw DataUtils.newMVStoreException(DataUtils.ERROR_FILE_CORRUPT,
                    "File corrupted in chunk {0}, expected page length 4..{1}, got {2}", chunkId, remaining,
                    pageLength);
        }

        short check = buff.getShort();
        int checkTest = DataUtils.getCheckValue(chunkId)
                ^ DataUtils.getCheckValue(offset)
                ^ DataUtils.getCheckValue(pageLength);
        if (check != (short) checkTest) {
            throw DataUtils.newMVStoreException(DataUtils.ERROR_FILE_CORRUPT,
                    "File corrupted in chunk {0}, expected check value {1}, got {2}", chunkId, checkTest, check);
        }

        pageNo = DataUtils.readVarInt(buff);
        if (pageNo < 0) {
            throw DataUtils.newMVStoreException(DataUtils.ERROR_FILE_CORRUPT,
                    "File corrupted in chunk {0}, got negative page No {1}", chunkId, pageNo);
        }

        int mapId = DataUtils.readVarInt(buff);
        if (mapId != map.getId()) {
            throw DataUtils.newMVStoreException(DataUtils.ERROR_FILE_CORRUPT,
                    "File corrupted in chunk {0}, expected map id {1}, got {2}", chunkId, map.getId(), mapId);
        }

        int keyCount = DataUtils.readVarInt(buff);
        keys = createKeyStorage(keyCount);
        int type = buff.get();
        if (isLeaf() != ((type & 1) == DataUtils.PAGE_TYPE_LEAF)) {
            throw DataUtils.newMVStoreException(
                    DataUtils.ERROR_FILE_CORRUPT,
                    "File corrupted in chunk {0}, expected node type {1}, got {2}",
                    chunkId, isLeaf() ? "0" : "1", type);
        }

        // to restrain hacky GenericDataType, which grabs the whole remainder of the buffer
        buff.limit(start + pageLength);

        if (!isLeaf()) {
            readPayLoad(buff);
        }
        boolean compressed = (type & DataUtils.PAGE_COMPRESSED) != 0;

        map.read(buff, keys, keyCount);
        if (isLeaf()) {
            readPayLoad(buff);
        }
        diskSpaceUsed = pageLength;
    }

    /**
     * Read the page payload from the buffer.
     *
     * @param buff the buffer
     */
    protected abstract void readPayLoad(ByteBuffer buff);

    public final boolean isSaved() {
        return DataUtils.isPageSaved(pos);
    }

    public final boolean isRemoved() {
        return DataUtils.isPageRemoved(pos);
    }

    /**
     * Mark this page as removed "in memory". That means that only adjustment of
     * "unsaved memory" amount is required. On the other hand, if page was
     * persisted, it's removal should be reflected in occupancy of the
     * containing chunk.
     *
     * @return true if it was marked by this call or has been marked already,
     * false if page has been saved already.
     */
    private boolean markAsRemoved() {
        assert getTotalCount() > 0 : this;
        long pagePos;
        do {
            pagePos = pos;
            if (DataUtils.isPageSaved(pagePos)) {
                return false;
            }
            assert !DataUtils.isPageRemoved(pagePos);
        } while (!posUpdater.compareAndSet(this, 0L, 1L));
        return true;
    }

    /**
     * Store the page and update the position.
     *
     * @param chunk the chunk
     * @param buff  the target buffer
     * @param toc   prospective table of content
     * @return the position of the buffer just after the type
     */
    protected final int write(Chunk chunk, WriteBuffer buff, List<Long> toc) {
        pageNo = toc.size();
        int keyCount = getKeyCount();
        int start = buff.position();
        buff.putInt(0)          // placeholder for pageLength
                .putShort((byte) 0) // placeholder for check
                .putVarInt(pageNo)
                .putVarInt(map.getId())
                .putVarInt(keyCount);
        int typePos = buff.position();
        int type = isLeaf() ? DataUtils.PAGE_TYPE_LEAF : DataUtils.PAGE_TYPE_NODE;
        buff.put((byte) type);
        int childrenPos = buff.position();
        writeChildren(buff, true);
        int compressStart = buff.position();
        map.write(buff, keys, keyCount);
        writeValues(buff);
        MVStore store = map.getStore();
        int expLen = buff.position() - compressStart;
        if (expLen > 16) {
            int compressionLevel = store.getCompressionLevel();
        }
        int pageLength = buff.position() - start;
        long tocElement = DataUtils.getTocElement(getMapId(), start, buff.position() - start, type);
        toc.add(tocElement);
        int chunkId = chunk.id;
        int check = DataUtils.getCheckValue(chunkId)
                ^ DataUtils.getCheckValue(start)
                ^ DataUtils.getCheckValue(pageLength);
        buff.putInt(start, pageLength).
                putShort(start + 4, (short) check);
        if (isSaved()) {
            throw DataUtils.newMVStoreException(
                    DataUtils.ERROR_INTERNAL, "Page already stored");
        }
        long pagePos = DataUtils.getPagePos(chunkId, tocElement);
        boolean isDeleted = isRemoved();
        while (!posUpdater.compareAndSet(this, isDeleted ? 1L : 0L, pagePos)) {
            isDeleted = isRemoved();
        }
        if (type == DataUtils.PAGE_TYPE_NODE) {
            // cache again - this will make sure nodes stays in the cache
            // for a longer time
        }
        int pageLengthEncoded = DataUtils.getPageMaxLength(pos);
        boolean singleWriter = map.isSingleWriter();
        chunk.accountForWrittenPage(pageLengthEncoded, singleWriter);
        if (isDeleted) {
            store.accountForRemovedPage(pagePos, chunk.version + 1, singleWriter, pageNo);
        }
        diskSpaceUsed = pageLengthEncoded != DataUtils.PAGE_LARGE ? pageLengthEncoded : pageLength;
        return childrenPos;
    }

    /**
     * Write values that the buffer contains to the buff.
     *
     * @param buff the target buffer
     */
    protected abstract void writeValues(WriteBuffer buff);

    /**
     * Write page children to the buff.
     *
     * @param buff       the target buffer
     * @param withCounts true if the descendant counts should be written
     */
    protected abstract void writeChildren(WriteBuffer buff, boolean withCounts);

    /**
     * Store this page and all children that are changed, in reverse order, and
     * update the position and the children.
     *
     * @param chunk the chunk
     * @param buff  the target buffer
     * @param toc   prospective table of content
     */
    abstract void writeUnsavedRecursive(Chunk chunk, WriteBuffer buff, List<Long> toc);

    /**
     * Unlink the children recursively after all data is written.
     */
    abstract void releaseSavedPages();

    public abstract int getRawChildPageCount();

    protected final boolean isPersistent() {
        return memory != IN_MEMORY;
    }

    public final int getMemory() {
        if (isPersistent()) {
//            assert memory == calculateMemory() :
//                    "Memory calculation error " + memory + " != " + calculateMemory();
            return memory;
        }
        return 0;
    }

    /**
     * Amount of used disk space in persistent case including child pages.
     *
     * @return amount of used disk space in persistent case
     */
    public long getDiskSpaceUsed() {
        long r = 0;
        if (isPersistent()) {
            r += diskSpaceUsed;
            if (!isLeaf()) {
                for (int i = 0; i < getRawChildPageCount(); i++) {
                    long pos = getChildPagePos(i);
                    if (pos != 0) {
                        r += getChildPage(i).getDiskSpaceUsed();
                    }
                }
            }
        }
        return r;
    }

    /**
     * Increase estimated memory used in persistent case.
     *
     * @param mem additional memory size.
     */
    final void addMemory(int mem) {
        memory += mem;
        assert memory >= 0;
    }

    public boolean isComplete() {
        return true;
    }

    /**
     * Called when done with copying page.
     */
    public void setComplete() {
    }

    /**
     * Make accounting changes (chunk occupancy or "unsaved" RAM), related to
     * this page removal.
     *
     * @param version at which page was removed
     * @return amount (negative), by which "unsaved memory" should be adjusted,
     * if page is unsaved one, and 0 for page that was already saved, or
     * in case of non-persistent map
     */
    public final int removePage(long version) {
        if (isPersistent() && getTotalCount() > 0) {
            MVStore store = map.store;
            if (!markAsRemoved()) { // only if it has been saved already
                long pagePos = pos;
                store.accountForRemovedPage(pagePos, version, map.isSingleWriter(), pageNo);
            } else {
                return -memory;
            }
        }
        return 0;
    }

    /**
     * Extend path from a given CursorPos chain to "prepend point" in a B-tree, rooted at this Page.
     *
     * @param cursorPos presumably pointing to this Page (null if real root), to build upon
     * @return new head of the CursorPos chain
     */
    public abstract CursorPos getPrependCursorPos(CursorPos cursorPos);

    /**
     * Extend path from a given CursorPos chain to "append point" in a B-tree, rooted at this Page.
     *
     * @param cursorPos presumably pointing to this Page (null if real root), to build upon
     * @return new head of the CursorPos chain
     */
    public abstract CursorPos getAppendCursorPos(CursorPos cursorPos);

    /**
     * Remove all page data recursively.
     *
     * @param version at which page got removed
     * @return adjustment for "unsaved memory" amount
     */
    public abstract int removeAllRecursive(long version);

    /**
     * Create array for keys storage.
     *
     * @param size number of entries
     * @return values array
     */
    public final String[] createKeyStorage(int size) {
        return new String[size];
    }

    /**
     * Create array for values storage.
     *
     * @param size number of entries
     * @return values array
     */
    final String[] createValueStorage(int size) {
        return new String[size];
    }

    /**
     * Create an array of page references.
     *
     * @param <K>  the key class
     * @param <V>  the value class
     * @param size the number of entries
     * @return the array
     */
    @SuppressWarnings("unchecked")
    public static <K, V> PageReference<K, V>[] createRefStorage(int size) {
        return new PageReference[size];
    }

    /**
     * A pointer to a page, either in-memory or using a page position.
     */
    public static final class PageReference<K, V> {

        /**
         * Singleton object used when arrays of PageReference have not yet been filled.
         */
        @SuppressWarnings("rawtypes")
        static final PageReference EMPTY = new PageReference<>(null, 0, 0);

        /**
         * The position, if known, or 0.
         */
        private long pos;

        /**
         * The page, if in memory, or null.
         */
        private Page page;

        /**
         * The descendant count for this child page.
         */
        final long count;

        /**
         * Get an empty page reference.
         *
         * @param <X> the key class
         * @param <Y> the value class
         * @return the page reference
         */
        @SuppressWarnings("unchecked")
        public static <X, Y> PageReference<X, Y> empty() {
            return EMPTY;
        }

        public PageReference(Page page) {
            this(page, page.getPos(), page.getTotalCount());
        }

        PageReference(long pos, long count) {
            this(null, pos, count);
            assert DataUtils.isPageSaved(pos);
        }

        private PageReference(Page page, long pos, long count) {
            this.page = page;
            this.pos = pos;
            this.count = count;
        }

        public Page getPage() {
            return page;
        }

        /**
         * Clear if necessary, reference to the actual child Page object,
         * so it can be garbage collected if not actively used elsewhere.
         * Reference is cleared only if corresponding page was already saved on a disk.
         */
        void clearPageReference() {
            if (page != null) {
                page.releaseSavedPages();
                assert page.isSaved() || !page.isComplete();
                if (page.isSaved()) {
                    assert pos == page.getPos();
                    assert count == page.getTotalCount() : count + " != " + page.getTotalCount();
                    page = null;
                }
            }
        }

        long getPos() {
            return pos;
        }

        /**
         * Re-acquire position from in-memory page.
         */
        void resetPos() {
            Page p = page;
            if (p != null && p.isSaved()) {
                pos = p.getPos();
                assert count == p.getTotalCount();
            }
        }

        @Override
        public String toString() {
            return "Cnt:" + count + ", pos:" + (pos == 0 ? "0" : DataUtils.getPageChunkId(pos) +
                    (page == null ? "" : "/" + page.pageNo) +
                    "-" + DataUtils.getPageOffset(pos) + ":" + DataUtils.getPageMaxLength(pos)) +
                    ((page == null ? DataUtils.getPageType(pos) == 0 : page.isLeaf()) ? " leaf" : " node") +
                    ", page:{" + page + "}";
        }
    }


    private static class NonLeaf extends Page {
        /**
         * The child page references.
         */
        private PageReference[] children;

        /**
         * The total entry count of this page and all children.
         */
        private long totalCount;

        NonLeaf(MVMap map) {
            super(map);
        }

        NonLeaf(MVMap map, NonLeaf source, PageReference[] children, long totalCount) {
            super(map, source);
            this.children = children;
            this.totalCount = totalCount;
        }

        NonLeaf(MVMap map, String[] keys, PageReference[] children, long totalCount) {
            super(map, keys);
            this.children = children;
            this.totalCount = totalCount;
        }

        @Override
        public int getNodeType() {
            return DataUtils.PAGE_TYPE_NODE;
        }

        @Override
        public Page copy(MVMap map) {
            return new IncompleteNonLeaf(map, this);
        }

        @Override
        public Page getChildPage(int index) {
            PageReference ref = children[index];
            Page page = ref.getPage();
            if (page == null) {
                page = map.readPage(ref.getPos());
                assert ref.getPos() == page.getPos();
                assert ref.count == page.getTotalCount();
            }
            return page;
        }

        @Override
        public long getChildPagePos(int index) {
            return children[index].getPos();
        }

        @Override
        public String getValue(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page split(int at) {
            assert !isSaved();
            int b = getKeyCount() - at;
            String[] bKeys = splitKeys(at, b - 1);
            PageReference[] aChildren = createRefStorage(at + 1);
            PageReference[] bChildren = createRefStorage(b);
            System.arraycopy(children, 0, aChildren, 0, at + 1);
            System.arraycopy(children, at + 1, bChildren, 0, b);
            children = aChildren;

            long t = 0;
            for (PageReference x : aChildren) {
                t += x.count;
            }
            totalCount = t;
            t = 0;
            for (PageReference x : bChildren) {
                t += x.count;
            }
            Page newPage = createNode(map, bKeys, bChildren, t, 0);
            return newPage;
        }

        @Override
        void expand(int extraKeyCount, String[] extraKeys, String[] extraValues) {
            throw new UnsupportedOperationException();
        }

        public void expand(int keyCount, Object[] extraKeys, Object[] extraValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getTotalCount() {
            assert !isComplete() || totalCount == calculateTotalCount() :
                    "Total count: " + totalCount + " != " + calculateTotalCount();
            return totalCount;
        }

        private long calculateTotalCount() {
            long check = 0;
            int keyCount = getKeyCount();
            for (int i = 0; i <= keyCount; i++) {
                check += children[i].count;
            }
            return check;
        }

        void recalculateTotalCount() {
            totalCount = calculateTotalCount();
        }

        @Override
        long getCounts(int index) {
            return children[index].count;
        }

        @Override
        public void setChild(int index, Page c) {
            assert c != null;
            PageReference child = children[index];
            if (c != child.getPage() || c.getPos() != child.getPos()) {
                totalCount += c.getTotalCount() - child.count;
                children = children.clone();
                children[index] = new PageReference<>(c);
            }
        }

        @Override
        public String setValue(int index, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertLeaf(int index, String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertNode(int index, String key, Page childPage) {
            int childCount = getRawChildPageCount();
            insertKey(index, key);

            PageReference[] newChildren = createRefStorage(childCount + 1);
            DataUtils.copyWithGap(children, newChildren, childCount, index);
            children = newChildren;
            children[index] = new PageReference<>(childPage);

            totalCount += childPage.getTotalCount();
            if (isPersistent()) {
                addMemory(Constants.MEMORY_POINTER + PAGE_MEMORY_CHILD);
            }
        }

        @Override
        public void remove(int index) {
            int childCount = getRawChildPageCount();
            super.remove(index);

            totalCount -= children[index].count;
            PageReference[] newChildren = createRefStorage(childCount - 1);
            DataUtils.copyExcept(children, newChildren, childCount, index);
            children = newChildren;
        }

        @Override
        public int removeAllRecursive(long version) {
            int unsavedMemory = removePage(version);
            if (isPersistent()) {
                for (int i = 0, size = map.getChildPageCount(this); i < size; i++) {
                    PageReference ref = children[i];
                    Page page = ref.getPage();
                    if (page != null) {
                        unsavedMemory += page.removeAllRecursive(version);
                    } else {
                        long pagePos = ref.getPos();
                        assert DataUtils.isPageSaved(pagePos);
                        if (DataUtils.isLeafPosition(pagePos)) {
                            map.store.accountForRemovedPage(pagePos, version, map.isSingleWriter(), -1);
                        } else {
                            unsavedMemory += map.readPage(pagePos).removeAllRecursive(version);
                        }
                    }
                }
            }
            return unsavedMemory;
        }

        @Override
        public CursorPos getPrependCursorPos(CursorPos cursorPos) {
            Page childPage = getChildPage(0);
            return childPage.getPrependCursorPos(new CursorPos(this, 0, cursorPos));
        }

        @Override
        public CursorPos getAppendCursorPos(CursorPos cursorPos) {
            int keyCount = getKeyCount();
            Page childPage = getChildPage(keyCount);
            return childPage.getAppendCursorPos(new CursorPos(this, keyCount, cursorPos));
        }

        @Override
        protected void readPayLoad(ByteBuffer buff) {
            int keyCount = getKeyCount();
            children = createRefStorage(keyCount + 1);
            long[] p = new long[keyCount + 1];
            for (int i = 0; i <= keyCount; i++) {
                p[i] = buff.getLong();
            }
            long total = 0;
            for (int i = 0; i <= keyCount; i++) {
                long s = DataUtils.readVarLong(buff);
                long position = p[i];
                assert position == 0 ? s == 0 : s >= 0;
                total += s;
                children[i] = position == 0 ?
                        PageReference.empty() :
                        new PageReference<>(position, s);
            }
            totalCount = total;
        }

        @Override
        protected void writeValues(WriteBuffer buff) {
        }

        @Override
        protected void writeChildren(WriteBuffer buff, boolean withCounts) {
            int keyCount = getKeyCount();
            for (int i = 0; i <= keyCount; i++) {
                buff.putLong(children[i].getPos());
            }
            if (withCounts) {
                for (int i = 0; i <= keyCount; i++) {
                    buff.putVarLong(children[i].count);
                }
            }
        }

        @Override
        void writeUnsavedRecursive(Chunk chunk, WriteBuffer buff, List<Long> toc) {
            if (!isSaved()) {
                int patch = write(chunk, buff, toc);
                writeChildrenRecursive(chunk, buff, toc);
                int old = buff.position();
                buff.position(patch);
                writeChildren(buff, false);
                buff.position(old);
            }
        }

        void writeChildrenRecursive(Chunk chunk, WriteBuffer buff, List<Long> toc) {
            int len = getRawChildPageCount();
            for (int i = 0; i < len; i++) {
                PageReference ref = children[i];
                Page p = ref.getPage();
                if (p != null) {
                    p.writeUnsavedRecursive(chunk, buff, toc);
                    ref.resetPos();
                }
            }
        }

        @Override
        void releaseSavedPages() {
            int len = getRawChildPageCount();
            for (int i = 0; i < len; i++) {
                children[i].clearPageReference();
            }
        }

        @Override
        public int getRawChildPageCount() {
            return getKeyCount() + 1;
        }

        @Override
        public void dump(StringBuilder buff) {
            super.dump(buff);
            int keyCount = getKeyCount();
            for (int i = 0; i <= keyCount; i++) {
                if (i > 0) {
                    buff.append(" ");
                }
                buff.append("[").append(Long.toHexString(children[i].getPos())).append("]");
                if (i < keyCount) {
                    buff.append(" ").append(getKey(i));
                }
            }
        }
    }


    private static class IncompleteNonLeaf extends NonLeaf {

        private boolean complete;

        IncompleteNonLeaf(MVMap map, NonLeaf source) {
            super(map, source, constructEmptyPageRefs(source.getRawChildPageCount()), source.getTotalCount());
        }

        private static <K, V> PageReference<K, V>[] constructEmptyPageRefs(int size) {
            // replace child pages with empty pages
            PageReference<K, V>[] children = createRefStorage(size);
            Arrays.fill(children, PageReference.empty());
            return children;
        }

        @Override
        void writeUnsavedRecursive(Chunk chunk, WriteBuffer buff, List<Long> toc) {
            if (complete) {
                super.writeUnsavedRecursive(chunk, buff, toc);
            } else if (!isSaved()) {
                writeChildrenRecursive(chunk, buff, toc);
            }
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public void setComplete() {
            recalculateTotalCount();
            complete = true;
        }

        @Override
        public void dump(StringBuilder buff) {
            super.dump(buff);
            buff.append(", complete:").append(complete);
        }

    }


    private static class Leaf extends Page {
        /**
         * The storage for values.
         */
        private String[] values;

        Leaf(MVMap map) {
            super(map);
        }

        private Leaf(MVMap map, Leaf source) {
            super(map, source);
            this.values = source.values;
        }

        Leaf(MVMap map, String[] keys, String[] values) {
            super(map, keys);
            this.values = values;
        }

        @Override
        public int getNodeType() {
            return DataUtils.PAGE_TYPE_LEAF;
        }

        @Override
        public Page copy(MVMap map) {
            return new Leaf(map, this);
        }

        @Override
        public Page getChildPage(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getChildPagePos(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getValue(int index) {
            return values[index];
        }

        @Override
        public Page split(int at) {
            assert !isSaved();
            int b = getKeyCount() - at;
            String[] bKeys = splitKeys(at, b);
            String[] bValues = createValueStorage(b);
            if (values != null) {
                String[] aValues = createValueStorage(at);
                System.arraycopy(values, 0, aValues, 0, at);
                System.arraycopy(values, at, bValues, 0, b);
                values = aValues;
            }
            Page newPage = createLeaf(map, bKeys, bValues, 0);
            return newPage;
        }

        @Override
        public void expand(int extraKeyCount, String[] extraKeys, String[] extraValues) {
            int keyCount = getKeyCount();
            expandKeys(extraKeyCount, extraKeys);
            if (values != null) {
                String[] newValues = createValueStorage(keyCount + extraKeyCount);
                System.arraycopy(values, 0, newValues, 0, keyCount);
                System.arraycopy(extraValues, 0, newValues, keyCount, extraKeyCount);
                values = newValues;
            }
        }

        @Override
        public long getTotalCount() {
            return getKeyCount();
        }

        @Override
        long getCounts(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setChild(int index, Page c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String setValue(int index, String value) {
            values = values.clone();
            String old = setValueInternal(index, value);
            return old;
        }

        private String setValueInternal(int index, String value) {
            String old = values[index];
            values[index] = value;
            return old;
        }

        @Override
        public void insertLeaf(int index, String key, String value) {
            int keyCount = getKeyCount();
            insertKey(index, key);

            if (values != null) {
                String[] newValues = createValueStorage(keyCount + 1);
                DataUtils.copyWithGap(values, newValues, keyCount, index);
                values = newValues;
                setValueInternal(index, value);
            }
        }

        @Override
        public void insertNode(int index, String key, Page childPage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(int index) {
            int keyCount = getKeyCount();
            super.remove(index);
            if (values != null) {
                String[] newValues = createValueStorage(keyCount - 1);
                DataUtils.copyExcept(values, newValues, keyCount, index);
                values = newValues;
            }
        }

        @Override
        public int removeAllRecursive(long version) {
            return removePage(version);
        }

        @Override
        public CursorPos getPrependCursorPos(CursorPos cursorPos) {
            return new CursorPos(this, -1, cursorPos);
        }

        @Override
        public CursorPos getAppendCursorPos(CursorPos cursorPos) {
            int keyCount = getKeyCount();
            return new CursorPos(this, ~keyCount, cursorPos);
        }

        @Override
        protected void readPayLoad(ByteBuffer buff) {
            int keyCount = getKeyCount();
            values = createValueStorage(keyCount);
            map.read(buff, values, getKeyCount());
        }

        @Override
        protected void writeValues(WriteBuffer buff) {
            map.write(buff, values, getKeyCount());
        }

        @Override
        protected void writeChildren(WriteBuffer buff, boolean withCounts) {
        }

        @Override
        void writeUnsavedRecursive(Chunk chunk, WriteBuffer buff, List<Long> toc) {
            if (!isSaved()) {
                write(chunk, buff, toc);
            }
        }

        @Override
        void releaseSavedPages() {
        }

        @Override
        public int getRawChildPageCount() {
            return 0;
        }

        @Override
        public void dump(StringBuilder buff) {
            super.dump(buff);
            int keyCount = getKeyCount();
            for (int i = 0; i < keyCount; i++) {
                if (i > 0) {
                    buff.append(" ");
                }
                buff.append(getKey(i));
                if (values != null) {
                    buff.append(':');
                    buff.append(getValue(i));
                }
            }
        }
    }
}


