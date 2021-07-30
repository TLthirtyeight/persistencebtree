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
}
