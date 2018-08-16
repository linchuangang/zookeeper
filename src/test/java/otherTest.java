/**
 * Created by Administrator on 2018/8/15.
 */
public class otherTest {
    public static void main(String[] args) {
        String lockPaths = "2wsnz";
        System.out.println(lockPaths.substring(1));
        System.out.println(lockPaths.substring(1, 2));


        System.out.println("result="+digui(20));
    }

    public static Integer digui(int count) {
        System.out.println(count);
        count=count-1;
        if(count<10){
            return 1000;
        }else {
            return digui(count);
        }
    }
}
