package com.example.testapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.testapplication.model.AccessPoint
import com.example.testapplication.ui.theme.TestApplicationTheme
import com.example.testapplication.utility.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FloorplanPickerAndDisplay()
        }
    }
}

@Composable
fun FloorplanPickerAndDisplay() {

    //Need LocalContext to get file
    val context = LocalContext.current

    // Don't fully understand how it is working but coroutineScope improves perfromance.
    val coroutineScope = rememberCoroutineScope()
    var floorplanBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showDimensionDialog by remember { mutableStateOf(false) }
    //    var accessPoints by remember { mutableStateOf<List<AccessPoint>?>(null) }


    val pickContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    FileUtils.loadBitmapFromUri(context, it)
                }
                floorplanBitmap = bitmap
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick =
            {
                pickContentLauncher.launch("image/*")
            }
        )
        {
            Text("Please choose a Floorplan (picture or a pdf)")
        }
        floorplanBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Floorplan",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            showDimensionDialog = true
            if (showDimensionDialog) {
                RequestDimensionsDialog { width, height -> showDimensionDialog = false
//            accessPoints = AccessPointPlacer.placeAccessPoints(width, height, 5f)
                }
                showDimensionDialog = false
            }


        }
    }
}

@Composable
fun RequestDimensionsDialog(onDimensionsReady: (Float, Float) -> Unit) {
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        //Learned today
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enter Floorplan Dimensions") },
            text = {
                Column {
                    TextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Width in meters") }
                    )
                    TextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height in meters") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        onDimensionsReady(width.toFloatOrNull() ?: 0f, height.toFloatOrNull() ?: 0f)
                    }
                ) { Text("Confirm") }
            }
        )
    }
}

