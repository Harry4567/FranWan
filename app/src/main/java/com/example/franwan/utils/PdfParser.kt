package com.example.franwan.utils

import android.content.Context
import android.util.Log
import com.example.franwan.ClassItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfParser(private val context: Context) {
    
    companion object {
        private const val TAG = "PdfParser"
        
        // Initialiser PDFBox (à appeler une seule fois dans l'application)
        fun initPdfBox(context: Context) {
            PDFBoxResourceLoader.init(context)
        }
    }
    
    /**
     * Parse un fichier PDF et extrait les informations de cours
     */
    fun parsePdf(inputStream: InputStream): List<ClassItem> {
        val courses = mutableListOf<ClassItem>()
        
        try {
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            
            Log.d(TAG, "PDF text extracted: ${text.length} characters")
            
            // Analyser le texte extrait pour trouver les cours
            val lines = text.split("\n")
            for (line in lines) {
                val course = parseLine(line.trim())
                if (course != null) {
                    courses.add(course)
                    Log.d(TAG, "Course found: $course")
                }
            }
            
            document.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PDF", e)
        }
        
        return courses
    }
    
    /**
     * Parse une ligne de texte pour extraire les informations d'un cours
     * Format attendu: "Jour Heure Cours Salle" (ex: "Lundi 08:00 Mathématiques A101")
     */
    private fun parseLine(line: String): ClassItem? {
        if (line.isBlank() || line.length < 10) return null
        
        // Patterns courants pour les emplois du temps
        val patterns = listOf(
            // Format: "Lundi 08:00 Mathématiques A101"
            Regex("""(Lundi|Mardi|Mercredi|Jeudi|Vendredi|Samedi|Dimanche)\s+(\d{1,2}:\d{2})\s+(.+?)\s+([A-Z]\d{1,3})"""),
            // Format: "Lundi 08:00-10:00 Mathématiques A101"
            Regex("""(Lundi|Mardi|Mercredi|Jeudi|Vendredi|Samedi|Dimanche)\s+(\d{1,2}:\d{2})-\d{1,2}:\d{2}\s+(.+?)\s+([A-Z]\d{1,3})"""),
            // Format: "Lundi 8h00 Mathématiques A101"
            Regex("""(Lundi|Mardi|Mercredi|Jeudi|Vendredi|Samedi|Dimanche)\s+(\d{1,2}h\d{2})\s+(.+?)\s+([A-Z]\d{1,3})""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(line)
            if (match != null) {
                val (day, time, course, room) = match.destructured
                return ClassItem(
                    day = day,
                    time = normalizeTime(time),
                    course = course.trim(),
                    room = room.trim()
                )
            }
        }
        
        return null
    }
    
    /**
     * Normalise le format de l'heure
     */
    private fun normalizeTime(time: String): String {
        return when {
            time.contains("h") -> {
                // Convertir "8h00" en "08:00"
                val parts = time.split("h")
                val hour = parts[0].padStart(2, '0')
                val minute = parts[1].padStart(2, '0')
                "$hour:$minute"
            }
            else -> time
        }
    }
    
    /**
     * Valide si une ligne contient potentiellement des informations de cours
     */
    private fun isValidCourseLine(line: String): Boolean {
        val dayPattern = Regex("""(Lundi|Mardi|Mercredi|Jeudi|Vendredi|Samedi|Dimanche)""")
        val timePattern = Regex("""\d{1,2}[:h]\d{2}""")
        
        return dayPattern.containsMatchIn(line) && timePattern.containsMatchIn(line)
    }
}
