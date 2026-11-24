package com.masroufi.app.ui.audit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.MasroufiTheme
import java.util.Locale

/**
 * The audit harness of Phase 12 (10-PERFORMANCE "Checklist before shipping a screen"): renders one composable the way the
 * hardest user sees it (font scale 2.0, right-to-left, animations off) and lists what the machine can prove is wrong.
 *
 * ```
 * @get:Rule val rule = createComposeRule()
 *
 * @Test fun myScreenPassesTheAudit() {
 *     rule.audit { MyScreenContent(sampleState, actions) }.assertNone()
 * }
 * ```
 *
 * Rules, each reported once per node with a description of it:
 *  - [AuditRule.TOUCH_TARGET]: a node with a click action whose touch bounds (the minimum-interactive expansion included)
 *    are under [AuditConfig.minTouchDp] in either direction.
 *  - [AuditRule.LABEL]: a node with a click action that has no text, content description or typed text for TalkBack.
 *  - [AuditRule.OVERFLOW]: text whose layout overflows its box (single line cut off, or more lines than the box holds).
 *    Text that is meant to be cut (an ellipsis on a name) goes in [AuditConfig.allowOverflow] by a part of its text.
 *
 * The content goes inside [MasroufiTheme]; wrap it in whatever providers the screen needs (state, `LocalHideAmounts`...)
 * inside the lambda. The clock is stopped while the tree is built, so an infinite animation cannot keep the test busy.
 * What it cannot prove (the pressed state on touch-down, skeleton shape, undo, reading order) stays a manual read of the
 * code. Dialogs and sheets are separate roots and are checked too.
 */
enum class AuditRule { TOUCH_TARGET, LABEL, OVERFLOW }

data class AuditFinding(val rule: AuditRule, val node: String, val detail: String) {
    override fun toString() = "$rule: $detail -> $node"
}

data class AuditConfig(
    val fontScale: Float = 2f,
    val rtl: Boolean = true,
    val minTouchDp: Float = 48f,
    val allowOverflow: List<String> = emptyList(),
)

/** Fails with every finding listed, one per line. */
fun List<AuditFinding>.assertNone() {
    if (isNotEmpty()) throw AssertionError("${size} audit finding(s):\n" + joinToString("\n") { "  $it" })
}

fun ComposeContentTestRule.audit(config: AuditConfig = AuditConfig(), content: @Composable () -> Unit): List<AuditFinding> {
    mainClock.autoAdvance = false
    try {
        setContent {
            MasroufiTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, config.fontScale),
                    LocalLayoutDirection provides if (config.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalReduceMotion provides true,
                ) { content() }
            }
        }
        mainClock.advanceTimeBy(1_000)
        val roots = onAllNodes(isRoot()).fetchSemanticsNodes()
        return roots.flatMap { auditTree(it, config) }
    } finally {
        mainClock.autoAdvance = true
    }
}

private fun auditTree(root: SemanticsNode, config: AuditConfig): List<AuditFinding> {
    val out = mutableListOf<AuditFinding>()
    fun visit(node: SemanticsNode) {
        val cfg = node.config
        if (!cfg.contains(SemanticsProperties.InvisibleToUser)) {
            if (cfg.contains(SemanticsActions.OnClick)) {
                val d = node.layoutInfo.density.density
                val b = node.touchBoundsInRoot
                val w = b.width / d
                val h = b.height / d
                // Half a pixel of rounding is not a failure.
                if (w < config.minTouchDp - 0.5f || h < config.minTouchDp - 0.5f) {
                    out += AuditFinding(AuditRule.TOUCH_TARGET, describe(node), "touch target %.1f x %.1f dp, under %.0f".format(Locale.US, w, h, config.minTouchDp))
                }
                if (label(node).isBlank()) out += AuditFinding(AuditRule.LABEL, describe(node), "clickable with no text or description for TalkBack")
            }
            val layouts = mutableListOf<TextLayoutResult>()
            cfg.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            if (layouts.any { it.hasVisualOverflow } && config.allowOverflow.none { text(node).contains(it) }) {
                out += AuditFinding(AuditRule.OVERFLOW, describe(node), "text overflows its box")
            }
        }
        node.children.forEach(::visit)
    }
    visit(root)
    return out
}

private fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

private fun label(node: SemanticsNode): String =
    listOf(text(node), node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(), node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty())
        .joinToString(" ")

private fun describe(node: SemanticsNode): String {
    val d = node.layoutInfo.density.density
    val b = node.boundsInRoot
    val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
    return "node ${node.id} text='${text(node)}' desc='${desc.orEmpty()}' role=${node.config.getOrNull(SemanticsProperties.Role)} " +
        "at (%.0f, %.0f) size %.1f x %.1f dp".format(Locale.US, b.left / d, b.top / d, b.width / d, b.height / d)
}
