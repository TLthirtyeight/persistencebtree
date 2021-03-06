package com.ysq.persistencebtree.visualizer.application;

import com.pdfjet.PDF;
import com.sun.javafx.robot.impl.FXRobotHelper;
import com.ysq.persistencebtree.mvstore.MVMap;
import com.ysq.persistencebtree.mvstore.MVStore;
import com.ysq.persistencebtree.mvstore.Page;
import com.ysq.persistencebtree.visualizer.algo.BPlusTree;
import com.ysq.persistencebtree.visualizer.algo.BTNode;
import com.ysq.persistencebtree.visualizer.algo.SearchingChain;
import com.ysq.persistencebtree.visualizer.ulti.ImageMergeUtil;
import com.ysq.persistencebtree.visualizer.visual.PersistenceBTreePane;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * @Author: ysq
 * @Description:
 * @Date: 2021/7/19 21:58
 */
public class PersistenceBtreeApp extends Application {

    private TextField h2StoreFile = new TextField();

    private BTNode<String> rootNode = new BTNode<>();

    private ScrollPane scrollPane = new ScrollPane();

    private TextField searchTextField = new TextField();

    private PersistenceBTreePane persistenceBTreePane;

    private BPlusTree<String> bTree;

    private SearchingChain<String> searchingChain;

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
        Button exportButton = new Button("export");


        Button searchButton = new Button("search");


