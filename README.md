# LazyClicker

Android 鼠标连点工具。它直读物理鼠标左键的 evdev 输入，按住期间按设定的 CPS 重复输出鼠标左键。注入由 Shizuku 的 UserService 以 shell（uid 2000）身份执行，App 本身不需要 root。

- 应用名：LazyClicker
- 包名：`com.m3e.clicker`
- 版本：2.0.2（versionCode 16）
- minSdk 26 / targetSdk 35 / compileSdk 35
- 界面：Jetpack Compose + Material 3

---

## 1. 功能

| 能力 | 实现 |
| --- | --- |
| 检测 | evdev 直读 `BTN_LEFT`(272)；能力位图读不出时监听全部节点，靠真实事件反推 |
| 输出 | `MotionEvent` + `buttonState = BUTTON_PRIMARY` |
| 模式 | 默认（区间随机 CPS）、DBC（一次基础点击扩展成 2/3/4 连点）、DC（限时高频 burst） |
| 总开关 | 主界面开关，一键启用或禁用；可用键盘键或鼠标侧键绑定切换 |
| 横屏 | 设置常驻右侧占 50%，主界面占 50%，不再滑出抽屉；竖屏仍为左侧抽屉 |
| 前台服务 | 常驻通知，可单独启停 |
| 无障碍兜底 | 无 Shizuku 时用 `dispatchGesture` 模拟触摸（无法区分左右键） |
| 自检 | 全屏 TEST，每 20 ms 刷新 CPS；点击在落点复刻 BASpark 的波纹与火花特效 |
| 语言 | 中文 / English，切换入口在设置页底部 |

---

## 2. 界面

- 主界面顶部是总开关，替代原来的「输出左键」标题；开关下方一行显示当前绑定的键位，点击可重新绑定。
- 接着是点击模式三选一（默认 / DBC / DC），CPS 与附加参数随模式变化。
- 页面底部固定一个 TEST 按钮。
- 竖屏时设置从左侧滑出（遮罩 + 滑入动画）；横屏时设置固定在右半屏，不提供菜单按钮。
- 设置分组依次为：高级、后台、诊断、关于，最下方是语言切换。
- 界面不显示常驻的「就绪」状态，也没有右上角状态点。
- 配色跟随系统深浅色与动态取色，不做固定配色。

### 2.1 开关与键位绑定

开关直接对应服务端的 `enabled` 状态。切换时不销毁监视线程，再次打开无需重新扫描设备；绑定键位由服务进程内的 evdev 读取线程识别，按下沿触发一次切换，抬起与重复事件忽略。

绑定流程：点击「绑定键位」后服务进入捕获模式，捕获下一个键盘按键或鼠标中键/侧键，确认后写入参数并持久化；捕获期间按键不会触发开关。

---

## 3. 三种点击模式

### 3.1 默认（Default）

CPS 区间 1–25，每次输出一次点击。间隔 = `1000 / random(min, max)`，按绝对时间排程（见第 8 节）。

### 3.2 DBC（Double Butterfly Click）

CPS 区间 1–16。基础节奏与默认模式相同，区别在于**每一次基础点击都可能扩展为多次点击**，扩展出来的点击之间只间隔若干毫秒，因此实际输出次数高于设定的基础 CPS。

参数：

| 参数 | 范围 | 说明 |
| --- | --- | --- |
| 多倍点概率 | 0–100%，可选区间 | 每次基础点击先按此概率决定是否进入多倍判定 |
| 二倍 / 三倍 / 四倍点概率 | 各 0–100% | 进入多倍判定后，三个概率各自独立随机 |
| 多倍点间隔 | 0–10 ms，区间可调 | 一次物理点击扩展出的多次输出之间的间隔，每次在区间内随机取值 |

判定流程：

```
基础点击 → 按 多倍点概率（区间内随机）判定
  ├─ 未触发 → 输出 1 次
  └─ 触发   → 对三个概率分别随机（概率为 0 的直接跳过）
        ├─ 命中两个及以上 → 在命中的倍数里随机取一个
        ├─ 恰好命中一个   → 用该倍数
        └─ 一个都没命中   → 固定输出二倍点
```

举例：二倍设为 0%，三倍与四倍各 50%，两者都没命中时仍输出二倍点；只有三倍命中则输出三倍点。

### 3.3 DC（Drag Click）

CPS 范围 15–150，另有一个 0.1–5.0 秒的持续时间区间，每一轮在区间内随机取值。

