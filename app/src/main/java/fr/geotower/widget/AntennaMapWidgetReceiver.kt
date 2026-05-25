package fr.geotower.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AntennaMapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AntennaMapWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancelPeriodicUpdateIfNoWidgetsRemain(context)
    }
}
