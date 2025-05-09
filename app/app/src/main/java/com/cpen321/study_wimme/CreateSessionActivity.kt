package com.cpen321.study_wimme

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreateSessionActivity : AppCompatActivity() {
    private var sessionVisibility: SessionVisibility = SessionVisibility.PRIVATE
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private val calendar = Calendar.getInstance()
    private var startDateMillis: Long = 0
    private var endDateMillis: Long = 0

    private val faculties = listOf(
        "Arts",
        "Science",
        "Engineering",
        "Business",
        "Education",
        "Law",
        "Medicine",
        "Other"
    )

    companion object {
        private const val LOCATION_PICKER_REQUEST_CODE = 1001
        private const val FRIEND_SELECT_REQUEST_CODE = 1002
        private const val TAG = "CreateSessionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_session)

        val nameInput = findViewById<TextInputEditText>(R.id.sessionNameInput)
        val startTimeInput = findViewById<TextInputEditText>(R.id.startTimeInput)
        val endTimeInput = findViewById<TextInputEditText>(R.id.endTimeInput)
        val locationInput = findViewById<TextInputEditText>(R.id.sessionLocationInput)
        val descriptionInput = findViewById<TextInputEditText>(R.id.sessionDescriptionInput)
        val subjectInput = findViewById<TextInputEditText>(R.id.subjectInput)

        val facultyDropdown = findViewById<AutoCompleteTextView>(R.id.facultyDropdown)

// Create an ArrayAdapter using the faculties list
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            faculties
        )

// Attach the adapter to the AutoCompleteTextView
        facultyDropdown.setAdapter(adapter)
        val yearInput = findViewById<TextInputEditText>(R.id.yearInput)
        val visibilityGroup = findViewById<MaterialButtonToggleGroup>(R.id.visibilityToggleGroup)
        val hostButton = findViewById<MaterialButton>(R.id.hostButton)

        setupVisibilityToggle(visibilityGroup)
        setupDateTimePickers(startTimeInput, endTimeInput)
        setupLocationPicker(locationInput)
        setupHostButton(
            hostButton,
            HostButtonInputs(nameInput, descriptionInput, subjectInput, yearInput)
        )
    }

    private fun setupVisibilityToggle(visibilityGroup: MaterialButtonToggleGroup) {
        visibilityGroup.check(R.id.privateButton)
        visibilityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                sessionVisibility = when (checkedId) {
                    R.id.privateButton -> SessionVisibility.PRIVATE
                    R.id.publicButton -> SessionVisibility.PUBLIC
                    else -> SessionVisibility.PRIVATE
                }
            }
        }
    }

    private fun setupDateTimePickers(
        startTimeInput: TextInputEditText,
        endTimeInput: TextInputEditText
    ) {
        startTimeInput.setOnClickListener {
            showDateTimePicker { selectedDate ->
                startDateMillis = selectedDate.time
                startTimeInput.setText(
                    SimpleDateFormat(
                        "MMM dd, yyyy hh:mm a",
                        Locale.getDefault()
                    ).format(selectedDate)
                )
            }
        }

        endTimeInput.setOnClickListener {
            showDateTimePicker { selectedDate ->
                endDateMillis = selectedDate.time
                endTimeInput.setText(
                    SimpleDateFormat(
                        "MMM dd, yyyy hh:mm a",
                        Locale.getDefault()
                    ).format(selectedDate)
                )
            }
        }
    }

    private fun setupLocationPicker(locationInput: TextInputEditText) {
        locationInput.setOnClickListener {
            Log.d(TAG, "Location field tapped")
            startActivityForResult(
                Intent(this, MapLocationPickerActivity::class.java),
                LOCATION_PICKER_REQUEST_CODE
            )
        }
    }

    data class SessionInputData(
        val name: String?,
        val startDateMillis: Long,
        val endDateMillis: Long,
        val selectedLatitude: Double?,
        val selectedLongitude: Double?,
        val subject: String?,
        val faculty: String?,
        val yearString: String?
    )

    private fun validateInputs(inputData: SessionInputData): Boolean {
        val nameInput = findViewById<TextInputEditText>(R.id.sessionNameInput)
        val startTimeInput = findViewById<TextInputEditText>(R.id.startTimeInput)
        val endTimeInput = findViewById<TextInputEditText>(R.id.endTimeInput)
        val subjectInput = findViewById<TextInputEditText>(R.id.subjectInput)
        val yearInput = findViewById<TextInputEditText>(R.id.yearInput)

        if (inputData.name.isNullOrEmpty()) {
            nameInput.error = "Session name is required"
            return false
        }

        if (inputData.startDateMillis == 0L) {
            startTimeInput.error = "Start time is required"
            return false
        }

        if (inputData.endDateMillis == 0L) {
            endTimeInput.error = "End time is required"
            return false
        }

        if (inputData.startDateMillis >= inputData.endDateMillis) {
            endTimeInput.error = "End time must be after start time"
            return false
        }

        if (inputData.selectedLatitude == null || inputData.selectedLongitude == null) {
            // Inform the user to pick a location first
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            return false
        }

        if (inputData.subject.isNullOrEmpty()) {
            subjectInput.error = "Subject is required"
            return false
        }

        if (inputData.yearString.isNullOrEmpty()) {
            yearInput.error = "Year is required"
            return false
        }

        val year = inputData.yearString.toIntOrNull()
        if (year == null || year < 1) {
            yearInput.error = "Valid year is required"
            return false
        }

        return true
    }

    data class HostButtonInputs(
        val nameInput: TextInputEditText,
        val descriptionInput: TextInputEditText,
        val subjectInput: TextInputEditText,
        val yearInput: TextInputEditText
    )

    private fun setupHostButton(hostButton: MaterialButton, inputs: HostButtonInputs) {
        hostButton.setOnClickListener {
            val name = inputs.nameInput.text?.toString()
            val description = inputs.descriptionInput.text?.toString()
            val subject = inputs.subjectInput.text?.toString()
            val facultyDropdown = findViewById<AutoCompleteTextView>(R.id.facultyDropdown)
            val faculty = facultyDropdown.text.toString()
            val yearString = inputs.yearInput.text?.toString()

            val inputData = SessionInputData(
                name = name,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                selectedLatitude = selectedLatitude,
                selectedLongitude = selectedLongitude,
                subject = subject,
                faculty = faculty,
                yearString = yearString
            )

            if (!validateInputs(inputData)) {
                return@setOnClickListener
            }

            val year = yearString!!.toInt()

            if (sessionVisibility == SessionVisibility.PRIVATE) {
                val intent = Intent(this, InviteFriendsActivity::class.java)
                intent.putExtra("SESSION_NAME", name)
                intent.putExtra("SESSION_DESCRIPTION", description ?: "")
                intent.putExtra("SESSION_LATITUDE", selectedLatitude)
                intent.putExtra("SESSION_LONGITUDE", selectedLongitude)
                intent.putExtra("SESSION_START_DATE", startDateMillis)
                intent.putExtra("SESSION_END_DATE", endDateMillis)
                intent.putExtra("SESSION_IS_PUBLIC", false)
                intent.putExtra("SESSION_SUBJECT", subject)
                intent.putExtra("SESSION_FACULTY", faculty)
                intent.putExtra("SESSION_YEAR", year)
                startActivityForResult(intent, FRIEND_SELECT_REQUEST_CODE)
            } else {
                val sessionDetails = SessionDetails(
                    name = name!!,
                    description = description ?: "",
                    latitude = selectedLatitude!!,
                    longitude = selectedLongitude!!,
                    startDate = Date(startDateMillis),
                    endDate = Date(endDateMillis),
                    isPublic = true,
                    subject = subject!!,
                    faculty = faculty!!,
                    year = year,
                    invitees = arrayListOf()
                )
                createSessionOnServer(sessionDetails)
            }
        }
    }

    private fun showDateTimePicker(onDateSelected: (Date) -> Unit) {
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        // Date picker dialog
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            // Time picker dialog
            TimePickerDialog(this, { _, hourOfDay, minute ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth, hourOfDay, minute)
                onDateSelected(selectedCalendar.time)
            }, currentHour, currentMinute, false).show()
        }, currentYear, currentMonth, currentDay).show()
    }

    data class SessionDetails(
        val name: String,
        val description: String,
        val latitude: Double,
        val longitude: Double,
        val startDate: Date,
        val endDate: Date,
        val isPublic: Boolean,
        val subject: String,
        val faculty: String,
        val year: Int,
        val invitees: ArrayList<String>
    )

    private fun createSessionOnServer(sessionDetails: SessionDetails) {
        val userId = LoginActivity.getCurrentUserId(this)

        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Creating session with hostId: $userId")
                val url = URL("${BuildConfig.SERVER_URL}/session")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonData = createSessionJsonData(sessionDetails, userId)

                val jsonString = jsonData.toString()
                Log.d(TAG, "Request body: $jsonString")

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonString)
                writer.flush()

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                val responseBody = if (responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error details"
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                Log.d(TAG, "Response body: $responseBody")

                handleServerResponse(responseCode, responseBody, sessionDetails)
                connection.disconnect()
            } catch (e: IOException) {
                Log.e(TAG, "Network error creating session", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CreateSessionActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: JSONException) {
                Log.e(TAG, "JSON error creating session", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CreateSessionActivity,
                        "JSON error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun createSessionJsonData(sessionDetails: SessionDetails, userId: String): JSONObject {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        return JSONObject().apply {
            put("name", sessionDetails.name)
            put("description", sessionDetails.description)
            put("hostId", userId)
            put("location", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(sessionDetails.longitude)
                    put(sessionDetails.latitude)
                })
            })
            put("dateRange", JSONObject().apply {
                put("startDate", dateFormat.format(sessionDetails.startDate))
                put("endDate", dateFormat.format(sessionDetails.endDate))
            })
            put("isPublic", sessionDetails.isPublic)
            put("subject", sessionDetails.subject)
            put("faculty", sessionDetails.faculty)
            put("year", sessionDetails.year)
            put("invitees", JSONArray().apply {
                sessionDetails.invitees.forEach { friendId ->
                    put(friendId)
                }
            })
        }
    }

    private suspend fun handleServerResponse(
        responseCode: Int,
        responseBody: String,
        sessionDetails: SessionDetails
    ) {
        withContext(Dispatchers.Main) {
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val responseJson = JSONObject(responseBody)
                val sessionJson = responseJson.optJSONObject("session")

                if (sessionJson != null) {
                    val sessionLocation = "${
                        sessionDetails.latitude.toString().take(7)
                    }, ${sessionDetails.longitude.toString().take(7)}"
                    val sessionTime = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                        .format(sessionDetails.startDate) + " - " +
                            SimpleDateFormat(
                                "hh:mm a",
                                Locale.getDefault()
                            ).format(sessionDetails.endDate)

                    val session = Session(
                        name = sessionDetails.name,
                        time = sessionTime,
                        location = sessionLocation,
                        description = sessionDetails.description,
                        visibility = if (sessionDetails.isPublic) SessionVisibility.PUBLIC else SessionVisibility.PRIVATE
                    )

                    setResult(RESULT_OK, Intent().apply {
                        putExtra("SESSION_NAME", session.name)
                        putExtra("SESSION_TIME", session.time)
                        putExtra("SESSION_LOCATION", session.location)
                        putExtra("SESSION_DESCRIPTION", session.description)
                        putExtra("SESSION_VISIBILITY", session.visibility)
                    })

                    Toast.makeText(
                        this@CreateSessionActivity,
                        "Session created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@CreateSessionActivity,
                        "Unexpected server response format",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                try {
                    val errorJson = JSONObject(responseBody)
                    val errorMessage = errorJson.optString("message", "Failed to create session")
                    Toast.makeText(this@CreateSessionActivity, errorMessage, Toast.LENGTH_SHORT)
                        .show()
                } catch (e: JSONException) {
                    Toast.makeText(
                        this@CreateSessionActivity,
                        "Failed to create session: $responseCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOCATION_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            val lat = data?.getDoubleExtra("LATITUDE", 0.0)
            val lng = data?.getDoubleExtra("LONGITUDE", 0.0)
            if (lat != null && lng != null) {
                selectedLatitude = lat
                selectedLongitude = lng
                findViewById<TextInputEditText>(R.id.sessionLocationInput).setText(
                    "Lat: ${
                        lat.toString().take(7)
                    }, Lng: ${lng.toString().take(7)}"
                )
            }
        } else if (requestCode == FRIEND_SELECT_REQUEST_CODE && resultCode == RESULT_OK) {
            // Get all the session data and selected friends
            val name = data?.getStringExtra("SESSION_NAME") ?: ""
            val description = data?.getStringExtra("SESSION_DESCRIPTION") ?: ""
            val latitude = data?.getDoubleExtra("SESSION_LATITUDE", 0.0) ?: 0.0
            val longitude = data?.getDoubleExtra("SESSION_LONGITUDE", 0.0) ?: 0.0
            val startDate = Date(data?.getLongExtra("SESSION_START_DATE", 0L) ?: 0L)
            val endDate = Date(data?.getLongExtra("SESSION_END_DATE", 0L) ?: 0L)
            val subject = data?.getStringExtra("SESSION_SUBJECT") ?: ""
            val faculty = data?.getStringExtra("SESSION_FACULTY") ?: ""
            val year = data?.getIntExtra("SESSION_YEAR", 1) ?: 1

            // Get selected friend IDs
            val selectedFriendIds =
                data?.getStringArrayExtra("SELECTED_FRIEND_IDS")?.toList() ?: listOf()

            // Create the session with selected invitees
            val sessionDetails = SessionDetails(
                name = name,
                description = description,
                latitude = latitude,
                longitude = longitude,
                startDate = startDate,
                endDate = endDate,
                isPublic = false,
                subject = subject,
                faculty = faculty,
                year = year,
                invitees = ArrayList(selectedFriendIds)
            )

            createSessionOnServer(sessionDetails)
        }
    }

    @VisibleForTesting
    fun setTestDates(startMillis: Long, endMillis: Long) {
        startDateMillis = startMillis
        endDateMillis = endMillis
    }

}