package com.injectorsuite.observertemplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.injectorsuite.observertemplate.ui.theme.ObserverTemplateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //A couple of examples of how this template might be used.
        val view1 = View1()
        view1.dispatchRequest1()
        view1.dispatchRequest2()

        val view2 = View2()
        view2.dispatchRequest()

        setContent {
            ObserverTemplateTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LandingScreen()
                }
            }
        }
    }
}

@Composable
fun LandingScreen() {
    Text(
        text = "Landing Screen"
    )
}

