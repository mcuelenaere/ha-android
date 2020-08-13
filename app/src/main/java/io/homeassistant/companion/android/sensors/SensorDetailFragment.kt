package io.homeassistant.companion.android.sensors

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.util.PermissionManager

class SensorDetailFragment(
    private val sensorRegistration: SensorRegistration<Any>,
    private val permissions: Array<String>
) :
    PreferenceFragmentCompat() {

    companion object {
        fun newInstance(
            sensorRegistration: SensorRegistration<Any>,
            permissions: Array<String>
        ): SensorDetailFragment {
            return SensorDetailFragment(sensorRegistration, permissions)
        }
    }

    private lateinit var sensorDao: SensorDao

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

        addPreferencesFromResource(R.xml.sensor_detail)

        findPreference<SwitchPreference>("enabled")?.let {
            val dao = sensorDao.get(sensorRegistration.uniqueId)
            val perm = havePermission()
            if (dao == null) {
                updateSensorEntity(perm)
                it.isChecked = perm
            } else {
                it.isChecked = dao.enabled
            }

            it.setOnPreferenceChangeListener { _, newState ->
                val isEnabled = newState as Boolean

                if (isEnabled && !havePermission()) {
                    requestPermissions(permissions, 0)
                    return@setOnPreferenceChangeListener false
                }

                updateSensorEntity(isEnabled)

                return@setOnPreferenceChangeListener true
            }
        }

        refreshSensorData()
    }

    private fun refreshSensorData() {
        findPreference<Preference>("unique_id")?.let {
            it.summary = sensorRegistration.uniqueId
        }
        findPreference<Preference>("state")?.let {
            if (sensorRegistration.unitOfMeasurement.isNullOrBlank())
                it.summary = sensorRegistration.state.toString()
            else
                it.summary =
                    sensorRegistration.state.toString() + " " + sensorRegistration.unitOfMeasurement
        }
        findPreference<Preference>("device_class")?.let {
            it.summary = sensorRegistration.deviceClass
        }
        findPreference<Preference>("icon")?.let {
            it.summary = sensorRegistration.icon
        }

        findPreference<PreferenceCategory>("attributes")?.let {
            if (sensorRegistration.attributes.isEmpty())
                it.isVisible = false
            else {
                sensorRegistration.attributes.keys.forEach { key ->
                    val pref = Preference(requireContext())
                    pref.title = key
                    pref.summary = sensorRegistration.attributes[key]?.toString() ?: ""
                    pref.isIconSpaceReserved = false

                    it.addPreference(pref)
                }
            }
        }
    }

    private fun updateSensorEntity(
        isEnabled: Boolean
    ) {
        var sensorEntity = sensorDao.get(sensorRegistration.uniqueId)
        if (sensorEntity != null) {
            sensorEntity.enabled = isEnabled
            sensorEntity.state = sensorRegistration.state.toString()
            sensorDao.update(sensorEntity)
        } else {
            sensorEntity = Sensor(
                sensorRegistration.uniqueId,
                isEnabled,
                false,
                sensorRegistration.state.toString()
            )
            sensorDao.add(sensorEntity)
        }
    }

    private fun havePermission(): Boolean {
        if (!permissions.isNullOrEmpty()) {
            return permissions.all { PermissionManager.hasPermission(requireContext(), it) }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        findPreference<SwitchPreference>("enabled")?.run {
            isChecked = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            updateSensorEntity(isChecked)
        }
    }
}