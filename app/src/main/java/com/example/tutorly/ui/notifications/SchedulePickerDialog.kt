package com.example.tutorly.ui.notifications
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.tutorly.R
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

fun showSchedulePickerDialog(
    context: Context,
    initialDates: List<Date> = emptyList(),
    maxSlots: Int = 5,
    onDone: (List<Date>) -> Unit,
    onCancel: () -> Unit = {}
) {
    val inflater = LayoutInflater.from(context)
    val dialogView = inflater.inflate(R.layout.dialog_schedule_picker, null)

    val timeSlotsContainer = dialogView.findViewById<LinearLayout>(R.id.time_slots_container)
    val addButton = dialogView.findViewById<Button>(R.id.add_time_slot_button)
    val doneButton = dialogView.findViewById<Button>(R.id.confirm_schedule_button)
    val cancelButton = dialogView.findViewById<Button>(R.id.cancel_schedule_button)

    val selectedDates = initialDates.map { it as Date? }.toMutableList()
    val slotViews = mutableListOf<View>()

    val dialog = AlertDialog.Builder(context)
        .setView(dialogView)
        .setCancelable(false)
        .create()

    fun updateDoneButtonState() {
        val now = Date()
        val hasValidDates = selectedDates.any { it != null && it.after(now) }

        doneButton.isEnabled = hasValidDates
        doneButton.setBackgroundColor(
            if (hasValidDates) context.getColor(android.R.color.holo_green_dark)
            else context.getColor(android.R.color.darker_gray)
        )
    }

    fun updateTimeText(view: View, date: Date?) {
        val timeText = view.findViewById<TextView>(R.id.time_text)
        timeText.text = if (date != null) {
            SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault()).format(date)
        } else {
            "Tap to pick time"
        }
    }

    fun pickDateTime(onPicked: (Date) -> Unit) {
        val now = Calendar.getInstance()

        val datePicker = DatePickerDialog(context, { _, year, month, dayOfMonth ->
            val timePicker = TimePickerDialog(context, { _, hourOfDay, minute ->
                val pickedDateTime = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, hourOfDay, minute)
                }
                val picked = pickedDateTime.time
                if (picked.after(Date())) {
                    onPicked(picked)
                } else {
                    Toast.makeText(context, "Pick a future time.", Toast.LENGTH_SHORT).show()
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false)

            // Change OK and Cancel button color to black in TimePicker
            timePicker.setOnShowListener {
                timePicker.getButton(TimePickerDialog.BUTTON_POSITIVE)
                    ?.setTextColor(android.graphics.Color.BLACK)
                timePicker.getButton(TimePickerDialog.BUTTON_NEGATIVE)
                    ?.setTextColor(android.graphics.Color.BLACK)
            }

            timePicker.show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))

        // Disallow selecting past dates
        datePicker.datePicker.minDate = now.timeInMillis

        // Change OK and Cancel button color to black in DatePicker
        datePicker.setOnShowListener {
            datePicker.getButton(DatePickerDialog.BUTTON_POSITIVE)
                ?.setTextColor(android.graphics.Color.BLACK)
            datePicker.getButton(DatePickerDialog.BUTTON_NEGATIVE)
                ?.setTextColor(android.graphics.Color.BLACK)
        }

        datePicker.show()
    }

    fun addTimeSlot(date: Date? = null) {
        if (slotViews.size >= maxSlots) return

        val slotView = inflater.inflate(R.layout.item_time_slot, null)
        val timeText = slotView.findViewById<TextView>(R.id.time_text)
        val deleteBtn = slotView.findViewById<ImageButton>(R.id.delete_button)

        updateTimeText(slotView, null)

        // Add null as a placeholder to selectedDates
        selectedDates.add(null) // will be null initially

        timeText.setOnClickListener {
            pickDateTime { picked ->
                val index = slotViews.indexOf(slotView)
                if (index >= 0 && picked.after(Date())) {
                    selectedDates[index] = picked
                    updateTimeText(slotView, picked)
                    updateDoneButtonState()
                }
            }
        }

        deleteBtn.setOnClickListener {
            val index = slotViews.indexOf(slotView)
            if (index >= 0) {
                selectedDates.removeAt(index)
                slotViews.removeAt(index)
                timeSlotsContainer.removeView(slotView)
                updateDoneButtonState()
            }
        }

        timeSlotsContainer.addView(slotView)
        slotViews.add(slotView)
        updateDoneButtonState()
    }


    addButton.setOnClickListener {
        if (slotViews.size < maxSlots) {
            addTimeSlot()
        } else {
            Toast.makeText(context, "You can only add up to $maxSlots times.", Toast.LENGTH_SHORT).show()
        }
    }

    doneButton.setOnClickListener {
        val finalDates = selectedDates.filterNotNull()

        if (finalDates.isEmpty()) {
            Toast.makeText(context, "Please select at least one time.", Toast.LENGTH_SHORT).show()
        } else {
            dialog.dismiss()
            onDone(finalDates)
        }
    }

    cancelButton.setOnClickListener {
        dialog.dismiss()
        onCancel()
    }

    dialog.show()
    updateDoneButtonState()
}