        h2StoreFile.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> ob, String o, String n) {
                h2StoreFile.setPrefColumnCount(h2StoreFile.getText().length() + 1);
            }
        });

        FileChooser fileChooser = new FileChooser();

        hBox.getChildren().addAll(new Label("?????????H2????????????:"), h2StoreFile, loadButton, showButton,
                exportButton, searchTextField, searchButton);
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
            bTree = initBtree(h2StoreFile.getText());
            //BPlusTree<String> bTree = initBtree2(3, 3);

            persistenceBTreePane = new PersistenceBTreePane(windowWidth / 2, 50, bTree);

            // ?????? persistenceBTreePane.setPrefSize(windowWidth, windowHeight);
            persistenceBTreePane.drawBTree(bTree.getRoot(), windowWidth / 2, 80);

            scrollPane.setContent(persistenceBTreePane);
            scrollPane.autosize();
            root.setCenter(scrollPane);


        });

        exportButton.setOnAction((e) -> {
            FileChooser fileChooser1 = new FileChooser();
            FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TXT files (*.png)", "*.png");
            fileChooser1.getExtensionFilters().add(extFilter);
            File savePictureFile = fileChooser1.showSaveDialog(primaryStage);
            if (savePictureFile != null) {
                System.out.println(savePictureFile.getParent());
                System.out.println(savePictureFile.getName());
            }
            //https://blog.csdn.net/u011768325/article/details/37961907
            try {
//                double totalWidth = persistenceBTreePane.getWidth();
//                double totalHeight = persistenceBTreePane.getHeight();
                double totalWidth = 50000;
                double totalHeight = 3000;

                double scrollPaneWidth = scrollPane.getWidth();
                double scrollPaneHeight = scrollPane.getHeight();

                int widthLoop = (int) (totalWidth / scrollPaneWidth);
                int heightLoop = (int) (totalHeight / scrollPaneHeight);

                int startX = 0, startY = 0;
                int picCount = 0;
                List<List<Integer>> tmpPictures = new ArrayList<>();
                for (int i = 0; i < heightLoop + 1; i++) {
                    // ?????????????????????????????????
                    List<Integer> picturesInARow = new ArrayList<>();
                    for (int j = 0; j < widthLoop + 1; j++) {
                        startX = j * (int) scrollPaneWidth;
                        startY = i * (int) scrollPaneHeight;
                        String filePath = savePictureFile.getParent() + File.separator + picCount + ".png";
                        File file = new File(filePath);

                        Rectangle2D rectangle2D = new Rectangle2D(startX, startY, scrollPane.getWidth(), scrollPane.getHeight());
                        SnapshotParameters snapshotParameters = new SnapshotParameters();
                        snapshotParameters.setViewport(rectangle2D);
                        BufferedImage bufImage = SwingFXUtils.fromFXImage(persistenceBTreePane.snapshot(snapshotParameters, null), null);
                        FileOutputStream out = new FileOutputStream(file);
                        ImageIO.write(bufImage, "png", out);
                        out.flush();
                        out.close();
                        picturesInARow.add(picCount);
                        picCount++;
                    }
                    tmpPictures.add(picturesInARow);
                }
                ImageMergeUtil.mergeAllPictures(tmpPictures, savePictureFile);
            } catch (Exception exception) {
                exception.printStackTrace();
            }

        });

        searchButton.setOnAction(e -> {
            BTNode<String> btNode = null;
            if (searchingChain != null) {
                drawWithSearchingChain(searchingChain, Color.web("#6ab5ff"), Color.BLACK);
            }
            searchingChain = bTree.search(searchTextField.getText().trim());
            drawWithSearchingChain(searchingChain, Color.RED, Color.AQUA);
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
            alert.setContentText("??????h2??????????????????!");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    BPlusTree<String> initBtree(String h2DBFile) {
        MVStore s = MVStore.open(h2DBFile);
        // ????????????map ?????????data
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

    public void drawWithSearchingChain(SearchingChain<String> searchingChain, Color color, Color lineColor) {
        BTNode<String> btNode = null;
        List<BTNode<String>> btNodeList = new ArrayList<>();
        while (searchingChain != null && (btNode = searchingChain.lastChild) != null) {
            btNodeList.add(btNode);
            int[] pos = persistenceBTreePane.findBTNodePosition(btNode);
            persistenceBTreePane.drawNode(btNode, pos[0], pos[1], color);
            searchingChain = searchingChain.parent;
        }
        for (int b = 0; b < btNodeList.size() - 1; b++) {
            persistenceBTreePane.drawLine(btNodeList.get(b), btNodeList.get(b + 1), lineColor);
        }
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

    private BPlusTree<String> initBtree1() {
        BPlusTree<String> bPlusTree = new BPlusTree<>();
        BTNode<String> root = new BTNode<>();
        bPlusTree.setRoot(root);

        for (int i = 0; i < 4; i++) {
            root.addKey(i, i + "");
        }


        BTNode<String> c1 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c1.addKey(i, i + "");
        }
        root.addChild(0, c1);

        BTNode<String> c2 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c2.addKey(i, i + "");
        }
        root.addChild(1, c2);

        BTNode<String> c3 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c3.addKey(i, i + "");
        }
        root.addChild(2, c3);

        BTNode<String> c4 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c4.addKey(i, i + "");
        }
        root.addChild(3, c4);

        BTNode<String> c5 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c5.addKey(i, i + "");
        }
        root.addChild(4, c5);

        BTNode<String> c11 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c11.addKey(i, i + "");
        }
        c1.addChild(0, c11);

        BTNode<String> c12 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c12.addKey(i, i + "");
        }
        c1.addChild(1, c12);

        BTNode<String> c13 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c13.addKey(i, i + "");
        }
        c1.addChild(1, c13);

        BTNode<String> c14 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c14.addKey(i, i + "");
        }
        c1.addChild(1, c14);

        BTNode<String> c15 = new BTNode<>();
        for (int i = 0; i < 4; i++) {
            c15.addKey(i, i + "");
        }
        c1.addChild(1, c15);


        return bPlusTree;
    }

    /**
     * ?????????????????????????????????????????????
     *
     * @param m     ????????????????????????key???
     * @param level ??????
     * @return B+???
     */
    private BPlusTree<String> initBtree2(int m, int level) {
        BPlusTree<String> bPlusTree = new BPlusTree<>();


        List<BTNode<String>> btNodes = new ArrayList<>();

        int levelCount = 1;
        for (int i = 1; i <= level; i++) {
            for (int j = 0; j < levelCount; j++) {
                BTNode<String> c = new BTNode<>();
                for (int l = 0; l < m; l++) {
                    c.addKey(l, l + "");
                }
                btNodes.add(c);
            }
            levelCount = levelCount * (m + 1);
        }

        BTNode<String> root = btNodes.get(0);
        bPlusTree.setRoot(root);

        for (int i = 0; i < btNodes.size(); i++) {
            BTNode<String> parent = btNodes.get(i);
            if (i * (m + 1) + 1 < btNodes.size()) {
                int childIndex = i * (m + 1) + 1;
                for (int j = 0; j < m + 1; j++) {
                    parent.addChild(btNodes.get(childIndex++));
                }
            }
        }
        return bPlusTree;
    }
}
