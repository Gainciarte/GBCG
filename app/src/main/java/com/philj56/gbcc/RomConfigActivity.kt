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

import android.os.Bundle
import com.philj56.gbcc.databinding.ActivityRomConfigBinding
import java.io.File

class RomConfigActivity : BaseActivity() {
    lateinit var configFile: File
    // The ROM's file name, to open its cheats
    lateinit var romFileName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        // Make sure we initialise configFile before calling anything else,
        // as it's accessed from within RomConfigFragment.
        val configDir = filesDir.resolve("config")
        configDir.mkdirs()
        val bundle = intent.extras
        romFileName = File(bundle?.getString("file") ?: "").name
        val filename = File(romFileName).nameWithoutExtension
        configFile = configDir.resolve("$filename.cfg")

        super.onCreate(savedInstanceState)

        val binding = ActivityRomConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
    }

    fun clearConfig() {
        configFile.delete()
        finish()
    }
}