按下左键后开始输出，持续到设定时间结束即停止；此时必须**松开再重新按下**才会开始下一轮，按住不放不会继续。松手会清除锁定。

---

## 4. 依赖与工具链

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.2.20 |
| Compose runtime / BOM | 1.8.1 / 1.8.2 |
| material3 | 1.4.0 |
| Shizuku api / provider | 13.1.5 |

Material 3 Expressive 组件在 material3 1.4.0 中仍为 internal，1.5.x 需要 compileSdk 37 与 AGP 9.1.0，因此界面使用 1.4.0 的公开 API 自行实现。

---

## 5. 架构与接口

```
应用进程  com.m3e.clicker
├── MainActivity
├── ClickerBridge            授权、绑定、轮询、参数下发、异常看门狗
├── ClickerForegroundService 前台服务与常驻通知
├── ClickerAccessibilityService
├── SystemAccess
└── ui/                      主界面、双页、设置、TEST

                 AIDL  IClickerService（service version 6）

Shizuku UserService  uid = 2000
├── ClickerService           状态机、模式循环
├── EvdevExpert              /dev/input 枚举与读取
└── InputInjector            MotionEvent 注入
```

AIDL 方法：

| 方法 | id |
| --- | --- |
| `destroy()` | 16777114 |
| `configureJson(String)` | 1 |
| `startMouseTrigger()` / `stopMouseTrigger()` | 2 / 3 |
| `startManualBurst()` / `stopManualBurst()` | 4 / 5 |
| `clickOnce()` | 6 |
| `resetCursor()` | 7 |
| `ping()` | 8 |
| `statusJson()` | 9 |
| `beginKeyCapture()` / `endKeyCapture()` | 10 / 11 |

`configureJson` 字段：

| 字段 | 含义 |
| --- | --- |
| `enabled` | 总开关 |
| `toggleKey` | 绑定键位的 evdev 键码，-1 未绑定 |
| `method` | 0=默认，1=DBC，2=DC |
| `minCps` / `maxCps` | 左键基础 CPS 区间 |
| `multiMin` / `multiMax` | 多倍点概率区间 |
| `doublePct` / `triplePct` / `quadPct` | 二倍 / 三倍 / 四倍点概率 |
| `gapMin` / `gapMax` | 多倍点间隔区间 |
| `dcCps` | DC 的 CPS |
| `dcDurMinMs` / `dcDurMaxMs` | DC 持续时间区间 |
| `followCursor` / `fixedX` / `fixedY` | 坐标模式 |
| `sensitivity` / `acceleration` / `touchCompat` | 光标与注入选项 |


AIDL 方法必须全部分配 id 或全部不分配。方法签名变化需同步提升 `SERVICE_VERSION`，否则 Shizuku 会复用旧服务进程。

---

## 6. 输入检测

1. 遍历 `/dev/input/event*`，读取 `EVIOCGBIT` 能力位图与设备名，排除带 `ABS_MT` 的触摸屏。
2. 逐字节解析 `struct input_event`：64 位每事件 24 字节，32 位 16 字节，字段为 `timeval`、`type`、`code`、`value`。
3. 只处理三个按键码 `BTN_LEFT` / `BTN_RIGHT` 的按下与抬起，以及 `EV_REL` 的 `REL_X` / `REL_Y`、`EV_ABS` 的绝对坐标。
4. 位图读不出时不做过滤，监听全部节点，靠真实按键事件确认设备类型。
5. 直读失败时回退到 `getevent -l`。
6. 按住期间设备从列表消失（拔出、断连）立即释放；读取线程结束同样释放。

---

## 7. 注入

- `InputManager#getInstance()` 在 UserService 进程不可用：内部依赖 `ActivityThread.currentApplication()`，服务进程没有 Application 实例。使用 `InputManagerGlobal#getInstance()`，`injectInputEvent(event, 2)`。
- 输出鼠标按键使用带 `buttonState` 的隐藏 `obtain` 重载，配 `PointerProperties` / `PointerCoords` 数组；按下时 `actionButton` 与 `buttonState` 均为对应键，抬起时 `buttonState=0`。
- 数组 Class 用 `Array.newInstance(Class.forName("android.view.MotionEvent$PointerProperties"), 0).javaClass`，不能写 `Class.forName("...$PointerProperties[]")`（非法 JVM 类名）。
- 坐标：`REL` 设备没有绝对位置，启动监听时先把系统光标移到屏幕中心，之后按增量累加并裁剪到屏幕。
- 相对位移换算：原始输入为 `delta × sensitivity`；速度加速为 `delta × (1 + min(|delta|,20) × 0.09) × sensitivity`。`sensitivity` 取 0–3，为 0 时不移动光标。

