package com.example.ui.agent

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * TaskDateTimeSchedulerPicker
 *
 * Allows users to choose an explicit Date and Time when an autonomous task should trigger.
 */
@Composable
fun TaskDateTimeSchedulerPicker(
    selectedTimeMillis: Long,
    onTimeSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        timeInMillis = if (selectedTimeMillis > System.currentTimeMillis()) selectedTimeMillis else System.currentTimeMillis() + (60 * 1000 * 30)
    }

    val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

    val dateStr = dateFormatter.format(Date(calendar.timeInMillis))
    val timeStr = timeFormatter.format(Date(calendar.timeInMillis))

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date Selector Box
        Row(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                .clickable {
                    showDatePicker(context, calendar) { updatedCal ->
                        onTimeSelected(updatedCal.timeInMillis)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Select Date",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text("Date", color = Color(0xFF8B949E), fontSize = 10.sp)
                Text(dateStr, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Time Selector Box
        Row(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                .clickable {
                    showTimePicker(context, calendar) { updatedCal ->
                        onTimeSelected(updatedCal.timeInMillis)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = "Select Time",
                tint = Color(0xFF2ED573),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text("Time", color = Color(0xFF8B949E), fontSize = 10.sp)
                Text(timeStr, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun showDatePicker(
    context: Context,
    currentCal: Calendar,
    onDateChosen: (Calendar) -> Unit
) {
    val year = currentCal.get(Calendar.YEAR)
    val month = currentCal.get(Calendar.MONTH)
    val day = currentCal.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(context, { _, selYear, selMonth, selDay ->
        val updated = (currentCal.clone() as Calendar).apply {
            set(Calendar.YEAR, selYear)
            set(Calendar.MONTH, selMonth)
            set(Calendar.DAY_OF_MONTH, selDay)
        }
        onDateChosen(updated)
    }, year, month, day).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}

private fun showTimePicker(
    context: Context,
    currentCal: Calendar,
    onTimeChosen: (Calendar) -> Unit
) {
    val hour = currentCal.get(Calendar.HOUR_OF_DAY)
    val minute = currentCal.get(Calendar.MINUTE)

    TimePickerDialog(context, { _, selHour, selMinute ->
        val updated = (currentCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, selHour)
            set(Calendar.MINUTE, selMinute)
            set(Calendar.SECOND, 0)
        }
        onTimeChosen(updated)
    }, hour, minute, false).show()
}
