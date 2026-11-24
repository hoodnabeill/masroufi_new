package com.masroufi.app.ui.audit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
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
 *  - [AuditRule.OVERFLOW]: text that does not fit its box (more lines than allowed, a line wider than the box, taller than
 *    the box, or an ellipsis).
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
    // One line: the CI annotation keeps only the first line of a failure message.
    if (isNotEmpty()) throw AssertionError("$size audit finding(s): " + joinToString(" || ") { "$it" })
}

fun ComposeContentTestRule.audit(config: AuditConfig = AuditConfig(), content: @Composable () -> Unit): List<AuditFinding> {
    mainClock.autoAdvance = false
    try {
        setContent {
            MasroufiTheme {
                val base = LocalDensity.current
                val viewConfig = LocalViewConfiguration.current
                // Compose widens every pointer target to 48 dp on its own; switched off so the node's own size is what is measured.
                val exact = object : ViewConfiguration by viewConfig {
                    override val minimumTouchTargetSize: DpSize = DpSize.Zero
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, config.fontScale),
                    LocalLayoutDirection provides if (config.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalReduceMotion provides true,
                    LocalViewConfiguration provides exact,
                ) { content() }
            }
        }
        mainClock.advanceTimeBy(1_000)
        // Clicks and labels as TalkBack sees them (merged); text boxes one by one (unmerged).
        val merged = onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { clickFindings(it, config) }
        val unmerged = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { overflowFindings(it, config) }
        return merged + unmerged
    } finally {
        mainClock.autoAdvance = true
    }
}

private fun walk(node: SemanticsNode, visit: (SemanticsNode) -> Unit) {
    if (node.config.contains(SemanticsProperties.InvisibleToUser)) return
    visit(node)
    node.children.forEach { walk(it, visit) }
}

/**
 * The touch area counts the node's own bounds and the whole layout it sits in, so a `minimumInteractiveComponentSize()`
 * around a smaller control is a 48 dp target (the same approximation Compose's own check makes).
 */
private fun clickFindings(root: SemanticsNode, config: AuditConfig): List<AuditFinding> {
    val out = mutableListOf<AuditFinding>()
    walk(root) { node ->
        if (node.config.contains(SemanticsActions.OnClick)) {
            val d = node.layoutInfo.density.density
            val w = maxOf(node.touchBoundsInRoot.width, node.layoutInfo.width.toFloat()) / d
            val h = maxOf(node.touchBoundsInRoot.height, node.layoutInfo.height.toFloat()) / d
            // Half a pixel of rounding is not a failure.
            if (w < config.minTouchDp - 0.5f || h < config.minTouchDp - 0.5f) {
                out += AuditFinding(AuditRule.TOUCH_TARGET, describe(node), "touch target %.1f x %.1f dp, under %.0f".format(Locale.US, w, h, config.minTouchDp))
            }
            if (label(node).isBlank()) out += AuditFinding(AuditRule.LABEL, describe(node), "clickable with no text or description for TalkBack")
        }
    }
    return out
}

/**
 * The text layout the semantics hand out is laid out again from the box's constraints, so its own `hasVisualOverflow` is
 * true for any text narrower than its box. What is checked instead: more lines than allowed, a line wider than the box,
 * a paragraph taller than the box, or a cut-off line (ellipsis).
 */
private fun overflowFindings(root: SemanticsNode, config: AuditConfig): List<AuditFinding> {
    val out = mutableListOf<AuditFinding>()
    walk(root) { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val r = layouts.firstOrNull() ?: return@walk
        if (config.allowOverflow.any { text(node).contains(it) }) return@walk
        val widest = (0 until r.lineCount).maxOfOrNull { r.getLineRight(it) } ?: 0f
        val why = when {
            r.multiParagraph.didExceedMaxLines -> "more lines than the ${r.layoutInput.maxLines} allowed"
            (0 until r.lineCount).any { r.isLineEllipsized(it) } -> "a line is cut with an ellipsis"
            widest > node.size.width + 1 -> "a line is %.0f px wide in a %d px box".format(Locale.US, widest, node.size.width)
            r.multiParagraph.height > node.size.height + 1 -> "text is %.0f px tall in a %d px box".format(Locale.US, r.multiParagraph.height, node.size.height)
            else -> null
        }
        if (why != null) out += AuditFinding(AuditRule.OVERFLOW, describe(node), "text overflows its box: $why")
    }
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
