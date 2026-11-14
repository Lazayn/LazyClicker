package com.m3e.clicker;

/**
 * 运行在 Shizuku UserService（shell 身份）里的连点服务接口。
 * 注意：destroy() 使用 Shizuku 约定的销毁 transaction id（16777114）。
 */
interface IClickerService {

    void destroy() = 16777114;

    /**
     * 配置参数。
     *
     * @param minCps      最小 CPS（1..30）
     * @param maxCps      最大 CPS（1..30）
     * @param outputMode  输出内容：0=键值(KeyEvent)，1=鼠标左键(MotionEvent)
     * @param keyCode     Android KeyEvent keyCode（outputMode=0 时生效）
     * @param followCursor true=输出发生在鼠标光标处；false=固定坐标
     * @param fixedX      固定坐标 X
     * @param fixedY      固定坐标 Y
     * @param sensitivity 相对位移鼠标的灵敏度系数
     * @param touchCompat true=用触摸事件模拟点击（部分设备鼠标注入无效时使用）
     * @param acceleration true=按系统指针加速近似曲线换算相对位移；false=原始输入（线性）
     */
    void configure(int minCps, int maxCps, int outputMode, int keyCode, boolean followCursor,
            int fixedX, int fixedY, float sensitivity, boolean touchCompat,
            boolean acceleration) = 1;

    /** 开始监听鼠标左键：按住即按 CPS 重复输出，松开立即停止 */
    void startMouseTrigger() = 2;

    /** 停止监听鼠标左键 */
    void stopMouseTrigger() = 3;

    /** 不依赖鼠标的连点（用于自测 / 无鼠标场景），带心跳看门狗 */
    void startManualBurst() = 4;

    /** 停止手动连点 */
    void stopManualBurst() = 5;

    /** 立即输出一次（键值或左键） */
    void clickOnce() = 6;

    /** 把光标(相对坐标基准)复位到屏幕中心 */
    void resetCursor() = 7;

    /** 心跳：手动连点时用于续期看门狗 */
    void ping() = 8;

    /** 运行状态（JSON 字符串） */
    String statusJson() = 9;
}
