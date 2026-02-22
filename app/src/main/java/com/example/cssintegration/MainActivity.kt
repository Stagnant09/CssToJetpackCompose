package com.example.cssintegration

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.cssintegration.annotation.CssStyle
import com.example.cssintegration.cssUtils.rememberParsedCss
import com.example.cssintegration.ui.theme.CssIntegrationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CssIntegrationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)){
                        Headline("Hello World")
                    }
                }
            }
        }
    }
}

private const val css = "color: #E71E63;" +
        " font-size: 32px;" +
        " font-weight: 700;" +
        " padding: 20px;" +
        " letter-spacing: 5px;" +
        " text-decoration: underline"

private const val boxCss = "background-color: #434343;"

@CssStyle(css)
@Composable
fun Headline(text: String) {
    val textStyle = rememberParsedCss(css)
    val boxStyle = rememberParsedCss(boxCss)
    Box(
        modifier = boxStyle.modifier,
    ){
        Text(
            text = text,
            style = textStyle.toTextStyle(),
            modifier = textStyle.modifier,
        )
    }
}

@Preview
@Composable
fun HeadlinePreview(){
    Headline("Hello world")
}