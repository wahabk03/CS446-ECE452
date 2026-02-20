package com.example.graphicaltimeplanner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.graphicaltimeplanner.ui.theme.GraphicalTimePlannerTheme
import com.example.graphicaltimeplanner.backgrounds.simpleGradient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GraphicalTimePlannerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .simpleGradient()
                ){
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current.applicationContext

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 140.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Logo",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text(text = "Username") },
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedLeadingIconColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedLeadingIconColor = colorResource(R.color.uw_gold_lvl4),
                focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedIndicatorColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedPlaceholderColor = colorResource(R.color.uw_gold_lvl4)
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Username"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(text = "Password") },
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedLeadingIconColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedLeadingIconColor = colorResource(R.color.uw_gold_lvl4),
                focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedIndicatorColor = colorResource(R.color.uw_gold_lvl4),
                unfocusedPlaceholderColor = colorResource(R.color.uw_gold_lvl4)
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Password"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            visualTransformation = PasswordVisualTransformation()
        )

        Button(
            onClick = {
                if (authenticate(username, password)) {
                    onLoginSuccess()
                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(colorResource(R.color.uw_gold_lvl4)),
            contentPadding = PaddingValues(start = 60.dp, end = 60.dp, top = 8.dp, bottom = 8.dp),
            modifier = Modifier
                .padding(top = 18.dp)
        ) {
            Text(
                text = "Login",
                fontSize = 22.sp
            )

        }
    }
}

private fun authenticate(username: String, password: String): Boolean {
    val validUsername = "admin"
    val validPassword = "admin123"
    return username == validUsername && password == validPassword
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(onLoginSuccess = {
                navController.navigate("home") {
                    popUpTo(0)
                }
            })
        }
        composable("home") {
            HomeScreen()
        }
    }
}