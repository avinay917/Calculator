package com.example.authapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.AppPreferences
import java.text.DecimalFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(
    email: String,
    onSecretUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogInput by remember { mutableStateOf("") }

    val secretPin = remember { AppPreferences.getSecretPin(context) }

    fun evaluateMath(expr: String): String {
        if (expr.isBlank()) return "0"
        return try {
            val sanitized = expr.replace("×", "*").replace("÷", "/")
            val tokens = mutableListOf<String>()
            var currentNum = ""
            for (ch in sanitized) {
                if (ch in "+-*/%") {
                    if (currentNum.isNotEmpty()) {
                        tokens.add(currentNum)
                        currentNum = ""
                    }
                    tokens.add(ch.toString())
                } else if (ch.isDigit() || ch == '.') {
                    currentNum += ch
                }
            }
            if (currentNum.isNotEmpty()) tokens.add(currentNum)
            if (tokens.isEmpty()) return "0"

            // Compute * / % first
            val pass1 = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (token == "*" || token == "/" || token == "%") {
                    val prev = pass1.removeAt(pass1.size - 1).toDoubleOrNull() ?: 0.0
                    val next = if (i + 1 < tokens.size) tokens[i + 1].toDoubleOrNull() ?: 1.0 else 1.0
                    val res = when (token) {
                        "*" -> prev * next
                        "/" -> if (next != 0.0) prev / next else 0.0
                        "%" -> prev * (next / 100.0)
                        else -> prev
                    }
                    pass1.add(res.toString())
                    i += 2
                } else {
                    pass1.add(token)
                    i++
                }
            }

            // Compute + -
            var total = pass1.firstOrNull()?.toDoubleOrNull() ?: 0.0
            var j = 1
            while (j < pass1.size) {
                val op = pass1[j]
                val nextVal = if (j + 1 < pass1.size) pass1[j + 1].toDoubleOrNull() ?: 0.0 else 0.0
                if (op == "+") total += nextVal
                else if (op == "-") total -= nextVal
                j += 2
            }

            val formatter = DecimalFormat("#,###.######")
            formatter.format(total)
        } catch (_: Exception) {
            "Error"
        }
    }

    fun onButtonClick(label: String) {
        when (label) {
            "AC" -> {
                expression = ""
                resultText = "0"
            }
            "DEL" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                    resultText = if (expression.isEmpty()) "0" else evaluateMath(expression)
                }
            }
            "=" -> {
                val cleanExpr = expression.trim()
                // Check if secret PIN matches
                if (cleanExpr == secretPin || cleanExpr.endsWith(secretPin)) {
                    Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
                    onSecretUnlocked()
                    return
                }

                if (cleanExpr.isNotEmpty()) {
                    val evaluated = evaluateMath(cleanExpr)
                    resultText = evaluated
                    expression = evaluated.replace(",", "")
                }
            }
            "+", "-", "×", "÷", "%" -> {
                if (expression.isNotEmpty() && expression.last() !in "+-×÷%") {
                    expression += label
                }
            }
            "." -> {
                val lastNumber = expression.split("+", "-", "×", "÷", "%").lastOrNull() ?: ""
                if (!lastNumber.contains(".")) {
                    expression += if (lastNumber.isEmpty()) "0." else "."
                }
            }
            else -> {
                // Numbers
                expression += label
                resultText = evaluateMath(expression)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF17171C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = expression.ifEmpty { " " },
                    color = Color(0xFF8E8E93),
                    fontSize = 28.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resultText,
                    color = Color.White,
                    fontSize = if (resultText.length > 8) 42.sp else 56.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Keypad Grid
            val buttons = listOf(
                listOf("AC", "%", "DEL", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { btn ->
                        val isOperator = btn in listOf("÷", "×", "-", "+", "=")
                        val isSpecial = btn in listOf("AC", "%", "DEL")
                        val isZero = btn == "0"

                        val btnBgColor = when {
                            btn == "=" -> Color(0xFF4B7BE5)
                            isOperator -> Color(0xFF4B7BE5).copy(alpha = 0.85f)
                            isSpecial -> Color(0xFF333338)
                            else -> Color(0xFF242429)
                        }

                        val textColor = when {
                            btn == "=" || isOperator -> Color.White
                            isSpecial -> Color(0xFFA5A5A5)
                            else -> Color.White
                        }

                        Box(
                            modifier = Modifier
                                .weight(if (isZero) 2f else 1f)
                                .height(72.dp)
                                .clip(if (isZero) RoundedCornerShape(36.dp) else CircleShape)
                                .background(btnBgColor)
                                .combinedClickable(
                                    onClick = { onButtonClick(btn) },
                                    onLongClick = {
                                        if (btn == "=") {
                                            showPinDialog = true
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (btn == "DEL") "⌫" else btn,
                                color = textColor,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinDialogInput = ""
            },
            icon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text("Security Passcode", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Enter the secret passcode to unlock configuration settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pinDialogInput,
                        onValueChange = { pinDialogInput = it },
                        singleLine = true,
                        placeholder = { Text("Default: 1234") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinDialogInput == secretPin) {
                            showPinDialog = false
                            pinDialogInput = ""
                            onSecretUnlocked()
                        } else {
                            Toast.makeText(context, "Invalid Passcode", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pinDialogInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
