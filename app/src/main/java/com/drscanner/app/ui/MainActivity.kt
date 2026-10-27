package com.drscanner.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.drscanner.app.R

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val historialFragment = HistorialFragment()
    private val vehiculosFragment = VehiculosFragment()
    private val configFragment = ConfigFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
            R.id.bottomNav
        )

        if (savedInstanceState == null) {
            mostrarFragment(homeFragment)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> mostrarFragment(homeFragment)
                R.id.nav_historial -> mostrarFragment(historialFragment)
                R.id.nav_vehiculos -> mostrarFragment(vehiculosFragment)
                R.id.nav_config -> mostrarFragment(configFragment)
            }
            true
        }
    }

    private fun mostrarFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
