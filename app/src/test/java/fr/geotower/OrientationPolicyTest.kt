package fr.geotower

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OrientationPolicyTest {
    @Test
    fun mainActivityDoesNotLockOrientation() {
        val manifest = File("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val document = factory.newDocumentBuilder().parse(manifest)
        val activities = document.getElementsByTagName("activity")
        val mainActivity = (0 until activities.length)
            .map { activities.item(it) as org.w3c.dom.Element }
            .firstOrNull { activity ->
                activity.getAttributeNS(ANDROID_NS, "name") == ".MainActivity"
            }

        assertNotNull("MainActivity must be declared in AndroidManifest.xml", mainActivity)
        assertEquals("", mainActivity!!.getAttributeNS(ANDROID_NS, "screenOrientation"))
        assertFalse(mainActivity.getAttributeNS(TOOLS_NS, "ignore").contains("LockedOrientationActivity"))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
