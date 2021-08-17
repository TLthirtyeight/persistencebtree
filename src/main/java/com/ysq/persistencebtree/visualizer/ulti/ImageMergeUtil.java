package com.ysq.persistencebtree.visualizer.ulti;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: ysq
 * @Description:
 * @Date: 2021/8/12 22:50
 */
public class ImageMergeUtil {
    /**
     * 待合并的两张图必须满足这样的前提，如果水平方向合并，则高度必须相等；如果是垂直方向合并，宽度必须相等。
     * mergeImage方法不做判断，自己判断。
     *
     * @param img1         待合并的第一张图
     * @param img2         带合并的第二张图
     * @param isHorizontal 为true时表示水平方向合并，为false时表示垂直方向合并
     * @return 返回合并后的BufferedImage对象
     * @throws IOException
     */
    public static BufferedImage mergeImage(BufferedImage img1,
                                           BufferedImage img2, boolean isHorizontal) throws IOException {
        int w1 = img1.getWidth();
        int h1 = img1.getHeight();
        int w2 = img2.getWidth();
        int h2 = img2.getHeight();

        // 从图片中读取RGB
        int[] ImageArrayOne = new int[w1 * h1];
        ImageArrayOne = img1.getRGB(0, 0, w1, h1, ImageArrayOne, 0, w1); // 逐行扫描图像中各个像素的RGB到数组中
        int[] ImageArrayTwo = new int[w2 * h2];
        ImageArrayTwo = img2.getRGB(0, 0, w2, h2, ImageArrayTwo, 0, w2);

        // 生成新图片
        BufferedImage DestImage = null;
        if (isHorizontal) { // 水平方向合并
            DestImage = new BufferedImage(w1 + w2, h1, BufferedImage.TYPE_INT_RGB);
            DestImage.setRGB(0, 0, w1, h1, ImageArrayOne, 0, w1); // 设置上半部分或左半部分的RGB
            DestImage.setRGB(w1, 0, w2, h2, ImageArrayTwo, 0, w2);
        } else { // 垂直方向合并
            DestImage = new BufferedImage(w1, h1 + h2, BufferedImage.TYPE_INT_RGB);
            DestImage.setRGB(0, 0, w1, h1, ImageArrayOne, 0, w1); // 设置上半部分或左半部分的RGB
            DestImage.setRGB(0, h1, w2, h2, ImageArrayTwo, 0, w2); // 设置下半部分的RGB
        }

        return DestImage;
    }

    /**
     * @param fileUrl 文件绝对路径或相对路径
     * @return 读取到的缓存图像
     * @throws IOException 路径错误或者不存在该文件时抛出IO异常
     */
    public static BufferedImage getBufferedImage(String fileUrl)
            throws IOException {
        File f = new File(fileUrl);
        return ImageIO.read(f);
    }

