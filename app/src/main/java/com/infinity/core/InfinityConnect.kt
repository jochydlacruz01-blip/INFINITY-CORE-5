package com.infinity.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private data class InfinityConnectionProfile(
    val name: String,
    val host: String,
    val port: String,
    val username: String
) {
    val address: String
        get() = "$host:$port"
}

private const val INFINITY_CONNECT_PREFS = "infinity_connect_profiles"
private const val INFINITY_CONNECT_KEY = "saved_profiles"

private fun loadInfinityProfiles(
    context: Context
): List<InfinityConnectionProfile> {
    return try {
        val prefs = context.getSharedPreferences(
            INFINITY_CONNECT_PREFS,
            Context.MODE_PRIVATE
        )

        val json = JSONArray(
            prefs.getString(INFINITY_CONNECT_KEY, "[]")
        )

        List(json.length()) { index ->
            val item = json.getJSONObject(index)

            InfinityConnectionProfile(
                name = item.optString("name"),
                host = item.optString("host"),
                port = item.optString("port", "3389"),
                username = item.optString("username")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveInfinityProfiles(
    context: Context,
    profiles: List<InfinityConnectionProfile>
) {
    val json = JSONArray()

    profiles.forEach { profile ->
        json.put(
            JSONObject().apply {
                put("name", profile.name)
                put("host", profile.host)
                put("port", profile.port)
                put("username", profile.username)
            }
        )
    }

    context.getSharedPreferences(
        INFINITY_CONNECT_PREFS,
        Context.MODE_PRIVATE
    ).edit()
        .putString(INFINITY_CONNECT_KEY, json.toString())
        .apply()
}

@Composable
fun InfinityConnectPanel() {
    val context = LocalContext.current

    val profiles = remember(context) {
        mutableStateListOf<InfinityConnectionProfile>().apply {
            addAll(loadInfinityProfiles(context))
        }
    }

    val showEditor = remember { mutableStateOf(false) }
    val editingName = remember { mutableStateOf("") }
    val editingHost = remember { mutableStateOf("") }
    val editingPort = remember { mutableStateOf("3389") }
    val editingUsername = remember { mutableStateOf("") }

    fun notifyUser(message: String) {
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun copyAddress(profile: InfinityConnectionProfile) {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "INFINITY CONNECT",
                profile.address
            )
        )

        notifyUser("Dirección copiada: ${profile.address}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Text(
            text = "INFINITY CONNECT",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Centro de conexiones remotas",
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "REMOTE DESKTOP",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Administra los perfiles de tus computadoras " +
                        "y prepara sus datos para conectarte mediante RDP."
                )

                Text(
                    text = "Puerto predeterminado: 3389"
                )

                Text(
                    text = "Perfiles guardados: ${profiles.size}"
                )
            }
        }

        Button(
            onClick = {
                editingName.value = ""
                editingHost.value = ""
                editingPort.value = "3389"
                editingUsername.value = ""
                showEditor.value = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("＋ Agregar computadora")
        }

        Text(
            text = "MIS COMPUTADORAS",
            style = MaterialTheme.typography.titleMedium
        )

        if (profiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Todavía no hay computadoras guardadas.")

                    Text(
                        "Agrega tu primer perfil para comenzar."
                    )
                }
            }
        }

        profiles.forEach { profile ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text("Dirección: ${profile.address}")
                    Text("Usuario: ${profile.username}")

                    Text(
                        text = "Perfil guardado · Conexión no verificada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyAddress(profile)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copiar dirección")
                        }

                        OutlinedButton(
                            onClick = {
                                profiles.remove(profile)
                                saveInfinityProfiles(context, profiles)
                                notifyUser("Perfil eliminado")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SEGURIDAD",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "INFINITY CONNECT no almacena contraseñas. " +
                "Utiliza una conexión segura y evita exponer el puerto " +
                "3389 directamente a Internet. Para conexiones externas, " +
                "se recomienda utilizar una VPN como Tailscale."
        )

        Text(
            text = "La conexión RDP real se integrará en una siguiente " +
                "etapa mediante un cliente remoto compatible."
        )
    }

    if (showEditor.value) {
        AlertDialog(
            onDismissRequest = {
                showEditor.value = false
            },
            title = {
                Text("Agregar computadora")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editingName.value,
                        onValueChange = {
                            editingName.value = it
                        },
                        label = {
                            Text("Nombre de la PC")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editingHost.value,
                        onValueChange = {
                            editingHost.value = it
                        },
                        label = {
                            Text("IP o nombre del equipo")
                        },
                        placeholder = {
                            Text("192.168.1.100")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editingPort.value,
                        onValueChange = {
                            editingPort.value = it.filter { char ->
                                char.isDigit()
                            }.take(5)
                        },
                        label = {
                            Text("Puerto RDP")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editingUsername.value,
                        onValueChange = {
                            editingUsername.value = it
                        },
                        label = {
                            Text("Usuario de Windows")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val portNumber = editingPort.value.toIntOrNull()

                        if (
                            editingName.value.isBlank() ||
                            editingHost.value.isBlank() ||
                            editingUsername.value.isBlank() ||
                            portNumber == null ||
                            portNumber !in 1..65535
                        ) {
                            notifyUser("Revisa los datos del perfil")
                        } else {
                            val profile = InfinityConnectionProfile(
                                name = editingName.value.trim(),
                                host = editingHost.value.trim(),
                                port = editingPort.value,
                                username = editingUsername.value.trim()
                            )

                            profiles.add(profile)
                            saveInfinityProfiles(context, profiles)

                            showEditor.value = false
                            notifyUser("Computadora guardada")
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditor.value = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
