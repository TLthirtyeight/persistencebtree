package com.ysq.persistencebtree.visualizer.algo;

/**
 * @Author: ysq
 * @Description:
 * @Date: 2021/8/17 22:13
 */

public class SearchingChain<K extends Comparable<K>> {
    public BTNode<K> lastChild;
    public int index;
    public SearchingChain<K> parent;
}
