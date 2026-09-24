package com.nuo.beanforge.ui;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * 界面配色。
 *
 * <p><b>刻意不 import 任何 IDEA API。</b> 原因是两条:</p>
 * <ul>
 *   <li>{@code UIUtil.getErrorForeground()} 在 IDEA 2020.2 里叫
 *       {@code com.intellij.util.ui.UIUtil}, 之后才搬到 {@code com.intellij.ui.UIUtil}
 *       —— 我们用 2020.2 的 SDK 编、装进新版 IDEA 跑, 引用任一侧都会在另一侧翻车。</li>
 *   <li>更要紧的是: 这个类一碰 IDE API, 依赖它的 {@link TypeCellEditor} 就没法在离线测试里
 *       用<b>真 JTable</b> 驱动了 —— 而"编辑器实例被复用导致候选残留"这类 bug,
 *       恰恰只有真组件能测出来。</li>
 * </ul>
 *
 * <p>所以优先取 Laf 注册的错误色 (在 IDEA 里就是当前主题的色), 取不到再退回一个
 * 明暗主题下都还看得清的红色。</p>
 */
final class UiColors {

    /**
     * 非法输入的错误红。
     */
    static final Color ERROR = resolveError();

    private static Color resolveError() {
        Color fromLaf = UIManager.getColor("Component.errorFocusColor");
        return fromLaf != null ? fromLaf : new Color(0xE5, 0x5A, 0x5A);
    }

    private UiColors() {
    }
}
