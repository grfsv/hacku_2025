package com.example.umechika.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.umechika.samples.EmptyRoute
import com.example.umechika.samples.FromHankyuToHanshin
import com.example.umechika.samples.NavigationRoute
import com.example.umechika.ui.theme.ActiveButtonColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationButton(
    onConfirm: (NavigationRoute) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val options = listOf(EmptyRoute(), FromHankyuToHanshin())
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(options[0]) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        colors = buttonColors(
            containerColor = ActiveButtonColor,
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "ルート案内",  // contentDescription は Icon に直接指定
            modifier = Modifier.requiredSize(48.dp)  // Icon のサイズを指定
        )
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "行き先を選択",
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .align(Alignment.CenterHorizontally),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        TextField(
                            value = selectedOption.routeName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("選択してください") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.routeName) },
                                    onClick = {
                                        selectedOption = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ボタンを横並びにする
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { showDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("キャンセル")
                        }
                        Spacer(modifier = Modifier.size(8.dp)) // ボタン間のスペース
                        Button(
                            onClick = {
                                onConfirm(selectedOption)
                                showDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("確定")
                        }
                    }
                }
            }
        }
    }
}
