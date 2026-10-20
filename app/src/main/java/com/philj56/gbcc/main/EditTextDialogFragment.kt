package com.philj56.gbcc.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.postDelayed
import androidx.core.widget.addTextChangedListener
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.R

/*
 * The text is delivered as a fragment result under the request key (plus the path, if one was
 * given), and an empty result under dismissKey(requestKey) when the dialog closes.
 * Everything goes through the arguments, so that Android can recreate the dialog
 * e.g. after a rotation.
 */
class EditTextDialogFragment : DialogFragment() {
    companion object {
        const val RESULT_TEXT = "text"
        const val RESULT_PATH = "path"
        private const val ARG_TITLE = "title"
        private const val ARG_INITIAL_TEXT = "initialText"
        private const val ARG_REQUEST_KEY = "requestKey"
        private const val ARG_PATH = "path"
        private const val ARG_HINT = "hint"

        fun dismissKey(requestKey: String) = "$requestKey.dismiss"

        fun newInstance(title: Int, initialText: String, requestKey: String, path: String? = null, hint: Int = 0) =
            EditTextDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_INITIAL_TEXT to initialText,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PATH to path,
                    ARG_HINT to hint
                )
            }
    }

    private val requestKey get() = requireArguments().getString(ARG_REQUEST_KEY) ?: ""

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = requireArguments().getInt(ARG_TITLE)
        val initialText = requireArguments().getString(ARG_INITIAL_TEXT) ?: ""
        val path = requireArguments().getString(ARG_PATH)
        val onConfirm = { text: String ->
            setFragmentResult(requestKey, bundleOf(RESULT_TEXT to text, RESULT_PATH to path))
        }
        return activity?.let {
            val textView = it.layoutInflater.inflate(R.layout.dialog_create_folder, null, false)
            val input = textView?.findViewById<EditText>(R.id.createFolderInput)
            requireArguments().getInt(ARG_HINT).takeIf { hint -> hint != 0 }?.let { hint -> input?.setHint(hint) }
            input?.setText(initialText)

            val builder = MaterialAlertDialogBuilder(it)
            builder.setTitle(title)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onConfirm(input?.text.toString())
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setView(textView)

            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            }

            input?.addTextChangedListener { editor ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    editor?.isNotBlank() ?: false
            }
            input?.setOnFocusChangeListener { v, hasFocus ->
                v.postDelayed(50) {
                    if (hasFocus) {
                        val imm =
                            context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(v, 0)
                    }
                }
            }
            input?.requestFocus()

            return dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDismiss(dialog: DialogInterface) {
        setFragmentResult(dismissKey(requestKey), Bundle())
        super.onDismiss(dialog)
    }
}