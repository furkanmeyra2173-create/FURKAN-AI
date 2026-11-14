package com.furkanai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Cyan = Color(0xFF48DDF5)
private val Background = Color(0xFF070B14)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Cyan,
                background = Background,
                surface = Color(0xFF101827)
            )) {
                AssistantHome()
            }
        }
    }
}

@Composable
private fun AssistantHome() {
    val transition = rememberInfiniteTransition(label = "assistant-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF101D31), Background, Color(0xFF05070D))
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "FURKAN AI",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "PERSONAL AI ASSISTANT",
                    color = Cyan.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(218.dp)
                        .scale(pulse)
                        .background(
                            Brush.radialGradient(
                                listOf(Cyan.copy(alpha = 0.22f), Color(0xFF183B62), Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(142.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1A5475), Color(0xFF101B35))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AI", color = Cyan, fontSize = 42.sp, fontWeight = FontWeight.Light)
                    }
                }
                Spacer(Modifier.height(34.dp))
                Text("Merhaba, ben FURKAN AI.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sana nasıl yardımcı olabilirim?",
                    color = Color(0xFF9EACC1),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { /* Voice interaction will be added in the next milestone. */ },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF06111B))
                ) {
                    Text("Sesli konuşma yakında", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Çok dilli  •  İnternet destekli  •  Akıllı asistan", color = Color(0xFF75849B), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("FURKAN AI  •  v0.1.0", color = Color(0xFF536176), fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
