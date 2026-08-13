package DSA;
import java.util.Scanner;
import java.util.HashMap;

public class TwoSum{

   public static void SortColours(int nums[]){
      HashMap<Integer,Integer> mp=new HashMap<>();
      for(int i=0;i<nums.length;i++){
        mp.put(nums[i],mp.getOrDefault(nums[i],0)+1);
      }
      int countOfzero=mp.get(0);
      int countOfone=mp.get(1);

      for(int i=0;i<countOfzero;i++){
         nums[i]=0;
      }
      for(int i=countOfzero;i<countOfzero+countOfone;i++){
         nums[i]=1;
      }
      for(int i=countOfzero+countOfone;i<nums.length;i++){
         nums[i]=2;
      }
   }

   public static void main(String args[]){

      int n;
      Scanner sc=new Scanner(System.in);
      
      n=sc.nextInt();
      int nums[]=new int[n];
      for(int i=0;i<n;i++){
         int val=sc.nextInt();
         nums[i]=val;
      }
      SortColours(nums);
      System.out.println("Sorted Colors");
      for(int ele : nums){
         System.out.print(ele+" ");
      }
   }
}