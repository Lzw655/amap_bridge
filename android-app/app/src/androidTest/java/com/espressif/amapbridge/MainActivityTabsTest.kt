package com.espressif.amapbridge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTabsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchesBetweenAllThreePages() {
        composeRule.onAllNodesWithText("连接调试", useUnmergedTree = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("导航模拟", useUnmergedTree = true)[0].performClick()
        composeRule.onNodeWithText("完整导航模拟器").assertIsDisplayed()
        composeRule.onNodeWithText("发送一次").assertIsDisplayed()
        composeRule.onNodeWithText("自动轮播").assertIsDisplayed()

        composeRule.onAllNodesWithText("ESP 预览", useUnmergedTree = true)[0].performClick()
        composeRule.onNodeWithText("ESP 320×240 预览").assertIsDisplayed()
        composeRule.onNodeWithText("下一路口").assertIsDisplayed()
    }

    @Test
    fun maneuverDropdownContainsProtocolActions() {
        composeRule.onAllNodesWithText("导航模拟", useUnmergedTree = true)[0].performClick()
        composeRule.onNode(hasText("右转 (right)", substring = true)).performClick()
        composeRule.onNodeWithText("直行 · straight").fetchSemanticsNode()
        composeRule.onNodeWithText("未知动作 · unknown").fetchSemanticsNode()
    }
}
