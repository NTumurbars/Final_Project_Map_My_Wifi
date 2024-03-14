package com.example.finalproject
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.finalproject.ui.theme.FinalProjectTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Check
import kotlin.math.ceil
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinalProjectTheme {
                FloorplanPickerAndDisplay()
            }
        }
    }
}

@Composable
fun FloorplanPickerAndDisplay() {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    //Floorplan Bitmap
    var floorplanBitmap by remember { mutableStateOf<Bitmap?>(null) }

    //AP related
    var accessPoints by remember { mutableStateOf(listOf<AccessPointInstance>()) }
    var showAccessPointSelectionDialog by remember { mutableStateOf(false) }

    //FloorPlan Dimensions
    var showDimensionDialog by remember { mutableStateOf(false) }
    var floorplanWidthInMeters by remember { mutableStateOf<Float?>(null) }
    var floorplanHeightInMeters by remember { mutableStateOf<Float?>(null) }
    var showDialogInvalidInput by remember { mutableStateOf(false) }


    val pickContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bitmap = FileUtils.loadBitmapFromUri(context, it)
                floorplanBitmap = bitmap
                showDimensionDialog = true
            }
        }
    }

    LaunchedEffect(floorplanWidthInMeters, floorplanHeightInMeters, floorplanBitmap) {
        // Ensure we have the dimensions in meters, the bitmap is loaded, and the AP types list isn't empty
        if (floorplanWidthInMeters != null && floorplanHeightInMeters != null &&
            AccessPointTypes.availableAccessPointTypes.isNotEmpty() && floorplanBitmap != null) {

            val imageWidthPx = floorplanBitmap!!.width
            val imageHeightPx = floorplanBitmap!!.height
            val apRangeMeters = AccessPointTypes.availableAccessPointTypes.first().range

            accessPoints = autoPlaceAccessPoints(
                imageWidthPx,
                imageHeightPx,
                floorplanWidthInMeters!!,
                floorplanHeightInMeters!!,
                apRangeMeters
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Button(
            onClick = { pickContentLauncher.launch("image/*") },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Choose Floorplan")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            floorplanBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Floorplan",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            val scale = floorplanBitmap?.width?.let { bmpWidth ->
                floorplanWidthInMeters?.let { widthMeters -> bmpWidth / widthMeters }
            } ?: 1f

            accessPoints.forEach { ap ->
                AccessPointDraggable(accessPoint = ap, scale = scale, onUpdate = { updatedAp ->
                    accessPoints = accessPoints.map { if (it.id == updatedAp.id) updatedAp else it }
                }, onDelete = {
                    accessPoints = accessPoints.filterNot { it.id == ap.id }
                })
            }
        }

        Button(
            onClick = { showAccessPointSelectionDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Add Access Point")
        }
    }

    if (showDimensionDialog) {
        RequestDimensionsDialog { width, height ->
            // check for valid input
            if (width > 0f && height > 0f) {
                floorplanWidthInMeters = width
                floorplanHeightInMeters = height
                showDimensionDialog = false
            } else {
                showDialogInvalidInput = true
            }
        }
    }

    if (showDialogInvalidInput) {
        // If the dimensiosn are wrong
        ErrorDialog(
            errorMessage = "Invalid input! Dimensions must be greater than zero.",
            onDismiss = { showDialogInvalidInput = false }
        )
    }

    if (showAccessPointSelectionDialog) {
        AccessPointSelectionDialog { selectedType ->
            showAccessPointSelectionDialog = false
            selectedType?.let { type ->
                val newX = floorplanBitmap?.width?.div(2)?.toFloat() ?: 0f
                val newY = floorplanBitmap?.height?.div(2)?.toFloat() ?: 0f
                accessPoints = accessPoints + AccessPointInstance(type = type, x = newX, y = newY)
            }
        }
    }
}


fun autoPlaceAccessPoints(
    floorplanImageWidthPx: Int,
    floorplanImageHeightPx: Int,
    widthMeters: Float,
    heightMeters: Float,
    rangeMeters: Float
): List<AccessPointInstance> {
    val overlapFactor = 0.20f  // 20% overlap but maybe more good
    val effectiveRange = rangeMeters * (1 - overlapFactor)
    val scaleX = floorplanImageWidthPx / widthMeters
    val scaleY = floorplanImageHeightPx / heightMeters

    // Calculate the number of APs needed horizontally and vertically
    val numAPsHorizontal = ceil(widthMeters / effectiveRange).toInt()
    val numAPsVertical = ceil(heightMeters / effectiveRange).toInt()

    val accessPoints = mutableListOf<AccessPointInstance>()


    for (x in 0 until numAPsHorizontal) {
        for (y in 0 until numAPsVertical) {
            val xPos = x * effectiveRange * scaleX
            val yPos = y * effectiveRange * scaleY


            val xCentered = xPos - floorplanImageWidthPx / 2
            val yCentered = yPos - floorplanImageHeightPx / 2

            accessPoints.add(
                AccessPointInstance(
                    type = AccessPointTypes.availableAccessPointTypes.first(), // using the defualt ap type that is the first one
                    x = xCentered,
                    y = yCentered
                )
            )
        }
    }

    return accessPoints
}



@Composable
fun AccessPointDraggable(
    accessPoint: AccessPointInstance,
    scale: Float,
    onUpdate: (AccessPointInstance) -> Unit,
    onDelete: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset(accessPoint.x, accessPoint.y)) }
    var showDelete by remember { mutableStateOf(false) }

    val toggleDelete = { showDelete = !showDelete }

    LaunchedEffect(key1 = accessPoint) {
        onUpdate(accessPoint.copy(x = offset.x, y = offset.y))
    }

    // Toggle the delete button on access point tap (maybe need to use a different way to delete)
    val dragGestureModifier = Modifier
        .pointerInput(Unit) {
            detectDragGestures(onDragEnd = {
                onUpdate(accessPoint.copy(x = offset.x, y = offset.y))
                showDelete = false  // Hide delete button when drag ends
            }) { change, dragAmount ->
                change.consume()
                offset += dragAmount
            }
        }

    // this canvas is used to draw the range

    Canvas(modifier = Modifier.fillMaxSize()) {
        val pixelRadius = accessPoint.type.range * scale
        val gradient = Brush.radialGradient(
            0f to Color.Green.copy(alpha = 0.3f),
            1f to Color.Gray.copy(alpha = 0.15f),
            center = Offset(offset.x, offset.y),
            radius = pixelRadius
        )

        drawCircle(
            brush = gradient,
            radius = pixelRadius,
            center = Offset(offset.x, offset.y)
        )

    }

    // Image representing the access point with the drag gesture
    Image(
        painter = painterResource(id = accessPoint.type.imageRes),
        contentDescription = "Access Point",
        modifier = Modifier
            .offset {
                IntOffset(
                    offset.x.roundToInt() - 20.dp.roundToPx(),
                    offset.y.roundToInt() - 20.dp.roundToPx()
                )
            } // Center the image
            .size(40.dp)
            .clip(CircleShape)
            .then(dragGestureModifier)
            .clickable(onClick = toggleDelete)
    )

    // Delete icon
    if (showDelete) {
        IconButton(
            onClick = {
                onDelete()
                showDelete = false
            },
            modifier = Modifier
                .offset {
                    IntOffset(
                        offset.x.roundToInt(), (offset.y + 30.dp.toPx()).roundToInt()
                    )
                } // show delete near the icon don't know where
                .size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = Color.Red
            )
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


@Composable
fun AccessPointSelectionDialog(
    onSelection: (AccessPointType?) -> Unit
) {
    val availableTypes = AccessPointTypes.availableAccessPointTypes
    var selectedTypeIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = { onSelection(null) },
        title = { Text("Select Access Point Type") },
        text = {
            LazyColumn {
                itemsIndexed(availableTypes) { index, type ->
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTypeIndex = index }
                        .padding(16.dp)) {
                        Text(type.name)
                        Spacer(Modifier.weight(1f))
                        if (selectedTypeIndex == index) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelection(selectedTypeIndex?.let { availableTypes[it] })
                }
            ) { Text("Confirm") }
        },
        dismissButton = {
            Button(onClick = { onSelection(null) }) { Text("Cancel") }
        }
    )
}


@Composable
fun ErrorDialog(errorMessage: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Error") },
        text = { Text(text = errorMessage) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ScreenPreview()
{
    FloorplanPickerAndDisplay()
}