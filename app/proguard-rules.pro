# 远端 UserService 进程按类名反射加载，全部保留
-keep class com.m3e.clicker.** { *; }
-keep interface com.m3e.clicker.** { *; }

# Shizuku
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**
-dontwarn rikka.shizuku.**

# AIDL
-keep class android.os.IInterface { *; }
