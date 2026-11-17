package com.m3e.clicker;

/**
 * 运行在 Shizuku UserService（shell 身份）里的连点服务接口。
 * destroy() 使用 Shizuku 约定的销毁 transaction id（16777114）。
 */
interface IClickerService {

    void destroy() = 16777114;

    /**
     * 下发全部参数（JSON，App 与服务的唯一配置入口）。
     *
     * 字段：
     *  enabled       总开关（界面上的开关，也可由绑定键位切换）
     *  toggleKey     绑定键位的 evdev 键码，-1 表示未绑定
     *  method        点击模式：0=默认，1=DBC，2=DC（仅左键页面使用）
     *  minCps/maxCps 左键默认 / DBC 模式的基础 CPS 区间
     *  multiMin/multiMax 多倍点概率区间（0..100）
     *  doublePct/triplePct/quadPct 二倍/三倍/四倍点概率（0..100）
     *  gapMs         多倍点之间的间隔（0..10 ms）
     *  dcCps         DC 模式 CPS（15..150）
     *  dcDurationMs  DC 模式单次按住持续时间（100..5000 ms）
     *  followCursor  光标跟随 / 固定坐标
     *  fixedX/fixedY 固定坐标
     *  sensitivity   光标灵敏度（0..3）
     *  touchCompat   触摸事件兼容
     *  acceleration  速度加速 / 原始输入
     *  master        全局总开关
     */
    void configureJson(String json) = 1;

    /** 开始监听当前页面对应的鼠标按键：按住即连点，松开立即停止 */
    void startMouseTrigger() = 2;

    /** 停止监听 */
    void stopMouseTrigger() = 3;

    /** 预留：手动连点（3 秒绝对超时） */
    void startManualBurst() = 4;

    /** 停止手动连点 */
    void stopManualBurst() = 5;

    /** 立即输出一次 */
    void clickOnce() = 6;

    /** 把光标基准复位到屏幕中心 */
    void resetCursor() = 7;

    /** 心跳 */
    void ping() = 8;

    /** 运行状态（JSON 字符串） */
    String statusJson() = 9;

    /** 进入绑定键位捕获：服务记录下一个非鼠标按键 */
    void beginKeyCapture() = 10;

    /** 结束绑定键位捕获 */
    void endKeyCapture() = 11;
}
