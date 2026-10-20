/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

package com.philj56.gbcc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.databinding.ActivityMainBinding
import com.philj56.gbcc.databinding.DialogRomActionsBinding
import com.philj56.gbcc.main.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BACK_DELAY: Int = 2000
private const val SAVE_DIR: String = "saves"
const val IMPORTED_SAVE_SUBDIR: String = "imported"
private const val REQUEST_NEW_GROUP = "newGroup"
private const val REQUEST_RENAME_GROUP = "renameGroup"

// Only saves are imported: the games stay in the ROM folder
private val IMPORTABLE_REGEX = Regex(".*\\.(sav|s[0-9])", RegexOption.IGNORE_CASE)
private val SAVE_EXTENSION_REGEX = Regex("sav|s[0-9]+", RegexOption.IGNORE_CASE)

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var watcher: RomFolderWatcher
    private var treeUri: Uri? = null
    // Every ROM in the folder and its subfolders, as of the last scan (null: the folder can't be read)
    private var allRoms: List<RomEntry>? = emptyList()
    private var groups = mutableListOf<RomGroup>()
    private lateinit var selectedGroup: String

    private var timeBackPressed: Long = 0

    private val fileAdapter = FileAdapter(
        onClick = { entry -> launchGame(entry) },
        onShowActions = { entry -> showRomActionsDialog(entry) }
    )

    private val chooseFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            RomFolder.choose(this, uri)
            openRomFolder()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_controls, true)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_image, true)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_sound, true)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_advanced, true)
        AppCompatDelegate.setDefaultNightMode(
            when (PreferenceManager.getDefaultSharedPreferences(this)
                .getString("night_mode", null)) {
                "on" -> AppCompatDelegate.MODE_NIGHT_YES
                "off" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback { backPress() }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mainToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        binding.chooseFolderButton.setOnClickListener { selectRomFolder() }

        binding.fileList.adapter = fileAdapter
        binding.fileList.itemAnimator = null

        groups = RomGroups.load(this)
        selectedGroup = RomGroups.selected(this)
        binding.groupChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val key = checkedIds.firstOrNull()?.let { group.findViewById<View>(it)?.tag as? String }
            if (key != null && key != selectedGroup) {
                selectedGroup = key
                RomGroups.select(this, key)
                showRoms()
            }
        }

        watcher = RomFolderWatcher(this) { roms ->
            allRoms = roms
            updateGroupChips()
            showRoms()
        }

        supportFragmentManager.setFragmentResultListener(REQUEST_NEW_GROUP, this) { _, result ->
            val name = result.getString(EditTextDialogFragment.RESULT_TEXT)?.trim()
            // When created from a game's menu, the game goes in the new group
            val romName = result.getString(EditTextDialogFragment.RESULT_PATH)
            if (!name.isNullOrEmpty()) {
                createGroup(name, romName)
            }
        }
        supportFragmentManager.setFragmentResultListener(REQUEST_RENAME_GROUP, this) { _, result ->
            val oldName = result.getString(EditTextDialogFragment.RESULT_PATH)
            val newName = result.getString(EditTextDialogFragment.RESULT_TEXT)?.trim()
            if (oldName != null && !newName.isNullOrEmpty()) {
                renameGroup(oldName, newName)
            }
        }

        if (savedInstanceState == null) {
            // No game is open, so any ROM left in the cache is stale
            GLActivity.clearRomCache(this)
        }
        openRomFolder()

        // Check if the version has changed since last launch, and perform some setup if so
        Thread {
            val lastLaunchVersion = filesDir.resolve("lastLaunchVersion")
            if (!lastLaunchVersion.exists()
                || !lastLaunchVersion.readText().startsWith("${BuildConfig.VERSION_CODE}")
            ) {
                lastLaunchVersion.createNewFile()
                lastLaunchVersion.writeText("${BuildConfig.VERSION_CODE}\n")

                assets.list("shaders")?.forEach { shader ->
                    File("$filesDir/shaders").mkdirs()
                    assets.open("shaders/$shader").use { iStream ->
                        val file = File("$filesDir/shaders/$shader")
                        FileOutputStream(file).use { iStream.copyTo(it) }
                    }
                }
            }
        }.start()
    }

    override fun onStart() {
        super.onStart()
        // Picks up any change made to the folder while the app was in the background,
        // and keeps watching it while it's visible
        treeUri?.let { watcher.watch(it) }
    }

    override fun onStop() {
        watcher.stop()
        super.onStop()
    }

    override fun onPause() {
        supportFragmentManager.fragments.forEach {
            if (it is DialogFragment) {
                it.dismissAllowingStateLoss()
            }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        delegate.applyDayNight()
    }

    private fun openRomFolder() {
        val tree = RomFolder.treeUri(this)
        treeUri = tree
        if (tree == null) {
            allRoms = if (RomFolder.wasChosen(this)) null else emptyList()
            updateGroupChips()
            showRoms()
            return
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            watcher.watch(tree)
        }
    }

    /** Rebuilds the row of groups: all games, GBC and GB if there are any, and the user's own. */
    private fun updateGroupChips() {
        val roms = allRoms
        binding.groupScroll.visibility = if (treeUri == null || roms.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (roms == null) {
            return
        }
        val chips = ArrayList<Pair<String, String>>()
        chips.add(RomGroups.ALL to getString(R.string.group_all))
        if (roms.any { it.isGbc }) chips.add(RomGroups.GBC to getString(R.string.group_gbc))
        if (roms.any { !it.isGbc }) chips.add(RomGroups.GB to getString(R.string.group_gb))
        groups.forEach { chips.add(RomGroups.customKey(it.name) to it.name) }
        if (chips.none { it.first == selectedGroup }) {
            // The group was deleted, or its games are gone
            selectedGroup = RomGroups.ALL
            RomGroups.select(this, selectedGroup)
        }

        binding.groupChips.removeAllViews()
        chips.forEach { (key, label) ->
            val chip = layoutInflater.inflate(R.layout.chip_group, binding.groupChips, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = key
            chip.text = label
            RomGroups.customName(key)?.let { name ->
                chip.setOnLongClickListener {
                    showGroupActionsDialog(name)
                    true
                }
            }
            binding.groupChips.addView(chip)
            if (key == selectedGroup) {
                binding.groupChips.check(chip.id)
            }
        }
        val addChip = layoutInflater.inflate(R.layout.chip_group_add, binding.groupChips, false) as Chip
        addChip.setOnClickListener { showNewGroupDialog(null) }
        binding.groupChips.addView(addChip)
    }

    private fun showRoms() {
        val roms = allRoms
        when {
            treeUri == null && roms != null -> {
                fileAdapter.submitList(emptyList())
                showEmptyState(R.string.rom_folder_prompt, true)
            }
            roms == null -> {
                // The folder can't be read any more (deleted, or the permission was revoked)
                fileAdapter.submitList(emptyList())
                showEmptyState(R.string.rom_folder_lost, true)
            }
            roms.isEmpty() -> {
                fileAdapter.submitList(emptyList())
                showEmptyState(R.string.rom_folder_empty, true)
            }
            else -> {
                val shown = RomHistory.sortRecentFirst(
                    RomGroups.filter(selectedGroup, roms, groups) ?: roms,
                    RomHistory.lastPlayed(this)
                )
                fileAdapter.gamesWithSaves = filesDir.resolve(SAVE_DIR)
                    .list { _, name -> name.endsWith(".sav") }
                    ?.map { it.removeSuffix(".sav") }?.toSet() ?: emptySet()
                fileAdapter.submitList(shown)
                if (shown.isEmpty()) {
                    showEmptyState(R.string.group_empty, false)
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.fileList.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showEmptyState(text: Int, showButton: Boolean) {
        binding.emptyText.setText(text)
        binding.chooseFolderButton.visibility = if (showButton) View.VISIBLE else View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.fileList.visibility = View.GONE
    }

    private fun launchGame(entry: RomEntry) {
        val tree = treeUri ?: return
        startActivity(GLActivity.intent(this, RomFolder.documentUri(tree, entry.documentId), entry.name))
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.chooseFolderItem -> selectRomFolder()
            R.id.importItem -> selectImportFiles()
            R.id.exportItem -> selectExportDir()
            R.id.settingsItem -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    private fun selectRomFolder() {
        // Start the picker in the current folder, if there is one
        val initial = treeUri?.let { RomFolder.documentUri(it, DocumentsContract.getTreeDocumentId(it)) }
        try {
            chooseFolder.launch(initial)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                baseContext,
                getString(R.string.message_no_files_app),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showNewGroupDialog(romName: String?) {
        EditTextDialogFragment.newInstance(
            R.string.new_group, "", REQUEST_NEW_GROUP, romName, R.string.group_name_hint
        ).show(supportFragmentManager, "")
    }

    private fun createGroup(name: String, romName: String?) {
        if (groups.any { it.name == name }) {
            Toast.makeText(baseContext, getString(R.string.group_exists, name), Toast.LENGTH_SHORT).show()
            return
        }
        val group = RomGroup(name)
        romName?.let { group.roms.add(it) }
        groups.add(group)
        RomGroups.save(this, groups)
        if (romName == null) {
            // A group made from the row of groups is shown right away
            selectedGroup = RomGroups.customKey(name)
            RomGroups.select(this, selectedGroup)
        }
        updateGroupChips()
        showRoms()
    }

    private fun showGroupActionsDialog(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(arrayOf(getString(R.string.rename_group), getString(R.string.delete_group))) { _, which ->
                when (which) {
                    0 -> EditTextDialogFragment.newInstance(
                        R.string.rename_group, name, REQUEST_RENAME_GROUP, name, R.string.group_name_hint
                    ).show(supportFragmentManager, "")
                    1 -> MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.delete_group_confirmation, name))
                        .setPositiveButton(R.string.delete) { _, _ -> deleteGroup(name) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun renameGroup(oldName: String, newName: String) {
        if (oldName == newName) {
            return
        }
        if (groups.any { it.name == newName }) {
            Toast.makeText(baseContext, getString(R.string.group_exists, newName), Toast.LENGTH_SHORT).show()
            return
        }
        val index = groups.indexOfFirst { it.name == oldName }
        if (index < 0) {
            return
        }
        groups[index] = groups[index].copy(name = newName)
        RomGroups.save(this, groups)
        if (selectedGroup == RomGroups.customKey(oldName)) {
            selectedGroup = RomGroups.customKey(newName)
            RomGroups.select(this, selectedGroup)
        }
        updateGroupChips()
        showRoms()
    }

    private fun deleteGroup(name: String) {
        groups.removeAll { it.name == name }
        RomGroups.save(this, groups)
        updateGroupChips()
        showRoms()
    }

    /** Lets the user tick the groups a game belongs to. */
    private fun showRomGroupsDialog(entry: RomEntry) {
        if (groups.isEmpty()) {
            showNewGroupDialog(entry.name)
            return
        }
        val names = groups.map { it.name }.toTypedArray()
        val checked = groups.map { entry.name in it.roms }.toBooleanArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.baseName)
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                groups.forEachIndexed { i, group ->
                    if (checked[i]) group.roms.add(entry.name) else group.roms.remove(entry.name)
                }
                RomGroups.save(this, groups)
                showRoms()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.new_group) { _, _ ->
                groups.forEachIndexed { i, group ->
                    if (checked[i]) group.roms.add(entry.name) else group.roms.remove(entry.name)
                }
                RomGroups.save(this, groups)
                showNewGroupDialog(entry.name)
            }
            .show()
    }

    private fun showRomActionsDialog(entry: RomEntry) {
        val binding = DialogRomActionsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .show()

        binding.buttonGroups.setOnClickListener {
            showRomGroupsDialog(entry)
            dialog.dismiss()
        }
        binding.buttonDeleteSave.setOnClickListener {
            showSaveDeleteDialog(entry)
            dialog.dismiss()
        }
        binding.buttonEditConfig.setOnClickListener {
            val intent = Intent(this, RomConfigActivity::class.java).apply {
                putExtra("file", entry.name)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        binding.buttonAddShortcut.setOnClickListener {
            addShortcut(entry)
            dialog.dismiss()
        }
    }

    private fun addShortcut(entry: RomEntry) {
        val tree = treeUri ?: return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            return
        }
        val iconResource = if (entry.extension.equals("gbc", ignoreCase = true)) {
            R.drawable.ic_game_shortcut_gbc
        } else {
            R.drawable.ic_game_shortcut_dmg
        }
        val icon =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val iconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 108f, resources.displayMetrics).toInt()
                val borderSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()

                val drawable = AdaptiveIconDrawable(
                    IconCompat.createWithResource(this, R.drawable.ic_launcher_background).loadDrawable(this),
                    IconCompat.createWithResource(this, iconResource).loadDrawable(this),
                )
                drawable.setBounds(borderSize, borderSize, iconSize - borderSize, iconSize - borderSize)

                val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                drawable.draw(Canvas(bitmap))
                IconCompat.createWithAdaptiveBitmap(bitmap)
            } else {
                IconCompat.createWithResource(this, iconResource)
            }
        val shortcut = ShortcutInfoCompat.Builder(this, "launch_${entry.documentId}")
            .setShortLabel(entry.baseName)
            .setLongLabel(entry.baseName)
            .setIcon(icon)
            .setIntents(
                arrayOf(
                    /* Clear any existing activities, and launch the game */
                    Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                    GLActivity.intent(this, RomFolder.documentUri(tree, entry.documentId), entry.name).apply {
                        action = resources.getString(R.string.launch_game_intent)
                    }
                )
            )
            .build()

        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    private fun showSaveDeleteDialog(entry: RomEntry) {
        MaterialAlertDialogBuilder(this)
            .setMessage(resources.getQuantityString(R.plurals.delete_save_confirmation, 1, 1))
            .setPositiveButton(R.string.delete) { _, _ -> performSaveDelete(entry.baseName) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun performSaveDelete(baseName: String) {
        val saveDir = filesDir.resolve(SAVE_DIR)
        // Only this game's battery save and save states: a prefix match would also
        // delete the saves of e.g. "Game 2" when deleting those of "Game"
        val saveRegex = Regex(Regex.escape(baseName) + "\\.(sav|s[0-9]+)")
        saveDir.listFiles { _, name ->
            name.matches(saveRegex)
        }?.forEach { save ->
            save.delete()
        }
        showRoms()
    }

    private fun selectImportFiles() {
        try {
            performImport.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                baseContext,
                getString(R.string.message_no_files_app),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun selectExportDir() {
        val saveDir = filesDir.resolve(SAVE_DIR)
        if (!saveDir.isDirectory || (saveDir.list()?.none { it.endsWith(".sav") } == true)) {
            Toast.makeText(baseContext, getString(R.string.message_no_export), Toast.LENGTH_SHORT)
                .show()
            return
        }
        try {
            performExport.launch("application/zip")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                baseContext,
                getString(R.string.message_no_files_app),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }

    private fun importFile(uri: Uri) {
        fun doCopy(input: InputStream, entryName: String) {
            // The core only looks for lowercase save extensions (".sav", ".s0"...)
            val extension = File(entryName).extension
            val name = entryName.dropLast(extension.length) + extension.lowercase()
            val file = filesDir.resolve(SAVE_DIR).resolve(IMPORTED_SAVE_SUBDIR).resolve(File(name).name)
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        fun importZipWithCharset(iStream: InputStream, charset: Charset) : Exception? {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ZipInputStream(iStream, charset)
                } else {
                    ZipInputStream(iStream)
                }.use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.matches(IMPORTABLE_REGEX)) {
                            doCopy(zip, entry.name)
                        }
                        entry = zip.nextEntry
                    }
                }
            } catch (e: IllegalArgumentException) {
                return e
            } catch (e: ZipException) {
                return e
            } catch (e: IOException) {
                return e
            }
            return null
        }

        fun showImportFailToast() {
            runOnUiThread {
                Toast.makeText(
                    baseContext,
                    getString(R.string.message_failed_import, uri),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val name = getFileName(uri)
        when {
            name == null -> {
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        getString(R.string.message_failed_name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            name.matches(IMPORTABLE_REGEX) -> {
                val iStream =
                    try {
                        contentResolver.openInputStream(uri)
                    } catch (e: FileNotFoundException) {
                        null
                    }
                if (iStream == null) {
                    showImportFailToast()
                    return
                }
                doCopy(iStream, name)
                iStream.close()
            }
            contentResolver.getType(uri).equals("application/zip")
                    or contentResolver.getType(uri).equals("application/x-zip-compressed")
                    or name.endsWith("zip", ignoreCase = true) -> {
                val iStream =
                    try {
                        contentResolver.openInputStream(uri)
                    } catch (e: FileNotFoundException) {
                        null
                    }
                if (iStream == null) {
                    showImportFailToast()
                    return
                }
                // We have no way of knowing what encoding was used to create the zip,
                // so just try utf-8, then cp437, then give up
                var e = importZipWithCharset(iStream, StandardCharsets.UTF_8)
                if (e != null) {
                    // importZipWithCharset closes the input stream, so we have to open a new one
                    val iStream2 =
                        try {
                            contentResolver.openInputStream(uri)
                        } catch (e: FileNotFoundException) {
                            null
                        }
                    if (iStream2 == null) {
                        showImportFailToast()
                        return
                    }
                    e = importZipWithCharset(iStream2, Charset.forName("Cp437"))
                    if (e != null) {
                        runOnUiThread {
                            MaterialAlertDialogBuilder(this).run {
                                setTitle(
                                    resources.getString(
                                        R.string.error_zip_title,
                                        e.message
                                    )
                                )
                                setMessage(R.string.error_zip_body)
                                setPositiveButton(android.R.string.ok, null)
                                show()
                            }
                        }
                    }
                }
            }
            else -> {
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        getString(R.string.message_import_not_save, name),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val performImport = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { resultData: List<Uri>? ->
        if (resultData == null) {
            return@registerForActivityResult
        }
        // Run the import in the background to avoid blocking the UI
        Thread {
            if (resultData.size >= 10) {
                // Give the user some notification that an import is occurring
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        resources.getQuantityString(
                            R.plurals.message_importing,
                            resultData.size,
                            resultData.size
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            resultData.forEach { importFile(it) }

            // Go through the imported saves and prompt to overwrite if they already exist
            val existing = ArrayList<File>()
            val saveDir = filesDir.resolve(SAVE_DIR)
            val importDir = saveDir.resolve(IMPORTED_SAVE_SUBDIR)
            for (file in importDir.walk()) {
                if (file == importDir) {
                    continue
                }
                val dest = saveDir.resolve(file.name)
                if (dest.exists()) {
                    existing.add(file)
                } else {
                    file.renameTo(dest)
                }
            }
            if (existing.size > 0) {
                existing.sort()
                // Fragments can only be shown from the main thread
                runOnUiThread {
                    if (!supportFragmentManager.isStateSaved) {
                        ImportOverwriteDialogFragment.newInstance(existing)
                            .show(supportFragmentManager, "")
                    }
                }
            } else {
                importDir.delete()
            }
            runOnUiThread { showRoms() }
        }.start()
    }

    private val performExport = registerForActivityResult(CreateSaveExportZip()) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }
        Thread {
            val saveDir = filesDir.resolve(SAVE_DIR)
            val saves = saveDir.listFiles { path -> path.extension == "sav" }
                ?: return@Thread
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zip ->
                    saves.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            runOnUiThread {
                Toast.makeText(
                    baseContext,
                    resources.getQuantityString(
                        R.plurals.message_export_complete,
                        saves.size,
                        saves.size
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun backPress() {
        if (timeBackPressed + BACK_DELAY > System.currentTimeMillis()) {
            finish()
            return
        }

        timeBackPressed = System.currentTimeMillis()

        Toast.makeText(baseContext, getString(R.string.message_repeat_back), Toast.LENGTH_SHORT)
            .show()
    }
}
