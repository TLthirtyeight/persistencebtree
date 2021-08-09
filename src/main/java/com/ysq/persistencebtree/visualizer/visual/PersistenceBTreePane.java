package com.ysq.persistencebtree.visualizer.visual;

import com.ysq.persistencebtree.visualizer.algo.BPlusTree;
import com.ysq.persistencebtree.visualizer.algo.BTNode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.*;

public class PersistenceBTreePane extends Pane {
    private BPlusTree<String> bTree;
    private double originalX, originalY;

    // TODO: make node size relate to pane's size
    private final int fontSize = 14;
    private final int rectangleWidth = 40;

    private final int levelVerticalDist = 30 * rectangleWidth;

    private final int levelPaddingLeftDist = 5 * rectangleWidth;

    private final int rowSpace = 60;

    private Map<BTNode<String>, int[]> nodeLocationMap = new HashMap<>();


    public PersistenceBTreePane(double x, double y, BPlusTree<String> bTree) {
        this.originalX = x;
        this.originalY = y;
        this.bTree = bTree;
    }


    private void drawNode(String s, double x, double y, Color color) {
        Rectangle rect = new Rectangle(x, y, rectangleWidth, rectangleWidth);
        rect.setFill(color);
        rect.setStroke(Color.WHITESMOKE);
        rect.setArcHeight(10);
        rect.setArcWidth(10);
        Text txt = new Text(x + 11 - s.length(), y + 20, s);
        txt.setFill(Color.WHITE);
        txt.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, fontSize));
        this.getChildren().addAll(rect, txt);
    }

    private void drawNode(BTNode<String> node, double x, double y, Color color) {
        for (int i = 0; i < node.getKeys().size(); i++) {
            String s = node.getKey(i);
            Rectangle rect = new Rectangle(x, y, rectangleWidth, rectangleWidth);
            rect.setFill(color);
            rect.setStroke(Color.WHITESMOKE);
            rect.setArcHeight(10);
            rect.setArcWidth(10);
            Text txt = new Text(x + 11 - s.length(), y + 20, s);
            txt.setFill(Color.WHITE);
            txt.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, fontSize));
            this.getChildren().addAll(rect, txt);
            x += (rectangleWidth);
        }
    }

    /**
     * 画出B树
     *
     * @param root B树的根节点
     * @param x 起始坐标x
     * @param y 起始坐标y
     */
    public void drawBTree(BTNode<String> root, double x, double y) {
        if (root != null) {
            // Draw keys of node
            List<List<BTNode<String>>> btreelist = new ArrayList<>();
            LinkedList<BTNode<String>> queue = new LinkedList<>();
            queue.addLast(root);

            while (!queue.isEmpty()) {
                int size = queue.size();
                List<BTNode<String>> tmpList = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    BTNode<String> node = queue.pollFirst();
                    tmpList.add(node);
                    for (int j = 0; j < node.getChildren().size(); j++) {
                        queue.add(node.getChild(j));
                    }
                }
                btreelist.add(tmpList);
            }


            int level = btreelist.size();
            for (int currentLevel = level - 1; currentLevel >= 0; currentLevel--) {
                int recttangleY = (currentLevel + 1) * levelVerticalDist;

                List<BTNode<String>> tmpList = btreelist.get(currentLevel);
                if (currentLevel == level - 1) {
                    int rectangleX = (level - 1 - currentLevel) * levelPaddingLeftDist + rectangleWidth;
                    for (int i = 0; i < tmpList.size(); i++) {
                        drawNode(tmpList.get(i), rectangleX, recttangleY, Color.web("#6ab5ff"));
                        markDownNodeLocation(tmpList.get(i), rectangleX, recttangleY, i);
                        //rectangleX += ((tmpList.get(i).getSize() * rectangleWidth) * 3);
                        rectangleX += ((tmpList.get(i).getSize() * rectangleWidth) + rectangleWidth);
                    }
                    continue;
                }

                for (int i = 0; i < tmpList.size(); i++) {
                    BTNode<String> btNode = tmpList.get(i);
                    BTNode<String> middleChildNode = btNode.getChild(btNode.getChildren().size() / 2);
                    int[] location = nodeLocationMap.get(middleChildNode);
                    int rectangleX = location[0];
                    drawNode(btNode, rectangleX, recttangleY, Color.web("#6ab5ff"));
                    markDownNodeLocation(btNode, rectangleX, recttangleY, i);
                    //rectangleX += ((tmpList.get(i).getSize() * rectangleWidth) * 3);
                   // rectangleX += ((tmpList.get(i).getSize() * rectangleWidth) + rectangleWidth);
                }

            }

            drawLines();

        }
    }

    private void drawLines() {
        Iterator<BTNode<String>> iterator = nodeLocationMap.keySet().iterator();
        while (iterator.hasNext()) {
            BTNode<String> child = iterator.next();
            if (child.getFather() != null) {
                int[] childLocation = nodeLocationMap.get(child);
                int startY0 = childLocation[1];
                int startX0 = childLocation[0] + child.getSize() * rectangleWidth / 2;

                BTNode<String> parent = child.getFather();
                int[] parentLocation = nodeLocationMap.get(parent);

                int childIndex = childLocation[2];
                int startY1 = parentLocation[1] + rectangleWidth;

                int startX1 = parentLocation[0] + childIndex * rectangleWidth;

                Line line = new Line(startX0, startY0, startX1, startY1);
                line.setStroke(Color.BLACK);
                line.setStrokeWidth(2.5);
                this.getChildren().add(line);
            }
        }
    }

    private void markDownNodeLocation(BTNode<String> btNode, int rectangleX, int recttangleY, int childIndex) {
        nodeLocationMap.put(btNode, new int[]{rectangleX, recttangleY, childIndex});
    }

}
