package com.ysq.persistencebtree.visualizer.algo;

import java.io.Serializable;

public class BPlusTree<K extends Comparable<K>> implements Serializable {
    private static final long serialVersionUID = 123456789;
    private BTNode<K> root = null;

    public BPlusTree() {

    }

    public BPlusTree(BTNode<K> root) {
        this.root = root;
    }


    public void setRoot(BTNode<K> root) {
        this.root = root;
    }

    public BTNode<K> getRoot() {
        return root;
    }

    public SearchingChain<K> search(K key) {
        BTNode<K> lastChild = root;
        SearchingChain<K> parent = null;
        int index = -1;
        while (lastChild.getChildren().size() != 0) {
            index = lastChild.binarySearch(key) + 1;
            if (index < 0) {
                index = -index;
            }
            SearchingChain<K> currentSearchingChain = new SearchingChain<>();
            currentSearchingChain.index = index;
            currentSearchingChain.lastChild = lastChild;
            currentSearchingChain.parent = parent;
            parent = currentSearchingChain;
            lastChild = lastChild.getChild(index);
        }
        int result = lastChild.binarySearch(key);
        if (result < 0) {
            return null;
        }
        SearchingChain<K> lastSearchingChain = new SearchingChain<>();
        lastSearchingChain.lastChild = lastChild;
        lastSearchingChain.index = result;
        lastSearchingChain.parent = parent;
        return lastSearchingChain;
    }
}
