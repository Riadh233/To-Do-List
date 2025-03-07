package com.notesapp.thoughtbox.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.notesapp.thoughtbox.MainActivity
import com.notesapp.thoughtbox.R
import com.notesapp.thoughtbox.databinding.FragmentAddTaskBinding
import com.notesapp.thoughtbox.tools.HIGH_PRIORITY
import com.notesapp.thoughtbox.tools.IDGenerator
import com.notesapp.thoughtbox.tools.LOW_PRIORITY
import com.notesapp.thoughtbox.tools.MID_PRIORITY
import com.notesapp.thoughtbox.tools.Priority
import com.notesapp.thoughtbox.tools.REQUEST_CODE_NOTIFICATION_PERMISSION
import com.notesapp.thoughtbox.ui.SpinnerAdapter
import com.notesapp.thoughtbox.viewmodels.SharedViewModel
import com.notesapp.thoughtbox.viewmodels.TasksViewModel
import java.text.SimpleDateFormat
import java.util.*

class AddTaskFragment : Fragment() {
    private lateinit var binding: FragmentAddTaskBinding
    private lateinit var viewModel: TasksViewModel
    private val args by navArgs<AddTaskFragmentArgs>()
    private lateinit var timePicker: MaterialTimePicker
    private lateinit var calendar : Calendar
    private var updatedReminder : Boolean = false
    private var onBackPressedCallback: OnBackPressedCallback? = null
    private val priorities = listOf(
        Priority("High Priority", R.drawable.ic_priority_high),
        Priority("Medium Priority", R.drawable.ic_priority_mid),
        Priority("Low Priority", R.drawable.ic_priority_low)
    )
    private val sharedViewModel: SharedViewModel by lazy {
        (requireActivity() as MainActivity).viewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddTaskBinding.inflate(inflater)
        viewModel = ViewModelProvider(this)[TasksViewModel::class.java]
        calendar = Calendar.getInstance()

        if(savedInstanceState != null){
            handleLandscapeMode(savedInstanceState)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val taskId : Long = IDGenerator.generateID()
        setUpReminderButton()
        setUpPrioritySpinner()

        if(args.currTask != null)
            setUpCurrTask()

        setUpBackButton(taskId)
    }


    private fun setUpPrioritySpinner() {
        binding.spinner.adapter = SpinnerAdapter(requireContext(),priorities)
        binding.spinner.background.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(requireContext(), R.color.blue),
            PorterDuff.Mode.SRC_ATOP
        )
    }