---

## 8. 连点循环

```
start = now
nextAt = start
while clicking && shouldKeepClicking():
    if now - start > 120s: 停止
    按当前模式输出 1 次或多次
    nextAt += interval
    若 nextAt <= now: nextAt = now + 1
    sleep(nextAt - now)
```

- 间隔按目标时间戳推进，注入耗时（每次两个事件 + `WAIT_FOR_FINISH`）不累积到周期。
- 服务端用 1 秒滑动窗口统计真实输出次数，`measuredCps` 随状态返回。
- 触发条件：`enabled && mousePressed`；关闭开关只停止循环，监视线程保留。
- 单次按住最长 120 秒，超时自动停止；DC 另有自身持续时间。
- 心跳 `ping()` 不续期任何计时。
- 应用侧看门狗：`clicking` 为真但 `trigger`、`manual` 都为假，连续两次轮询后强制停止。

---

## 9. 后台运行

`ClickerForegroundService` 以前台服务类型 `specialUse` 常驻，通知带停止动作。设置页可跳转通知权限、电池优化白名单、无障碍开关。

`ClickerAccessibilityService` 通过 `dispatchGesture` 提供备用点击通道，只能模拟屏幕触摸，无法表达鼠标按键。

---

## 10. 状态字段

`statusJson()` 返回：`ready`、`uid`、`trigger`、`clicking`、`manual`、`pressed`、`enabled`、`switchSeq`、`toggleKey`、`capturing`、`capturedKey`、`method`、`dcLocked`、`inject`、`injectMode`、`injectImpl`、`injectError`、`minCps`、`maxCps`、`dcCps`、`dcDurMinMs`、`dcDurMaxMs`、`multiMin`、`multiMax`、`doublePct`、`triplePct`、`quadPct`、`gapMin`、`gapMax`、`cursorX`、`cursorY`、`width`、`height`、`followCursor`、`sensitivity`、`acceleration`、`clicks`、`measuredCps`、`pressedDevice`、`lastKey`、`devices`、`nodeDetails`、`scanError`、`err`。

---

## 11. 构建

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

gradle :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

发布构建关闭了 `lintVitalAnalyzeRelease`（`lint { checkReleaseBuilds = false; abortOnError = false }`）。

---

## 12. 已知限制

- 必须运行 Shizuku；无 Shizuku 时只能用无障碍手势，无法区分左右键。
- `REL` 设备的光标位置是推算值，系统的指针加速只能近似匹配。
- 部分 ROM 对 `injectInputEvent` 响应不同，需要开启触摸兼容。
- 部分设备的能力位图不可读，依赖真实事件反推设备类型。
- 触摸兼容模式输出的是触摸事件，多倍点可能被系统合并。

---

## 13. 目录结构

```
M3EClicker/
├── app/src/main/
│   ├── aidl/com/m3e/clicker/IClickerService.aidl
│   ├── java/com/m3e/clicker/
│   │   ├── MainActivity.kt
│   │   ├── ClickerBridge.kt
│   │   ├── ClickerService.kt
│   │   ├── InputInjector.kt
│   │   ├── EvdevExpert.kt
│   │   ├── ClickerForegroundService.kt
│   │   ├── ClickerAccessibilityService.kt
│   │   ├── SystemAccess.kt
│   │   ├── Shell.kt
│   │   └── ui/
│   │       ├── Theme.kt
│   │       ├── ClickerScreen.kt
│   │       └── Lang.kt
│   ├── res/mipmap-*/ic_launcher.png
│   ├── res/mipmap-*/ic_launcher_round.png
│   └── AndroidManifest.xml
├── keystore/m3e.jks（不随仓库分发）
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 14. 参考

- Shizuku API：<https://github.com/RikkaApps/Shizuku-API>
- KeyMapper：<https://github.com/keymapperorg/KeyMapper>
- ImageToolbox（界面参考）：<https://github.com/T8RIN/ImageToolbox>
- Jetpack Compose：<https://developer.android.com/jetpack/compose>
- Material 3：<https://m3.material.io>

---

## 15. 声明

本项目仅用于自动化测试、辅助操作等合法场景。使用者需自行确保使用行为符合目标应用的服务条款与当地法律。
