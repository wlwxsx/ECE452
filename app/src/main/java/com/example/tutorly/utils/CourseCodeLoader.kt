package com.example.tutorly.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object CourseCodeLoader {
    private const val TAG = "CourseCodeLoader"
    private var courseCodes: List<String> = emptyList()
    private var subjects: List<String> = emptyList()
    
    fun loadCourseCodes(context: Context): List<String> {
        if (courseCodes.isNotEmpty()) {
            return courseCodes
        }
        
        return try {
            val inputStream = context.assets.open("course_codes.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            
            val jsonString = String(buffer, Charsets.UTF_8)
            val jsonArray = JSONArray(jsonString)
            
            val codes = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val course = jsonObject.getString("course")
                codes.add(course)
            }
            
            courseCodes = codes.sorted()
            subjects = courseCodes.map { it.split(" ")[0] }.distinct().sorted()
            Log.d(TAG, "Loaded ${courseCodes.size} course codes and ${subjects.size} subjects")
            courseCodes
        } catch (e: Exception) {
            Log.e(TAG, "Error loading course codes: ${e.message}")
            emptyList()
        }
    }
    
    fun getCourseCodes(): List<String> = courseCodes
    
    fun getSubjects(): List<String> {
        if (subjects.isEmpty() && courseCodes.isNotEmpty()) {
            subjects = courseCodes.map { it.split(" ")[0] }.distinct().sorted()
        }
        return subjects
    }
    
    fun getCourseCodesForSubject(subject: String): List<String> {
        return courseCodes.filter { it.startsWith(subject) }
            .map { it.split(" ")[1] } // Extract just the course number
            .distinct()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE } // Sort numerically
    }
    
    fun getFullCourseCode(subject: String, courseCode: String): String {
        return "$subject $courseCode"
    }
} 