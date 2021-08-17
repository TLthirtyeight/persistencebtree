package com.ysq.persistencebtree.visualizer.algo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

public class BTNode<E extends Comparable<E>> implements Serializable {
    private static final long serialVersionUID = 987654321;

    private int fullNumber;
    private BTNode<E> father;
    private ArrayList<BTNode<E>> children = new ArrayList<BTNode<E>>();
    private ArrayList<E> keys = new ArrayList<>();

    public BTNode() {
    }

    public BTNode(int order) {
        fullNumber = order - 1;
    }

    /**
     * @return true, if node is a leaf
     */
    public boolean isLastInternalNode() {
        if (keys.size() == 0)
            return false;
        for (BTNode<E> node : children)
            if (node.keys.size() != 0)
                return false;
        return true;
    }

    /**
     * @return the father
     */
    public BTNode<E> getFather() {
        return father;
    }

    /**
     * @param father the father to set
     */
    public void setFather(BTNode<E> father) {
        this.father = father;
    }

    public ArrayList<BTNode<E>> getChildren() {
        return children;
    }

    /**
     * @param index the index to get
     * @return the child
     */
    public BTNode<E> getChild(int index) {
        return children.get(index);
    }

    /**
     * @param index the index to add
     * @param node  the node to be added
     */
    public void addChild(int index, BTNode<E> node) {
        node.setFather(this);
        children.add(index, node);
    }

    public void addChild(BTNode<E> node) {
        node.setFather(this);
        if (node != null) {
            children.add(node);
        }
    }

    /**
     * @param index the index to remove
     */
    public void removeChild(int index) {
        children.remove(index);
    }

    /**
     * @param index the index to get
     * @return the key
     */
    public E getKey(int index) {
        return keys.get(index);
    }

    /**
     * @param index   the index to add
     * @param element the element be added
     */
    public void addKey(int index, E element) {
        keys.add(index, element);
    }

    /**
     * @param index the index to remove
     */
    public void removeKey(int index) {
        keys.remove(index);
    }

    public ArrayList<E> getKeys() {
        return keys;
    }

    /**
     * @return true, if keys.size() == order - 1
     */
    public boolean isFull() {
        return fullNumber == keys.size();
    }

    /**
     * @return true, if keys.size() > order - 1
     */
    public boolean isOverflow() {
        return fullNumber < keys.size();
    }

    /**
     * @return true, if keys is empty
     */
    public boolean isNull() {
        return keys.isEmpty();
    }

    /**
     * @return keys size
     */
    public int getSize() {
        return keys.size();
    }

    public int binarySearch(E key) {
        if (isNumeric((String)key)) {
            int low = 0;
            int high = keys.size() - 1;
            int mid = -1;
            while (low <= high) {
                // int compare = key.compareTo(storage[x]);
                mid = (low + high) >>> 1;
                int compare = Integer.parseInt((String) key) - Integer.parseInt((String) keys.get(mid));
                if (compare == 0) {
                    return mid;
                }
                if (compare < 0) {
                    high = mid - 1;
                } else if (compare > 0){
                    low = mid + 1;
                }
            }
            return -(low + 1);
        }
       return Collections.binarySearch(keys, key);
    }

    private boolean isNumeric(String str) {
        String bigStr;
        try {
            bigStr = new BigDecimal(str).toString();
        } catch (Exception e) {
            return false;//异常 说明包含非数字。
        }
        return true;
    }
}
