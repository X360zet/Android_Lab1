import java.io.FileInputStream
import java.util.Properties

// Ключ ИИ берётся из local.properties (этот файл не должен попадать в git)
// либо из переменной окружения AI_PLANNER_API_KEY.
val aiProps = Properties()
val aiLocalFile = rootProject.file("local.properties")
if (aiLocalFile.exists()) {
    FileInputStream(aiLocalFile).use { aiProps.load(it) }
}
val aiPlannerApiKey: String =
    aiProps.getProperty("AI_PLANNER_API_KEY")
        ?: System.getenv("AI_PLANNER_API_KEY")
        ?: ""

extra["aiPlannerApiKey"] = aiPlannerApiKey

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
