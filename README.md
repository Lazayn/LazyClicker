# LazyClicker
> （注：部分内容由豆包 AI 生成）

Android 连点工具。它读取物理鼠标左键的原始输入，按住期间按可调的最小/最大 CPS 重复输出按键事件或鼠标左键。输入检测直接读 `/dev/input`，事件注入通过 Shizuku 的 UserService 以 shell（uid 2000）身份执行，因此 App 本身不需要 root。

- 应用名：LazyClicker
- 包名：`com.m3e.clicker`
- 版本：1.4.0（versionCode 5）
- minSdk 26 / targetSdk 35 / compileSdk 35
- 界面：Jetpack Compose + Material 3

---

## 1. 功能范围

| 能力 | 实现方式 |
| --- | --- |
| 鼠标左键检测 | 直读 evdev，解析 `BTN_LEFT` 事件；能力位图读不出时监听全部输入节点，用真实事件反推鼠标 |
| 按住重复输出 | 服务端循环，间隔取 `1000 / random(minCps, maxCps)`，两次输出之间的节奏按绝对时间对齐 |
| 输出类型 | 默认 `KeyEvent` 键值（可在常用键、自定义 keyCode、实体键盘扫描三种方式中选择）；可选鼠标左键（`MotionEvent` + `buttonState`） |
| CPS 范围 | 1–30，可设区间；区间相等即固定频率 |
| 光标跟随 | 鼠标相对位移换算为系统光标坐标，灵敏度 0–3 倍，可选“速度加速”或“原始输入”模型；也可固定坐标输出 |
| 运行环境 | 前台服务 + 常驻通知；无 Shizuku 时可退化为无障碍手势，但该通道无法表达鼠标按键 |
| 自检 | 全屏 TEST 页，每 100 ms 刷新一次实时 CPS，并显示点击位置 |
| 语言 | 中文 / English，切换入口在设置页底部 |

---

## 2. 依赖与工具链

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.2.20 |
| Compose BOM / runtime | 1.8.2 / 1.8.1 |
| material3 | 1.4.0 |
| Shizuku api / provider | 13.1.5 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |

Material 3 Expressive 组件（`MaterialExpressiveTheme`、`MotionScheme`、`ButtonGroup`、`FloatingToolbar`）在 material3 1.4.0 中仍为 internal，而 1.5.x 要求 compileSdk 37 与 AGP 9.1.0，因此界面用 1.4.0 的公开 API 自行实现：大字号层级、大圆角、弹簧动效、按下形变、进度柱。

---

## 3. 整体结构

```
应用进程  com.m3e.clicker
├── MainActivity               Compose 入口
├── ClickerBridge              授权、绑定、状态轮询、参数下发、异常看门狗
├── ClickerForegroundService   前台服务与常驻通知
├── ClickerAccessibilityService 无障碍手势（备用通道）
├── SystemAccess               通知 / 电池优化 / 无障碍的检查与跳转
└── ui/                        主界面、设置抽屉、TEST 页、键值目录

                    AIDL  IClickerService（service version 5）

Shizuku UserService  uid = 2000（shell）
├── ClickerService             状态机、参数、连点循环
├── EvdevExpert                /dev/input 枚举与读取
└── InputInjector              KeyEvent / MotionEvent 注入
```

App 不直接持有注入权限，只通过 AIDL 调用服务。服务由 Shizuku 以 `daemon(true)`、`processNameSuffix("clicker")` 启动，进程独立于应用，应用被杀后仍可存活。

### AIDL 接口

| 方法 | id | 说明 |
| --- | --- | --- |
| `destroy()` | 16777114 | 结束服务进程 |
| `configure(minCps, maxCps, outputMode, keyCode, followCursor, fixedX, fixedY, sensitivity, touchCompat, acceleration)` | 1 | 下发全部参数 |
| `startMouseTrigger()` / `stopMouseTrigger()` | 2 / 3 | 开始 / 停止监听鼠标左键 |
| `startManualBurst()` / `stopManualBurst()` | 4 / 5 | 手动连点，3 秒绝对超时 |
| `clickOnce()` | 6 | 单次输出 |
| `resetCursor()` | 7 | 光标归位到屏幕中心 |
| `ping()` | 8 | 心跳 |
| `statusJson()` | 9 | 返回完整状态 |

AIDL 的每个方法必须全部分配 id 或全部不分配，混用会导致 `destroy()` 找不到。方法签名一旦变化必须同步提升 `SERVICE_VERSION`，否则 Shizuku 会复用仍然存在的旧服务进程。

---

## 4. 输入检测实现

### 4.1 设备枚举

遍历 `/dev/input/event*`，用 `EVIOCGBIT` 读取 `EV_KEY`、`EV_REL`、`EV_ABS` 能力位图，判断节点是否支持 `BTN_LEFT`(272) 或鼠标类按键；同时读取设备名。

