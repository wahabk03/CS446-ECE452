package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Program selection
    var programs by remember { mutableStateOf<List<Program>>(emptyList()) }
    var selectedProgram by remember { mutableStateOf<Program?>(null) }
    var programSearchQuery by remember { mutableStateOf("") }
    var programDropdownExpanded by remember { mutableStateOf(false) }

    // Year level selection
    val yearLevels = listOf("1A", "1B", "2A", "2B", "3A", "3B", "4A", "4B")
    var selectedYearLevel by remember { mutableStateOf("") }
    var yearDropdownExpanded by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        programs = CourseRepository.getPrograms()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.75f))
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circle with calendar icon
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Schedule icon",
                    tint = Color.White,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Create Account",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Register with your UWaterloo email",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        placeholder = { Text("you@uwaterloo.ca") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                            focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                            cursorColor = colorResource(R.color.uw_gold_lvl4)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                            focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                            cursorColor = colorResource(R.color.uw_gold_lvl4)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                            focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                            cursorColor = colorResource(R.color.uw_gold_lvl4)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Program selection (optional)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (programDropdownExpanded) programSearchQuery
                                    else selectedProgram?.name ?: "",
                            onValueChange = {
                                programSearchQuery = it
                                programDropdownExpanded = true
                            },
                            label = { Text("Program (optional)") },
                            placeholder = { Text("Search your program...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                                focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                                cursorColor = colorResource(R.color.uw_gold_lvl4)
                            )
                        )
                        DropdownMenu(
                            expanded = programDropdownExpanded && programs.isNotEmpty(),
                            onDismissRequest = { programDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            val filtered = programs.filter {
                                it.name.contains(programSearchQuery, ignoreCase = true)
                            }
                            filtered.forEach { program ->
                                DropdownMenuItem(
                                    text = { Text(program.name, fontSize = 14.sp) },
                                    onClick = {
                                        selectedProgram = program
                                        programSearchQuery = ""
                                        programDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Year level selection (optional)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedYearLevel,
                            onValueChange = {},
                            label = { Text("Year Level (optional)") },
                            placeholder = { Text("Select year...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { yearDropdownExpanded = true },
                            singleLine = true,
                            readOnly = true,
                            enabled = !isLoading,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                                focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                                cursorColor = colorResource(R.color.uw_gold_lvl4)
                            )
                        )
                        DropdownMenu(
                            expanded = yearDropdownExpanded,
                            onDismissRequest = { yearDropdownExpanded = false }
                        ) {
                            yearLevels.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year) },
                                    onClick = {
                                        selectedYearLevel = year
                                        yearDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val trimmedEmail = email.trim()
                            val trimmedPassword = password.trim()

                            if (trimmedEmail.isBlank() || trimmedPassword.isBlank() || confirmPassword.isBlank()) {
                                errorMessage = "Please fill in all fields"
                                return@Button
                            }

                            if (!trimmedEmail.endsWith("@uwaterloo.ca")) {
                                errorMessage = "Must use a @uwaterloo.ca email"
                                return@Button
                            }

if (trimmedPassword.length < 6) {
                                errorMessage = "Password must be at least 6 characters"
                                return@Button
                            }

                            if (trimmedPassword != confirmPassword.trim()) {
                                errorMessage = "Passwords do not match"
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            coroutineScope.launch {
                                try {
                                    val result = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                                    // Save profile data (program + year level) if provided
                                    val uid = result.user?.uid
                                    if (uid != null) {
                                        val userData = mutableMapOf<String, Any>(
                                            "scheduledCourses" to emptyList<Any>()
                                        )
                                        if (selectedProgram != null) {
                                            userData["program"] = selectedProgram!!.slug
                                            userData["faculty"] = selectedProgram!!.faculty
                                        }
                                        if (selectedYearLevel.isNotBlank()) {
                                            // Convert "1A"->1, "1B"->1, "2A"->2, etc.
                                            val yearNum = selectedYearLevel.first().digitToInt()
                                            userData["yearLevel"] = yearNum
                                            userData["yearLevelLabel"] = selectedYearLevel
                                        }
                                        FirebaseFirestore.getInstance()
                                            .collection("users").document(uid)
                                            .set(userData).await()
                                    }
                                    result.user?.sendEmailVerification()?.await()
                                    auth.signOut()
                                    onRegisterSuccess()
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage ?: "Registration failed"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.uw_gold_lvl4),
                            contentColor = Color.Black
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(
                                "REGISTER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Already have an account?",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = onLoginClick) {
                            Text(
                                "Login here!",
                                color = colorResource(R.color.uw_gold_lvl4),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
