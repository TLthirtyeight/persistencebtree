package com.ysq.persistencebtree.visualizer.application;

import com.ysq.persistencebtree.mvstore.MVMap;
import com.ysq.persistencebtree.mvstore.MVStore;
import com.ysq.persistencebtree.mvstore.Page;
import com.ysq.persistencebtree.visualizer.algo.BPlusTree;
import com.ysq.persistencebtree.visualizer.algo.BTNode;
import com.ysq.persistencebtree.visualizer.visual.PersistenceBTreePane;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

/**
 * @Author: ysq
 * @Description:
 * @Date: 2021/7/19 21:58
 */
public class PersistenceBtreeApp extends Application {

    private TextField h2StoreFile = new TextField();

    private BTNode<String> rootNode = new BTNode<>();

    @Override
    public void start(Stage primaryStage) throws Exception {
        final int windowHeight = 1040;
        final int windowWidth = 7200;


        BorderPane root = new BorderPane();
        HBox hBox = new HBox(15);
        root.setTop(hBox);
        BorderPane.setMargin(hBox, new Insets(10, 10, 10, 10));
        Button loadButton = new Button("load");
        Button showButton = new Button("show");


        h2StoreFile.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> ob, String o, String n) {
                h2StoreFile.setPrefColumnCount(h2StoreFile.getText().length() + 1);
            }
        });

        FileChooser fileChooser = new FileChooser();

        hBox.getChildren().addAll(new Label("请选择H2存储文件:"), h2StoreFile, loadButton, showButton);
        hBox.setAlignment(Pos.CENTER);


        loadButton.setOnAction(
                (final ActionEvent e) -> {
                    File file = fileChooser.showOpenDialog(primaryStage);
                    if (file != null) {
                        h2StoreFile.setText(file.getAbsolutePath());
                    }
                });


//        BPlusTree<String> bTree = initBtree();
//        PersistenceBTreePane persistenceBTreePane = new PersistenceBTreePane(windowWidth / 2, 50, bTree);
//        root.setCenter(persistenceBTreePane);
//        persistenceBTreePane.setPrefSize(windowWidth, windowHeight);
        showButton.setOnAction((final ActionEvent e) -> {
            if (!checkH2DBFile()) {
                return;
            }
            BPlusTree<String> bTree = initBtree(h2StoreFile.getText());

            ScrollPane scrollPane = new ScrollPane();

            PersistenceBTreePane persistenceBTreePane = new PersistenceBTreePane(windowWidth / 2, 50, bTree);

            // 去掉 persistenceBTreePane.setPrefSize(windowWidth, windowHeight);
            persistenceBTreePane.drawBTree(bTree.getRoot(), windowWidth / 2, 80);

            scrollPane.setContent(persistenceBTreePane);
            scrollPane.autosize();
            root.setCenter(scrollPane);
        });

        // Create a scene
        Scene scene = new Scene(root, 720, 360);
        scene.getStylesheets().add(getClass().getResource("BtreeStyle.css").toExternalForm());
        primaryStage.setTitle("B-Tree Visualization");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private boolean checkH2DBFile() {
        String h2DBFileName = h2StoreFile.getText();
        if (h2DBFileName == null || h2DBFileName.length() == 0 || !h2DBFileName.endsWith(".mv.db")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error Dialog");
            //alert.setHeaderText("Look, an Error Dialog");
            alert.setContentText("非法h2存储文件路径!");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    BPlusTree<String> initBtree(String h2DBFile) {
        MVStore s = MVStore.open(h2DBFile);
        // 先统一叫map 名字为data
        MVMap map = s.openMap("data");

        Page rootPage = map.getRootPage();


        BPlusTree<String> bTree = initBtreeDFS(rootPage);


        return bTree;
    }

    private BPlusTree<String> initBtreeDFS(Page page) {
        solve(page, null);
        BPlusTree<String> bPlusTree = new BPlusTree<>();
        bPlusTree.setRoot(rootNode);
        return bPlusTree;
    }

    private void solve(Page page, BTNode<String> parent) {
        if (page == null) {
            return;
        }
        BTNode<String> node = new BTNode<>();
        for (int j = 0; j < page.getKeyCount(); j++) {
            node.addKey(j, page.getKey(j));
        }
        if (parent == null) {
            rootNode = node;
        }
        if (parent != null) {
            parent.addChild(node);
        }
        if (!page.isLeaf()) {
            for (int j = 0; j < page.getKeyCount() + 1; j++) {
                Page tmpPage = page.getChildPage(j);
                solve(tmpPage, node);
            }
        }
    }


    private BPlusTree<String> initBtree() {
        BPlusTree<String> bPlusTree = new BPlusTree<>();
        BTNode<String> root = new BTNode<>();
        bPlusTree.setRoot(root);

        root.addKey(0, "1");
        root.addKey(1, "2");
        root.addKey(2, "3");
        root.addKey(3, "4");
        root.addKey(4, "5");
        root.addKey(5, "6");

        BTNode<String> c1 = new BTNode<>();
        c1.addKey(0, "5");
        c1.addKey(1, "5");
        c1.addKey(2, "5");
        c1.addKey(3, "5");
        c1.addKey(4, "5");

        root.addChild(0, c1);

        BTNode<String> c2 = new BTNode<>();
        c2.addKey(0, "60");
        c2.addKey(1, "79");

        root.addChild(1, c2);

        BTNode<String> c3 = new BTNode<>();
        c3.addKey(0, "2");
        c3.addKey(1, "3");

        c1.addChild(0, c3);

        BTNode<String> c4 = new BTNode<>();
        c4.addKey(0, "2");
        c4.addKey(1, "3");

        c1.addChild(0, c4);

        BTNode<String> c5 = new BTNode<>();
        c5.addKey(0, "2");
        c5.addKey(1, "2");
        c5.addKey(2, "2");
        c5.addKey(3, "2");
        c5.addKey(4, "2");
        c5.addKey(5, "2");

        c4.addChild(0, c5);

        return bPlusTree;
    }
}