早期版本在能力位图读取失败时会过滤掉全部节点，导致扫描结果为“13 个节点，没有鼠标”。现在的策略是：位图读不到时不做过滤，监听所有节点，靠真实收到的 `BTN_LEFT` 事件把设备标记为鼠标。

### 4.2 事件解析

每个节点用 `FileInputStream` 顺序读取 `struct input_event`，按位宽解析：64 位每事件 24 字节，32 位每事件 16 字节，字段为 `timeval`、`type`、`code`、`value`。只处理三类：

- `EV_KEY` + `BTN_LEFT`：`value=1` 为按下，`0` 为抬起，`2` 为重复；
- `EV_REL` + `REL_X` / `REL_Y`：鼠标相对位移；
- `EV_ABS` + `ABS_X` / `ABS_Y`：数位板等绝对坐标设备，按 `ABS_X/ABS_Y` 的信息归一化。

直读失败时回退到 `getevent -l`，解析其文本输出，仅作为兜底。

### 4.3 生命周期

监视线程周期性重新扫描设备列表。按住期间如果当前设备从列表里消失（拔出、断连、休眠），立即触发释放，避免按下状态残留。读取线程退出时同样执行释放。

---

## 5. 注入实现

注入在 UserService 进程内完成。`InputManager#getInstance()` 在该进程不可用：它内部依赖 `ActivityThread.currentApplication()`，而服务进程没有 Application 实例，调用必然 NPE。可用的是 `InputManagerGlobal#getInstance()`，它通过 `ServiceManager.getService("input")` 取 Binder，`injectInputEvent(event, mode)` 的 mode 传 2（`WAIT_FOR_FINISH`）。

### 5.1 键值

用 `KeyEvent(downTime, eventTime, action, code, repeat)` 构造 DOWN / UP 两个事件依次注入。Android 没有表示鼠标按键的 keyCode，这是默认输出键值模式的原因。

### 5.2 鼠标左键

`MotionEvent` 需要一个带 `buttonState` 的构造路径。使用隐藏的 pointer-based `obtain` 重载，配合 `PointerProperties` / `PointerCoords` 数组，按下时 `actionButton = BUTTON_PRIMARY`、`buttonState = BUTTON_PRIMARY`，抬起时 `buttonState = 0`。

取数组类型时不能用 `Class.forName("android.view.MotionEvent$PointerProperties[]")`，这是非法 JVM 类名，会抛 `ClassNotFoundException: Invalid name`，早期版本因此把整条注入通道标记为不可用。正确做法是 `Array.newInstance(Class.forName("android.view.MotionEvent$PointerProperties"), 0).javaClass`。该步骤失败现在只降级鼠标模式，不影响键值注入。

### 5.3 坐标

`REL` 设备没有绝对坐标，服务启动触发时先把系统光标移到屏幕中心，使内部推算坐标与真实光标对齐，之后按增量累加并裁剪到屏幕范围。

Android 的光标本身带系统级加速，不是 1:1 的原始输入，所以换算提供两种模型：

- 原始输入：`out = delta × sensitivity`；
- 速度加速：`gain = 1 + min(|delta|, 20) × 0.09`，`out = delta × gain × sensitivity`，大位移获得更高倍率，近似系统加速曲线。

`sensitivity` 取值 0–3，为 0 时相对位移不产生任何移动。

---

## 6. 连点循环

```
loopStart = now
nextAt = loopStart
while clicking && shouldKeepClicking():
    if now - loopStart > 120s: 停止
    outputOnce()
    统计最近 1 秒输出次数 → measuredCps
    nextAt += 1000 / cps
    若 nextAt <= now: nextAt = now + 1
    sleep(nextAt - now)
```

要点：

1. **绝对时间排程**。早期写法是“注入完成后 sleep(1000/cps)”，注入本身（两个事件 + `WAIT_FOR_FINISH`）的耗时被叠加进周期，设定 30 时实测只有 21–22。改成按目标时间戳推进后，注入耗时不累积，实际速率贴近设定值。
2. **CPS 取整**。`interval = 1000 / cps` 向下取整并至少 1 ms，min 与 max 顺序无关。
3. **真实速率上报**。服务端用 1 秒滑动窗口记录每次输出的时间戳，`measuredCps` 随 `statusJson` 返回，界面和 TEST 显示的都是实际输出次数，不是配置值。

### 6.1 触发条件

开始连点只有两个入口：真实的 `BTN_LEFT` 按下，以及显式的手动连点调用。`shouldKeepClicking()` 的判定是：手动模式且未超过绝对 deadline，或者 `triggerEnabled && mousePressed`。

1.4.0 修掉的一个问题是手动状态无法结束：心跳 `ping()` 每次轮询都会把 manual deadline 往后推，而判定只看 `manualActive`，于是界面持续轮询就等于无限续命；叠加 daemon 服务跨应用重启存活、鼠标拔出后按下标志卡住，表现为“进入应用、没连鼠标、没点开始也在一直连点”。当前的处理：

