package com.evorez.ezdmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var controller: DeviceController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = DeviceController(applicationContext)
        setContent { EzdApp(controller) }
    }
    override fun onStart() { super.onStart(); controller.start() }
    override fun onStop() { controller.stop(); super.onStop() }
    override fun onDestroy() { controller.destroy(); super.onDestroy() }
}

@Composable
private fun EzdApp(device: DeviceController) {
    var page by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("Home", "CPU", "Report").forEachIndexed { index, name ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Text(listOf("⌂", "◉", "≡")[index]) }, label = { Text(name) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    0 -> Home(device)
                    1 -> Cpu(device)
                    else -> Report(device)
                }
            }
        }
    }
}

@Composable
private fun Home(device: DeviceController) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(R.drawable.evorez_placeholder), contentDescription = "Evorez", modifier = Modifier.size(64.dp))
        Column { Text("EZD Manager", style = MaterialTheme.typography.headlineSmall); Text("Root: ${device.root}") }
    }
    Button(onClick = device::grantRoot, enabled = !device.busy) { Text(if (device.root == "Granted") "Request root again" else "Grant root") }
    Text("Battery  ${device.battery}", style = MaterialTheme.typography.titleMedium)
    if (device.root == "Granted") {
        Text("CPU", style = MaterialTheme.typography.titleMedium)
        if (device.snapshot.policies.isEmpty()) Text("No readable CPU policies")
        device.snapshot.policies.forEach { Text("${it.id}  ·  ${freq(it.current)}") }
        Text("Thermal", style = MaterialTheme.typography.titleMedium)
        if (device.snapshot.thermal.isEmpty()) Text("No thermal zones found")
        device.snapshot.thermal.forEach { Text("${it.type.ifBlank { it.id }} (${it.id})  ·  ${it.temp?.let { n -> "${n / 1000.0} °C" } ?: "Unavailable"}") }
    }
    if (device.message.isNotBlank()) Text(device.message)
}

@Composable
private fun Cpu(device: DeviceController) {
    Text("CPU", style = MaterialTheme.typography.headlineSmall)
    if (device.root != "Granted") { Text("Grant root on Home to discover CPU controls."); return }
    if (device.snapshot.policies.isEmpty()) Text("No readable CPU policies")
    if (device.message.isNotBlank()) Text(device.message)
    device.snapshot.policies.forEach { policy ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(policy.id, style = MaterialTheme.typography.titleMedium)
                if (policy.cpus.isNotBlank()) Text("CPUs ${policy.cpus}")
                Text("Now ${freq(policy.current)} · Max ${freq(policy.max)}")
                if (policy.governor.isNotBlank()) Text("Governor ${policy.governor}")
                if (policy.writable && policy.original != null) {
                    if (policy.frequencies.isNotEmpty()) FrequencyMenu(policy, device)
                    else Text("Available frequencies not exposed; selection unavailable.")
                    OutlinedButton(onClick = { device.setMax(policy, policy.original, reset = true) }, enabled = !device.busy) { Text("Reset to ${freq(policy.original)}") }
                } else Text(if (policy.original == null) "Baseline unavailable; changes disabled." else "Max frequency is read-only.")
            }
        }
    }
}

@Composable
private fun FrequencyMenu(policy: Policy, device: DeviceController) {
    var expanded by remember(policy.id) { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }, enabled = !device.busy) { Text("Set max") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            policy.frequencies.forEach { value ->
                DropdownMenuItem(text = { Text(freq(value)) }, onClick = { expanded = false; device.setMax(policy, value) }, enabled = !device.busy && policy.min != null && value >= policy.min)
            }
        }
    }
}

@Composable
private fun Report(device: DeviceController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Text("Device report", style = MaterialTheme.typography.headlineSmall)
    Button(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EZD device report", device.report()))
        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
    }) { Text("Copy") }
    Text(device.report(), style = MaterialTheme.typography.bodySmall)
}

private fun freq(value: Long?): String = if (value == null) "Unavailable" else "${value / 1000} MHz"
