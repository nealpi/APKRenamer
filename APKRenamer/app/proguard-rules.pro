# ARSCLib 内部会使用反射访问字段，保持不动
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# apksig
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# 一些可能用到的 javax.naming 等 —— Android 里没有，忽略警告
-dontwarn javax.naming.**
-dontwarn java.beans.**
