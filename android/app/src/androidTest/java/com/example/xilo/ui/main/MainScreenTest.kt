package com.example.xilo.ui.main

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.xilo.MainActivity

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appContext_hasCorrectPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.xilo", appContext.packageName)
    }

    @Test
    fun mainActivity_launchesWithoutCrash() {
        activityRule.scenario.onActivity { activity ->
            assertEquals(MainActivity::class.java, activity::class.java)
        }
    }
}
