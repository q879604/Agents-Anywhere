package com.agentsanywhere.app.ui.screens.sessiondetail

import com.agentsanywhere.app.feature.sessiondetail.MessageAuthor
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessage
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessageKind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMessagesAutoFollowTest {
    @Test
    fun readingOnlyResumesFollowingAfterExplicitReturn() {
        val state = TimelineReadingState()
        assertTrue(state.following)
        state.gesture(24f)
        assertFalse(state.following)
        state.invalidate()
        assertFalse(state.following)
        state.release()
        assertTrue(state.following)
    }

    @Test
    fun expansionSurvivesGroupAppendSplitAndMerge() {
        val state = TimelineReadingState()
        state.toggle("first", "header:first")
        assertTrue(state.groupExpanded("tools", listOf("first", "second")))
        state.toggleGroup("tools", listOf("first", "second"), "group")
        assertFalse(state.groupExpanded("tools", listOf("first", "second", "third")))
        state.toggleGroup("tools", listOf("first", "second", "third"), "group")
        assertTrue(state.groupExpanded("tools", listOf("second", "third")))
        assertTrue(state.groupExpanded("tools", listOf("older", "first", "second", "third")))
        assertFalse(state.groupExpanded("reconnect", listOf("other")))
    }
}


    @Test
    fun singleToolRowKeepsKeyWhenNewCallJoinsRun() {
        val reasoning = TimelineMessage(
            id = "reasoning-1",
            author = MessageAuthor.Agent,
            text = "thinking",
            kind = TimelineMessageKind.Reasoning,
        )
        val call = TimelineMessage(
            id = "tool-2",
            author = MessageAuthor.Tool,
            text = "read_image",
            kind = TimelineMessageKind.ToolCall,
        )
        val before = groupTimelineMessages(listOf(reasoning)).single()
        val after = groupTimelineMessages(listOf(reasoning, call)).single()
        assertEquals(before.key, after.key)
    }
