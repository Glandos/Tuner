/*
 * Copyright 2020 Michael Moessner
 *
 * This file is part of Tuner.
 *
 * Tuner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tuner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tuner.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.tuner

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.preference.*
import kotlin.math.pow
import kotlin.math.roundToInt

fun indexToWindowSize(index: Int): Int {
  return 2f.pow(7 + index).roundToInt()
}

class SettingsFragment : PreferenceFragmentCompat() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences, rootKey)
    setHasOptionsMenu(true)
  }

  override fun onPrepareOptionsMenu(menu : Menu) {
//        super.onPrepareOptionsMenu(menu)
    menu.findItem(R.id.action_settings)?.isVisible = false
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

    val appearance = findPreference<ListPreference?>("appearance")
      ?: throw RuntimeException("No appearance preference")

    appearance.summaryProvider =
      Preference.SummaryProvider<ListPreference> { preference ->
        when (preference?.value) {
          "dark" -> getString(R.string.dark_appearance)
          "light" -> getString(R.string.light_appearance)
          else -> getString(R.string.system_appearance)
        }
      }

    appearance.setOnPreferenceChangeListener { _, _ ->
      val act = activity as MainActivity?
      act?.recreate()
      true
    }

    val a4Frequency = findPreference<EditTextPreference?>("a4_frequency")
    a4Frequency?.setOnBindEditTextListener { editText ->
      editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    a4Frequency?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
      getString(R.string.hertz_str, preference?.text ?: "440")
    }

    val windowingFunction = findPreference<ListPreference?>("windowing")
    windowingFunction?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

    val windowSize = findPreference<SeekBarPreference>("window_size") ?: throw RuntimeException("No window_size preference")
    windowSize.setOnPreferenceChangeListener { preference, newValue ->
      preference.summary = getWindowSizeSummary(newValue as Int)
      true
    }
    windowSize.summary = getWindowSizeSummary(windowSize.value)

    val overlap = findPreference<SeekBarPreference?>("overlap") ?: throw RuntimeException("No overlap preference")
    overlap.setOnPreferenceChangeListener { preference, newValue ->
      preference.summary = getString(R.string.percent, newValue as Int)
      true
    }
    overlap.summary = getString(R.string.percent, overlap.value)

    return super.onCreateView(inflater, container, savedInstanceState)
  }

  private fun getWindowSizeSummary(windowSizeIndex: Int): String {
    val s = indexToWindowSize(windowSizeIndex)
    return "$s (" + getString(R.string.minimum_frequency) + getString(R.string.hertz, 44100f / s) + ")"
  }
}