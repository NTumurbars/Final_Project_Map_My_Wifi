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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import java.lang.Float.min
import java.util.UUID
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
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Floorplan Bitmap
    var floorplanBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // AP related
    val accessPoints = remember { mutableStateListOf<AccessPointInstance>() }
    // FloorPlan Dimensions
    var showDimensionDialog by remember { mutableStateOf(false) }
    var floorplanWidthInMeters by remember { mutableStateOf<Float?>(null) }
    var floorplanHeightInMeters by remember { mutableStateOf<Float?>(null) }
    var showDialogInvalidInput by remember { mutableStateOf(false) }
    var showAccessPointSelectionDialog by remember { mutableStateOf(false) }

    val pickContentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                // Implementation for FileUtils.loadBitmapFromUri should be provided
                val bitmap = FileUtils.loadBitmapFromUri(context, it)
                floorplanBitmap = bitmap
                showDimensionDialog = true
            }
        }
    }

    // Calculate screen scale
    val screenScale = floorplanBitmap?.let { bitmap ->
        val density = LocalDensity.current // Retrieve the current density

        // Converting Dp to pixel values using toPx() no idea how density works though but I just know it is wrong to use dp as pixels
        val bitmapWidthPx = with(density) { bitmap.width.toDp().toPx() }
        val bitmapHeightPx = with(density) { bitmap.height.toDp().toPx() }
        val screenWidthPx = with(density) { screenWidth.toPx() }
        val screenHeightPx = with(density) { screenHeight.toPx() }

        // Now we have Floats, so min should return Float
        min(screenWidthPx / bitmapWidthPx, screenHeightPx / bitmapHeightPx) * 0.8f // to make it fit in the box
    } ?: 1f // always return 1 in case something goes wrong

    // Calculate meter-to-pixel scale (after floorplan dimensions are set)
    val meterToPixelScale: Float = floorplanWidthInMeters?.let { meters ->
        floorplanBitmap?.width?.div(meters) ?: 1f
    } ?: 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { pickContentLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Choose Floorplan")
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(4.dp)
        ) {
            floorplanBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Floorplan",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .scale(screenScale),
                    contentScale = ContentScale.Fit
                )
            }

            accessPoints.forEachIndexed { index, ap ->
                AccessPointDraggable(
                    accessPoint = ap,
                    meterToPixelScale = meterToPixelScale,
                    screenScale = screenScale,
                    onUpdate = { updatedAp ->
                        accessPoints[index] = updatedAp
                    },
                    onDelete = { deletedAp ->
                        accessPoints.removeAll { it.id == deletedAp.id }
                    }
                )
            }
        }

        Button(
            onClick = { showAccessPointSelectionDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Add Access Point")
        }

        //Generate Proposal button
        Button(
            onClick = { /* Handle generate proposal action */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Generate Proposal")
        }

    }


    if (showDimensionDialog) {
        RequestDimensionsDialog { width, height ->
            // check for valid input
            if (width > 0f && height > 0f) {
                floorplanWidthInMeters = width
                floorplanHeightInMeters = height
                showDimensionDialog = false


                accessPoints.clear()
                floorplanBitmap?.let { bitmap ->
                    val accessPointsToAdd = autoPlaceAccessPoints(
                        bitmap.width,
                        bitmap.height,
                        width,
                        height,
                        AccessPointTypes.availableAccessPointTypes.first()// Ensure you have this from user selection
                    )
                    accessPoints.addAll(accessPointsToAdd)
                }



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
                accessPoints.add(AccessPointInstance(type = type, x = newX, y = newY))
            }
        }
    }
}


fun autoPlaceAccessPoints(
    imageWidthPx: Int,
    imageHeightPx: Int,
    widthMeters: Float,
    heightMeters: Float,
    accessPointType: AccessPointType
): List<AccessPointInstance> {
    val scale = minOf(imageWidthPx / widthMeters, imageHeightPx / heightMeters)
    val effectiveDiameterPx = (accessPointType.range * 2 * scale).toInt()

    // Adjust spacing for 20% overlap
    val overlap = 0.2 * accessPointType.range * scale // 20% of the AP's range as overlap
    val spacing = (effectiveDiameterPx - overlap).toInt()

    val accessPoints = mutableListOf<AccessPointInstance>()
    val rowCount = ceil(widthMeters / ((spacing / scale) / scale)).toInt()
    val colCount = ceil(heightMeters / ((spacing / scale) / scale)).toInt()

    for (row in 0 until rowCount) {
        for (col in 0 until colCount) {
            val xPos = (row * spacing + spacing / 2f)
            val yPos = (col * spacing + spacing / 2f)

            if (xPos < imageWidthPx && yPos < imageHeightPx) {
                accessPoints.add(AccessPointInstance(
                    type = accessPointType,
                    x = xPos,
                    y = yPos
                ))
            }
        }
    }

    return accessPoints
}

@Composable
fun AccessPointDraggable(
    accessPoint: AccessPointInstance,
    meterToPixelScale: Float,
    screenScale: Float,
    onUpdate: (AccessPointInstance) -> Unit,
    onDelete: (AccessPointInstance) -> Unit
) {
    var offset by remember { mutableStateOf(Offset(accessPoint.x, accessPoint.y)) }
    var showDelete by remember { mutableStateOf(false) }
    val toggleDelete = { showDelete = !showDelete }
    val iconSize = 40.dp
    val iconHalfSizePx = with(LocalDensity.current) { (iconSize / 2).toPx() * screenScale }

    LaunchedEffect(key1 = accessPoint) {
        offset = Offset(accessPoint.x * screenScale, accessPoint.y * screenScale)
    }

    val dragGestureModifier = Modifier
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { _ ->
                    showDelete = true
                },
                onDragEnd = {
                    showDelete = false
                    onUpdate(accessPoint.copy(x = offset.x / screenScale, y = offset.y / screenScale))
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                }
            )
        }

    val gradient = Brush.radialGradient(
        0f to Color.Green.copy(alpha = 0.5f),
        1f to Color.Green.copy(alpha = 0.1f),
        center = offset,
        radius = accessPoint.type.range * meterToPixelScale * screenScale
    )

    Canvas(modifier = Modifier.fillMaxSize().then(dragGestureModifier)) {
        drawCircle(
            brush = gradient,
            center = offset,
            radius = accessPoint.type.range * meterToPixelScale * screenScale
        )
    }

    Image(
        painter = painterResource(id = accessPoint.type.imageRes),
        contentDescription = "Access Point",
        modifier = Modifier
            .offset { IntOffset((offset.x - iconHalfSizePx).roundToInt(), (offset.y - iconHalfSizePx).roundToInt()) }
            .size(iconSize)
            .clip(CircleShape)
            .then(dragGestureModifier)
            .clickable { toggleDelete() }
    )

    // The delete button appears above the access point when `showDelete` is true
    if (showDelete) {
        IconButton(
            onClick = {
                onDelete(accessPoint)
                showDelete = false
            },
            modifier = Modifier
                .offset { IntOffset((offset.x - iconHalfSizePx).roundToInt(), (offset.y + iconHalfSizePx).roundToInt()) }
                .size(iconSize)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Access Point",
                tint = MaterialTheme.colorScheme.error
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
                        label = { Text("Width in meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height in meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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