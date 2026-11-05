package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private var initialQuickActionType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val app = application as SpendFlowApplication

        setContent {
            MyApplicationTheme {
                MainScreen(
                    app = app,
                    initialQuickActionType = initialQuickActionType
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.getStringExtra("action") ?: intent.action
        val type = intent.getStringExtra("type")
        if (action == "quick_add" || action == "com.example.spendflow.ACTION_QUICK_ADD") {
            initialQuickActionType = type ?: "EXPENSE"
        }
    }
}
