package visualizertest;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * @Author: ysq
 * @Description:
 * @Date: 2021/7/31 23:08
 */
public class HBoxTest extends Application {
    @Override
    public void start(Stage primaryStage) {
        TextField myTextField = new TextField();
        HBox hbox = new HBox();
        hbox.getChildren().add(myTextField);
        //HBox.setHgrow(myTextField, Priority.ALWAYS);
        hbox.setPadding(new Insets(1));

        Rectangle r1 = new Rectangle(10, 10);
        hbox.getChildren().add(r1);
        // from =>w WW .yi  I BA I.C O M
        Scene scene = new Scene(hbox, 320, 112, Color.rgb(0, 0, 0, 0));

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
//更多请阅读：https://www.yiibai.com/javafx/javafx_hbox.html