- 心跳不再续期任何计时；
- 手动连点改为 3 秒绝对超时；
- 绑定成功后先清除一次服务里残留的 trigger / manual 状态；
- 按住期间设备消失立即释放；
- 单次按住超过 120 秒强制停止；
- 应用侧看门狗：`clicking` 为真但 `trigger` 与 `manual` 都为假，连续两次轮询后强制停止。

---

## 7. 后台运行

`ClickerForegroundService` 以前台服务类型 `specialUse` 常驻，通知带停止动作。设置页可跳转通知权限、电池优化白名单、无障碍开关。

`ClickerAccessibilityService` 通过 `dispatchGesture` 提供备用点击通道，用于 Shizuku 不可用的场景；该通道只能模拟屏幕触摸，无法表达鼠标按键。

---

## 8. 界面

- **主界面**：左上角菜单、应用名、状态点；`输出`（键值 / 左键 + 当前键值按钮）、CPS 大字与区间滑块、状态行、开始 / 停止按钮、TEST 按钮。
- **设置**：从左侧滑出，含“高级”（灵敏度、加速 / 原始、触摸兼容、跟随 / 固定坐标）、“后台”（Shizuku 入口、通知、电池、无障碍、前台服务）、“诊断”（注入实现、输出类型、最后输入、光标、错误、节点列表）、“关于”（使用的代码与 GitHub 链接）；最下方是中英文切换。
- **TEST**：全屏页，每 100 ms 刷新一次 CPS，显示点击位置，20 格历史柱，左上角退出。未连接 Shizuku 时测量手动点击速率，连接且正在连点时显示服务端真实输出速率。

界面文案为内嵌中英双语，通过 `CompositionLocal` 传递当前语言，状态持久化在 `lazy_clicker_prefs`，切换即时生效。

---

## 9. 状态字段

`statusJson()` 返回的字段：

| 字段 | 含义 |
| --- | --- |
| `ready` | 服务是否就绪 |
| `uid` | 服务进程 uid，正常为 2000 |
| `trigger` / `clicking` / `manual` / `pressed` | 监听中 / 正在连点 / 手动模式 / 左键按下 |
| `inject` / `injectMode` / `injectImpl` | 注入是否可用 / 当前注入模式 / 使用的管理器 |
| `outputMode` / `keyCode` | 输出类型与键值 |
| `minCps` / `maxCps` | 当前频率区间 |
| `cursorX` / `cursorY` / `width` / `height` | 推算光标位置与屏幕尺寸 |
| `followCursor` / `sensitivity` / `acceleration` | 坐标模型参数 |
| `clicks` / `measuredCps` | 累计输出次数 / 最近 1 秒真实速率 |
| `pressedDevice` | 当前按下的设备路径 |
| `devices` / `nodeDetails` / `scanError` | 设备列表、节点明细、扫描错误 |
| `err` | 最近一次错误 |

---

## 10. 构建

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

gradle :app:assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release.apk`。

签名配置在 `app/build.gradle.kts` 中指向 `keystore/m3e.jks`，该文件不随仓库分发。发布构建关闭了 `lintVitalAnalyzeRelease`（`lint { checkReleaseBuilds = false; abortOnError = false }`），因为在当前工具链下该任务会崩溃。

---

## 11. 已知限制

- 必须有运行中的 Shizuku；没有 Shizuku 时只能使用无障碍手势，无法输出鼠标按键。
- Android 输入模型没有鼠标按键的 keyCode，输出键值模式实际上输出的是普通按键。
- `REL` 设备没有绝对坐标，光标位置是推算值；系统指针加速与实现中的加速模型只能近似匹配。
- 不同 ROM 对 `injectInputEvent` 的响应不同，部分系统需要开启“触摸兼容”。
- 部分设备的能力位图不可读，此时依赖真实事件反推设备类型。

---

## 12. 目录结构

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
│   │       ├── KeyCatalog.kt
│   │       └── Lang.kt
│   ├── res/mipmap-*/ic_launcher.png
│   ├── res/mipmap-*/ic_launcher_round.png
│   └── AndroidManifest.xml
├── keystore/m3e.jks（不随仓库分发）
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 13. 参考

- Shizuku API：<https://github.com/RikkaApps/Shizuku-API>
- KeyMapper：<https://github.com/keymapperorg/KeyMapper>
- ImageToolbox（界面参考）：<https://github.com/T8RIN/ImageToolbox>
- Jetpack Compose：<https://developer.android.com/jetpack/compose>
- Material 3：<https://m3.material.io>

---

## 14. 声明

本项目仅用于自动化测试、辅助操作等合法场景。使用者需自行确保使用行为符合目标应用的服务条款与当地法律。