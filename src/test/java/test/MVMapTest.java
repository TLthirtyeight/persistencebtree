package test;

import com.ysq.persistencebtree.common.FileUtils;
import com.ysq.persistencebtree.mvstore.MVMap;
import com.ysq.persistencebtree.mvstore.MVStore;
import com.ysq.persistencebtree.mvstore.Page;
import org.junit.Test;

import java.util.LinkedList;

/**
 * @author y00522461
 * @description TODO
 * @since 2021/7/10
 */
public class MVMapTest {
    private static String MVDBFILE = "F:/h2data_1/test9.mv.db";
    @Test
    public static void testOpenMVMap() {
        String fileName = MVDBFILE;
        FileUtils.delete(fileName);

        MVStore s = MVStore.open(fileName);
        MVMap map = s.openMap("data");
        for (int i = 0; i < 30000; i++) {
            map.put(i + "", i + "");
        }
        s.commit();
//        for (int i = 0; i < 100; i++) {
//            map.put(i, "Hi");
//        }
//        s.commit();
        s.close();
    }

    @Test
    public static void testReadMVMap() {
        String fileName = MVDBFILE;
        MVStore s = MVStore.open(fileName);
        MVMap map = s.openMap("data");
//        for (int i = 0; i < 100; i++) {
//            System.out.println(map.get(i + ""));
//        }
        System.out.println(map.get("400"));
        System.out.println(map.get("399"));

    }

    public static void testReadAllPage() {
        String fileName = MVDBFILE;
        // String fileName = "D:/h2data_1/test8.mv.db";
        MVStore s = MVStore.open(fileName);
        MVMap map = s.openMap("data");

        Page rootPage = map.getRootPage();

        LinkedList<Page> queue = new LinkedList<>();
        queue.add(rootPage);

        int level = 1;
        while (!queue.isEmpty()) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Page tmpPage = queue.pollFirst();
                // System.out.println("level : " + level + " : " + tmpPage);
                System.out.println("level : " + level);
                for (int j = 0; j < tmpPage.getKeyCount(); j++) {
                    System.out.print(tmpPage.getKey(j) + " ");
                }
                System.out.println();
                if (!tmpPage.isLeaf()) {
                    for (int j = 0; j < tmpPage.getKeyCount() + 1; j++) {
                        queue.add(tmpPage.getChildPage(j));
                    }
                }

            }
            level++;
        }

    }

    public static void main(String[] args) {
        testOpenMVMap();
        //testReadMVMap();
        testReadAllPage();
    }
}

