package com.roy.languagekeyboard

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 只要这一行，确保它指向你的 activity_main 布局即可
        setContentView(R.layout.activity_main)

        // 把下面所有报错的代码（比如 ViewCompat.setOnApplyWindowInsetsListener...）全部删掉

    }
}