    /**
     * 输出图片
     *
     * @param buffImg  图像拼接叠加之后的BufferedImage对象
     * @param savePath 图像拼接叠加之后的保存路径
     */
    public static void generateSaveFile(BufferedImage buffImg, String savePath) {
        int temp = savePath.lastIndexOf(".") + 1;
        try {
            File outFile = new File(savePath);
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            ImageIO.write(buffImg, savePath.substring(temp), outFile);
            System.out.println("ImageIO write...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Java 测试图片合并方法
     */
    public void imageMargeTest() {
        // 读取待合并的文件
        BufferedImage bi1 = null;
        BufferedImage bi2 = null;
        // 调用mergeImage方法获得合并后的图像
        BufferedImage destImg = null;
        System.out.println("下面是垂直合并的情况：");
        String saveFilePath = "C:\\Users\\ysq\\Desktop\\temppic\\1.png";
        String divingPath = "C:\\Users\\ysq\\Desktop\\temppic\\2.png";
        String margeImagePath = "C:\\Users\\ysq\\Desktop\\temppic\\margeNew.png";
        try {
            bi1 = getBufferedImage(saveFilePath);
            bi2 = getBufferedImage(divingPath);
            // 调用mergeImage方法获得合并后的图像
            destImg = mergeImage(bi1, bi2, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 保存图像
        generateSaveFile(destImg, margeImagePath);
        System.out.println("垂直合并完毕!");
    }


    public static void imageMarge(String saveFilePath, String divingPath, String margeImagePath, boolean isHorizortal) {
        // 读取待合并的文件
        BufferedImage bi1 = null;
        BufferedImage bi2 = null;
        // 调用mergeImage方法获得合并后的图像
        BufferedImage destImg = null;
        try {
            bi1 = getBufferedImage(saveFilePath);
            bi2 = getBufferedImage(divingPath);
            // 调用mergeImage方法获得合并后的图像
            destImg = mergeImage(bi1, bi2, isHorizortal);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 保存图像
        generateSaveFile(destImg, margeImagePath);
    }

    /**
     * 把所有图片合成一张
     *
     * @param pictures        所有图片的序号，每一行一个list
     * @param savePictureFile 图片保存路径
     */
    public static void mergeAllPictures1(List<List<Integer>> pictures, File savePictureFile) throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(pictures.size());
        ExecutorService service = Executors.newCachedThreadPool();

        String picturePath = savePictureFile.getParent();
        List<String> verticalPictures = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            int finalI = i;
            service.execute(() -> {
                List<Integer> picturesInARow = pictures.get(finalI);
                int size = picturesInARow.size();
                if (size > 1) {
                    String currentPic = picturePath.concat(File.separator).concat(picturesInARow.get(0) + ".png");
                    for (int j = 1; j < size; j++) {
                        String mergePic = picturePath.concat(File.separator).concat(picturesInARow.get(j) + ".png");
                        String tmpPic = picturePath.concat(File.separator).concat(System.currentTimeMillis() + ".png");
                        imageMarge(currentPic, mergePic, tmpPic, true);
                        currentPic = tmpPic;
                    }
                    verticalPictures.add(currentPic);
                    // System.out.println(currentPic);
                    countDownLatch.countDown();
                }
            });

        }

        countDownLatch.await();
        if (verticalPictures.size() > 1) {
            String currentPic = verticalPictures.get(0);
            for (int i = 1; i < verticalPictures.size(); i++) {
                String mergePic = verticalPictures.get(i);
                String tmpPic = picturePath.concat(File.separator).concat(System.currentTimeMillis() + ".png");
                imageMarge(currentPic, mergePic, tmpPic, false);
                currentPic = tmpPic;
            }
            System.out.println(currentPic);
            File outPutPic = new File(currentPic);
            outPutPic.renameTo(savePictureFile);
        }

    }

    public static void mergeAllPictures(List<List<Integer>> pictures, File savePictureFile) throws InterruptedException {

        String picturePath = savePictureFile.getParent();
        List<String> verticalPictures = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            List<Integer> picturesInARow = pictures.get(i);

            int size = picturesInARow.size();
            if (size > 1) {
                String currentPic = picturePath.concat(File.separator).concat(picturesInARow.get(0) + ".png");
                for (int j = 1; j < size; j++) {
                    String mergePic = picturePath.concat(File.separator).concat(picturesInARow.get(j) + ".png");
                    String tmpPic = picturePath.concat(File.separator).concat(System.currentTimeMillis() + ".png");
                    imageMarge(currentPic, mergePic, tmpPic, true);
                    currentPic = tmpPic;
                }
                verticalPictures.add(currentPic);
                // System.out.println(currentPic);

            }
        }

        if (verticalPictures.size() > 1) {
            String currentPic = verticalPictures.get(0);
            for (int i = 1; i < verticalPictures.size(); i++) {
                String mergePic = verticalPictures.get(i);
                String tmpPic = picturePath.concat(File.separator).concat(System.currentTimeMillis() + ".png");
                imageMarge(currentPic, mergePic, tmpPic, false);
                currentPic = tmpPic;
            }
            System.out.println(currentPic);
            File outPutPic = new File(currentPic);
            outPutPic.renameTo(savePictureFile);
        }

    }

    public static void mergeAllPictures2(List<List<Integer>> pictures, File savePictureFile) throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(pictures.size());
        ExecutorService service = Executors.newCachedThreadPool();

        String picturePath = savePictureFile.getParent();
        List<String> verticalPictures = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            List<Integer> picturesInARow = pictures.get(i);
            service.execute(() -> {
                int size = picturesInARow.size();
                if (size > 1) {
                    String currentPic = picturePath.concat(File.separator).concat(picturesInARow.get(0) + ".png");
                    for (int j = 1; j < size; j++) {
                        String mergePic = picturePath.concat(File.separator).concat(picturesInARow.get(j) + ".png");
                        String tmpPic = picturePath.concat(File.separator).concat(System.currentTimeMillis() + ".png");
                        imageMarge(currentPic, mergePic, tmpPic, true);
                        currentPic = tmpPic;
                    }
                    verticalPictures.add(currentPic);
                    // System.out.println(currentPic);
                    countDownLatch.countDown();
                }
            });

        }

        countDownLatch.await();
        if (verticalPictures.size() > 1) {
            String currentPic = verticalPictures.get(0);
            for (int i = 1; i < verticalPictures.size(); i++) {
                String mergePic = verticalPictures.get(i);
                String tmpPic = picturePath.concat(File.separator).concat(System.currentTimeMillis() + ".png");
                imageMarge(currentPic, mergePic, tmpPic, false);
                currentPic = tmpPic;
            }
            System.out.println(currentPic);
            File outPutPic = new File(currentPic);
            outPutPic.renameTo(savePictureFile);
        }
    }
}
