package com.ysq.persistencebtree.visualizer.application;

import com.ysq.persistencebtree.visualizer.algo.BTree;
import com.ysq.persistencebtree.visualizer.visual.BTreePane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.LinkedList;

public class Main extends Application {

	private int key;
	private BTreePane btPane;
	private TextField keyText = new TextField();
	private Button previousButton = new Button("Prev");
	private Button nextButton = new Button("Next");

	private int index = 0;
	private LinkedList<BTree<Integer>> bTreeLinkedList = new LinkedList<BTree<Integer>>();
	private BTree<Integer> bTree = new BTree<Integer>(3);

	@Override
	public void start(Stage primaryStage) {
		// TODO Auto-generated method stub
		final int windowHeight = 480;
		final int windowWidth = 720;

		BorderPane root = new BorderPane();

		// Create button HBox on top
		HBox hBox = new HBox(15);
		root.setTop(hBox);
		BorderPane.setMargin(hBox, new Insets(10, 10, 10, 10));
		// TextField
		keyText.setPrefWidth(60);
		keyText.setAlignment(Pos.BASELINE_RIGHT);
		// Button
		Button insertButton = new Button("Insert");
		Button deleteButton = new Button("Delete");
		Button searchButton = new Button("Search");
		Button resetButton = new Button("Reset");
		resetButton.setId("reset");
		resetButton.setStyle("-fx-base: red;");
		Label nullLabel = new Label();
		nullLabel.setPrefWidth(30);

		hBox.getChildren().addAll(new Label("Enter a number: "), keyText, insertButton, deleteButton, searchButton,
				resetButton, nullLabel, previousButton, nextButton);
		hBox.setAlignment(Pos.CENTER);
		checkVisible();

		// Create TreePane in center
		btPane = new BTreePane(windowWidth / 2, 50, bTree);
		btPane.setPrefSize(windowHeight, windowWidth);
		root.setCenter(btPane);

		insertButton.setOnMouseClicked(e -> insertValue());
		deleteButton.setOnMouseClicked(e -> deleteValue());
		searchButton.setOnMouseClicked(e -> searchValue());
		resetButton.setOnMouseClicked(e -> reset());
		previousButton.setOnMouseClicked(e -> goPrevious());
		nextButton.setOnMouseClicked(e -> goNext());

		// Create a scene
		Scene scene = new Scene(root, 720, 360);
		scene.getStylesheets().add(getClass().getResource("BtreeStyle.css").toExternalForm());
		primaryStage.setTitle("B-Tree Visualization");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	private void checkVisible() {
		if (index > 0 && index < bTreeLinkedList.size() - 1) {
			previousButton.setVisible(true);
			nextButton.setVisible(true);
		} else if (index > 0 && index == bTreeLinkedList.size() - 1) {
			previousButton.setVisible(true);
			nextButton.setVisible(false);
		} else if (index == 0 && index < bTreeLinkedList.size() - 1) {
			previousButton.setVisible(false);
			nextButton.setVisible(true);
		} else {
			previousButton.setVisible(false);
			nextButton.setVisible(false);
		}
	}

	private void insertValue() {
		try {
			key = Integer.parseInt(keyText.getText());
			keyText.setText("");
			bTree.setStepTrees(new LinkedList<BTree<Integer>>());

			bTree.insert(key);

			index = 0;
			bTreeLinkedList = bTree.getStepTrees();
			btPane.updatePane(bTreeLinkedList.get(0));
			checkVisible();
		} catch (NumberFormatException e) {
			Alert alert = new Alert(Alert.AlertType.WARNING, "Illegal input data!", ButtonType.OK);
			alert.show();
		}
	}

	private void deleteValue() {
		try {
			key = Integer.parseInt(keyText.getText());
			keyText.setText("");
			if (bTree.getNode(key) == bTree.nullBTNode) {
				throw new Exception("Not in the tree!");
			}
			bTree.setStepTrees(new LinkedList<BTree<Integer>>());

			bTree.delete(key);

			index = 0;
			bTreeLinkedList = bTree.getStepTrees();
			btPane.updatePane(bTreeLinkedList.get(0));
			checkVisible();
		} catch (NumberFormatException e) {
			Alert alert = new Alert(Alert.AlertType.WARNING, "Illegal input data!", ButtonType.OK);
			alert.show();
		} catch (Exception e) {
			Alert alert = new Alert(Alert.AlertType.WARNING, e.getMessage(), ButtonType.OK);
			alert.show();
		}
	}

	private void searchValue() {
		try {
			key = Integer.parseInt(keyText.getText());
			keyText.setText("");

			btPane.searchPathColoring(bTree, key);

		} catch (NumberFormatException e) {
			Alert alert = new Alert(Alert.AlertType.WARNING, "Illegal input data!", ButtonType.OK);
			alert.show();
		} catch (Exception e) {
			Alert alert = new Alert(Alert.AlertType.WARNING, e.getMessage(), ButtonType.OK);
			alert.show();
		}
	}

	private void goPrevious() {
		if (index > 0) {
			index--;
			btPane.updatePane(bTreeLinkedList.get(index));

			checkVisible();
		}
	}

	private void goNext() {
		if (index < bTreeLinkedList.size() - 1) {
			index++;
			System.out.println("index: " + index + " - size: " + bTreeLinkedList.size());
			btPane.updatePane(bTreeLinkedList.get(index));

			checkVisible();
		}
	}

	private void reset() {
		keyText.setText("");

		bTree.setRoot(null);
		index = 0;
		bTreeLinkedList.clear();
		btPane.updatePane(bTree);
		checkVisible();
	}
}