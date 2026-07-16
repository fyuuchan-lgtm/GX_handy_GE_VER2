package com.example.yakuzaiapp.ui.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.yakuzaiapp.ui.home.HomeScreen
import org.junit.Rule
import org.junit.Test

class PrivacyNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun privacyMenuOpensPolicyAndBackReturnsHome() {
        composeRule.setContent {
            var showPrivacy by mutableStateOf(false)
            if (showPrivacy) {
                PrivacyPolicyScreen(onBack = { showPrivacy = false })
            } else {
                HomeScreen(
                    onOpenDrugSearch = {},
                    onOpenMedisImport = {},
                    onOpenFacilityRegistration = {},
                    onOpenUserRegistration = {},
                    onOpenFillHistory = {},
                    onOpenPrivacyPolicy = { showPrivacy = true },
                    onOpenUserSelection = {},
                    onOpenFillMode = {},
                    onOpenDispensing = {},
                    onOpenAudit = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("メニュー").performClick()
        composeRule.onNodeWithText("プライバシーポリシー").performClick()
        composeRule.onNodeWithText("プライバシーポリシー").assertIsDisplayed()
        composeRule.onNodeWithText("10. 改定・問い合わせ").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.onNodeWithContentDescription("メニュー").assertIsDisplayed()
    }
}
