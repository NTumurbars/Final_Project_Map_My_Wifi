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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
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


    // Proposal related states
    var showProposalDialog by remember { mutableStateOf(false) }
    val proposalOptions by remember { derivedStateOf { generateProposalOptions(accessPoints) } }
    val visitationFee = 15000 // Example visitation fee
    val proposalSummary by remember { derivedStateOf { ProposalSummary(proposalOptions, visitationFee) } }


    //show the floorplan
    val pickContentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bitmap = FileUtils.loadBitmapFromUri(context, it)
                floorplanBitmap = bitmap
                showDimensionDialog = true
            }
        }
    }

    // Calculate screen scale
    val screenScale = floorplanBitmap?.let { bitmap ->
        val density = LocalDensity.current

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
            onClick = { showProposalDialog = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Generate Proposal")
        }
    }

    if (showProposalDialog) {
        ProposalSummaryDialog(proposalSummary) {
            showProposalDialog = false
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
    // Correctly convert meters to pixels for both dimensions
    val scaleX = imageWidthPx / widthMeters
    val scaleY = imageHeightPx / heightMeters
    // Use the minimum scale to ensure the access point coverage is consistent in both dimensions
    val scale = minOf(scaleX, scaleY)

    // Calculate the effective diameter of an AP's coverage in pixels
    val effectiveDiameterPx = (accessPointType.range * 2 * scale).toInt()
    // Directly compute the spacing between APs, considering a 20% overlap
    val overlap = 0.2 * effectiveDiameterPx
    val spacing = (effectiveDiameterPx - overlap).toInt()

    // Initialize the list to hold all placed AP instances
    val accessPoints = mutableListOf<AccessPointInstance>()

    // Calculate the number of rows and columns to fill the area with APs
    val rowCount = ceil(widthMeters / (effectiveDiameterPx / scale / 2)).toInt()
    val colCount = ceil(heightMeters / (effectiveDiameterPx / scale / 2)).toInt()

    // Place APs in a grid, spacing them according to the calculated values
    for (row in 0 until rowCount) {
        for (col in 0 until colCount) {
            // Calculate the position of each AP in pixels, offsetting by half the spacing to center
            val xPos = (row * spacing + spacing / 2f) * scaleX
            val yPos = (col * spacing + spacing / 2f) * scaleY

            // Add the AP to the list if it's within the image bounds
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
    val baseIconSize = 40.dp
    val iconSize = baseIconSize * screenScale
    val iconHalfSizePx = with(LocalDensity.current) { (iconSize / 2).toPx() }

    LaunchedEffect(key1 = accessPoint) {
        offset = Offset(accessPoint.x * screenScale, accessPoint.y * screenScale)
    }

    // Modifier for drag gestures
    val dragGestureModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(onDrag = { change, dragAmount ->
            change.consume()
            offset += dragAmount
        }, onDragEnd = {
            onUpdate(accessPoint.copy(x = offset.x / screenScale, y = offset.y / screenScale))
        })
    }

    // need good design here but don't know what
    RangeIndicator(
        center = offset,
        radius = accessPoint.type.range * meterToPixelScale * screenScale,
    )

    // AP Icon, centered
    Image(
        painter = painterResource(id = accessPoint.type.imageRes),
        contentDescription = "Access Point",
        modifier = Modifier
            .offset { IntOffset((offset.x - iconHalfSizePx).roundToInt(), (offset.y - iconHalfSizePx).roundToInt()) }
            .size(iconSize)
            .clipToBounds()
            .clickable { showDelete = !showDelete }
            .then(dragGestureModifier)
    )

    // delete button visibility
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
fun RangeIndicator(
    center: Offset,
    radius: Float,
) {
    val diameter = (radius * 2).dp
    Canvas(modifier = Modifier.size(diameter)) {
        val colors = listOf(
            Color(0xFF00C853),
            Color(0xFF00C853).copy(alpha = 0.6f),
            Color(0x0000C853).copy(alpha = 0.1f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
}

@Composable
fun ProposalSummaryDialog(summary: ProposalSummary, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Proposal Summary",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "One-time Visitation Fee: ¥${summary.visitationFee}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Rental",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Purchase",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                summary.proposalOptions.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${option.accessPointType.name} ",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Qty: ${option.quantity}")
                            Text("Unit Cost: ¥${option.accessPointType.rentalCost}")
                            Text("Total: ¥${option.rentalTotalCost}")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${option.accessPointType.name} ",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Qty: ${option.quantity}")
                            Text("Unit Cost: ¥${option.accessPointType.purchaseCost}")
                            Text("Total: ¥${option.purchaseTotalCost}")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                val totalRentalWithVisitation = summary.totalRentalCost + summary.visitationFee
                val totalPurchaseWithVisitation = summary.totalPurchaseCost + summary.visitationFee
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Grand Total with Visitation: ¥$totalRentalWithVisitation",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Grand Total with Visitation: ¥$totalPurchaseWithVisitation",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Close")
                }
            }
        }
    }
}




fun generateProposalOptions(accessPoints: List<AccessPointInstance>): List<ProposalOption> {
    val groupedByType = accessPoints.groupBy { it.type }
    return groupedByType.map { (type, instances) ->
        ProposalOption(
            accessPointType = type,
            quantity = instances.size,
            rentalTotalCost = instances.size * type.rentalCost,
            purchaseTotalCost = instances.size * type.purchaseCost
        )
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