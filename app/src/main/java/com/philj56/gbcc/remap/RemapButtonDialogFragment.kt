package com.philj56.gbcc.remap

import android.app.Dialog
import android.os.Bundle
import android.widget.RadioGroup
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.philj56.gbcc.R
import com.philj56.gbcc.RemapActivity

class RemapButtonDialogFragment : DialogFragment() {
    companion object {
        private const val ARG_KEY = "key"
        private const val ARG_ANALOGUE = "analogue"

        // The data goes in the arguments, so that Android can recreate the dialog
        fun newInstance(key: String, analogue: Boolean) = RemapButtonDialogFragment().apply {
            arguments = bundleOf(ARG_KEY to key, ARG_ANALOGUE to analogue)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val key = requireArguments().getString(ARG_KEY) ?: ""
        val analogue = requireArguments().getBoolean(ARG_ANALOGUE)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val mapping = prefs.getString(key, "unmapped")
        val buttonNames: Array<String>
        val buttonValues: Array<String>
        if (analogue) {
            buttonNames = resources.getStringArray(R.array.button_map_analogue_names_array)
            buttonValues = resources.getStringArray(R.array.button_map_analogue_values_array)
        } else {
            buttonNames = resources.getStringArray(R.array.button_map_names_array)
            buttonValues = resources.getStringArray(R.array.button_map_values_array)
        }

        val layout = requireActivity().layoutInflater.inflate(R.layout.dialog_remap_button, null, false)
        val radioGroup = layout.findViewById<RadioGroup>(R.id.remapDialogRadioGroup)
        buttonNames.forEachIndexed { index, name ->
            val radio = MaterialRadioButton(radioGroup.context)
            radio.text = name
            radio.id = index
            radioGroup.addView(radio)

            if (mapping == buttonValues[index]) {
                radioGroup.check(index)
            }
        }

        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.select_mapping)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit {
                    putString(key, buttonValues[radioGroup.checkedRadioButtonId])
                    apply()
                }
                (activity as? RemapActivity)?.updateButtonText(key)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setView(layout)

        return builder.create()
    }
}