    private fun setUpBackButton(taskId : Long) {
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
       onBackPressedCallback = object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                if(isEnabled){
                    if((inputCheck(binding.etTitle.text.toString(),binding.etDescription.text.toString()))){
                        if(args.currTask == null){
                            createTask(taskId)
                            if (timeChosen())
                                sharedViewModel.setAlarm(taskId, calendar.timeInMillis)
                        }
                        else{
                            updateTask()
                            if (timeChosen() && updatedReminder){
                                sharedViewModel.setAlarm(args.currTask!!.id, calendar.timeInMillis)
                            }
                        }
                    }
                    isEnabled = false
                    findNavController().navigate(AddTaskFragmentDirections.actionAddTaskFragmentToViewPagerFragment2())
                }

            }
        }
        onBackPressedCallback?.let {
            requireActivity().onBackPressedDispatcher.addCallback(this, it)
        }
    }

    private fun handleLandscapeMode(savedInstanceState: Bundle) {
        val buttonText = savedInstanceState.getString("buttonText")
        binding.reminderButton.text = buttonText
        calendar.timeInMillis = savedInstanceState.getLong("calendar")
    }

    private fun timeChosen(): Boolean {
        val reminderBtnString : String = binding.reminderButton.text.toString().lowercase(Locale.getDefault())
        return reminderBtnString != getString(R.string.set_reminder) && reminderBtnString != getString(R.string.expired).lowercase()
    }

    private fun setUpReminderButton() {
        binding.reminderButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            }
            setUpTimePicker()
        }
    }
    private fun setUpTimePicker(){
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText("Select Time")
            .build()

        timePicker.show(requireActivity().supportFragmentManager, "time_picker")
        timePicker.addOnPositiveButtonClickListener {
            if (args.currTask != null)
                updatedReminder = true
            val chosenHour = timePicker.hour
            val chosenMinute = timePicker.minute
            val selectedTime = formatTime(chosenHour, chosenMinute)
            calendar[Calendar.HOUR_OF_DAY] = chosenHour
            calendar[Calendar.MINUTE] = chosenMinute
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MILLISECOND] = 0
            val currentTime = Calendar.getInstance()
            if(calendar.timeInMillis <= currentTime.timeInMillis){
                calendar.add(Calendar.DATE, 1)
                binding.reminderButton.text = "Tomorrow $selectedTime"
            }else{
                binding.reminderButton.text = "Today $selectedTime"
            }
        }
    }
    private fun formatTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        return format.format(calendar.time)
    }


    @SuppressLint("SuspiciousIndentation")
    private fun setUpCurrTask(){
        binding.etTitle.setText(args.currTask?.title)
        binding.etDescription.setText(args.currTask?.description)

        binding.spinner.setSelection(priorities.indexOfFirst { it.label == getPriorityText(args.currTask?.priority!!) })

        if(args.currTask?.alarmTime != getString(R.string.Set_reminder)){
            if (args.currTask?.alarmTime == getString(R.string.expired))
                binding.reminderButton.text = getString(R.string.expired)
            else
            binding.reminderButton.text = args.currTask?.alarmTime?.substring(5)
        }

        else{
            binding.reminderButton.text = getString(R.string.set_reminder)
        }
    }


    private fun createTask(taskId: Long){
        var text = "Rings ${binding.reminderButton.text.trim()}"
        if(!timeChosen())
            text = getString(R.string.Set_reminder)
        val selectedPriority = getPriority(priorities[binding.spinner.selectedItemPosition].label)

        viewModel.insertDataToDatabase(taskId,binding.etTitle.text.toString().trim()
            ,binding.etDescription.text.toString().trim(),selectedPriority,text,calendarToString(calendar.time)
        )
    }

    private fun getPriority(label: String): Int {
        return when(label){
            getString(R.string.low_priority) -> LOW_PRIORITY
            getString(R.string.medium_priority) -> MID_PRIORITY
            else -> HIGH_PRIORITY
        }
    }

    private fun calendarToString(calendar: Date): String {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss 'GMT'Z yyyy", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    private fun getPriorityText(color: Int): String {
        return when(color) {
            HIGH_PRIORITY -> getString(R.string.high_priority)
            MID_PRIORITY -> getString(R.string.medium_priority)
            else -> getString(R.string.low_priority)
        }
    }

    private fun updateTask(){
        var alarmTime = "Rings ${binding.reminderButton.text.trim()}"
        if(!timeChosen()){
            alarmTime = getString(R.string.Set_reminder)
        }
        if(binding.reminderButton.text.toString().lowercase() == getString(R.string.expired).lowercase())
            alarmTime = getString(R.string.expired)

        val selectedPriority = getPriority(priorities[binding.spinner.selectedItemPosition].label)
        val scheduledDate: String = if(updatedReminder)
            calendarToString(calendar.time)
        else
            args.currTask!!.scheduledDate
        viewModel.updateData(args.currTask!!.id,binding.etTitle.text.toString().trim(),
            binding.etDescription.text.toString().trim(),selectedPriority,alarmTime,
            scheduledDate)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) {
            outState.putString("buttonText", binding.reminderButton.text.toString())
            outState.putLong("calendar", calendar.timeInMillis)
        }
    }
    private fun inputCheck(title : String,description : String) : Boolean{
        return !(TextUtils.isEmpty(title) && TextUtils.isEmpty(description))
    }
    fun requestNotificationPermission() {
        // Check if the permission is already granted
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
                ActivityCompat.requestPermissions(requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION_PERMISSION)
        }
    }
    override fun onResume() {
        super.onResume()
        onBackPressedCallback?.isEnabled = true
    }

    override fun onPause() {
        super.onPause()
        onBackPressedCallback?.isEnabled = false
    }
}
