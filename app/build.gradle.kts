@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("com.android.application") apply true
	kotlin("android") apply true
}

base {
	archivesName.set("gbcg")
}

android {
	compileSdk = 36
	buildToolsVersion = "36.0.0"
	defaultConfig {
		applicationId = "io.github.gainciarte.gbcg"
		resValue("string", "application_id", "io.github.gainciarte.gbcg")
		minSdk = 21
		targetSdk = 36
		versionCode = 41
		versionName = "beta41"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		externalNativeBuild {
			cmake {
				targets.add("gbcc")
			}
		}
		ndk {
			debugSymbolLevel = "FULL"
		}
		splits {
			abi {
				isEnable = true
				isUniversalApk = true
				// The ABIs the NDK still supports (r28 dropped armeabi and mips)
				reset()
				include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
			}
		}
	}
	buildTypes {
		release {
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			isMinifyEnabled = true
			isShrinkResources = true
		}
		debug {
			isMinifyEnabled = false
			applicationIdSuffix = ".debug"
			resValue("string", "application_id", "io.github.gainciarte.gbcg.debug")
		}
	}
	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
	externalNativeBuild {
		cmake {
			path("src/main/cpp/CMakeLists.txt")
		}
	}
	
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	// r28 or later aligns the native library to 16 KB pages, which Google Play
	// requires for apps targeting Android 15 or higher
	ndkVersion = "28.2.13676358"
	testOptions {
		unitTests {
			isIncludeAndroidResources = true
		}
	}
	namespace = "com.philj56.gbcc"
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_17)
	}
}

dependencies {
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
	implementation("androidx.camera:camera-core:1.4.2")
	implementation("androidx.camera:camera-camera2:1.4.2")
	implementation("androidx.camera:camera-lifecycle:1.4.2")
	implementation("androidx.core:core-ktx:1.17.0")
	implementation("androidx.constraintlayout:constraintlayout:2.2.1")
	implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("androidx.recyclerview:recyclerview:1.4.0")
	implementation("com.google.android.material:material:1.12.0")
	testImplementation("junit:junit:4.13.2")
	// Renders the skins on the PC, with Android's own drawing code (SkinPreviewTest)
	testImplementation("org.robolectric:robolectric:4.14.1")
	testImplementation("androidx.test:core:1.6.1")
	androidTestImplementation("androidx.test:runner:1.5.2")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.register<Copy>("copyAssets") {
	from("libs/gbcc/tileset.png")
	from("libs/gbcc/camera.png")
	into("src/main/assets")
}

tasks.register<Copy>("copyResources") {
	from("libs/gbcc/print.wav")
	into("src/main/res/raw")
}

tasks["preBuild"].dependsOn("copyAssets")
tasks["preBuild"].dependsOn("copyResources")
