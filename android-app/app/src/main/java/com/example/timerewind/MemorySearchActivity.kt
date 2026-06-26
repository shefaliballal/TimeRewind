package com.example.timerewind

import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import android.widget.Switch
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AppCompatActivity
import com.example.timerewind.R
import android.os.Bundle
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.timerewind.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.timerewind.MemoryItem
import android.util.Log
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent
import androidx.recyclerview.widget.DefaultItemAnimator
import com.airbnb.lottie.LottieAnimationView

class MemorySearchActivity : AppCompatActivity() {

    private lateinit var queryInput: EditText
    private lateinit var searchBtn: Button
    private lateinit var loadLocalBtn: Button
    private lateinit var localSearchToggle: Switch
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MemoryAdapter
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MemorySearch", "onCreate called")
        
        // Check if user is logged in
        if (!isUserLoggedIn()) {
            startLoginActivity()
            return
        }
        
        setContentView(R.layout.activity_memory_search)

        // Initialize database helper
        dbHelper = DatabaseHelper(this)

        queryInput = findViewById(R.id.queryInput)
        searchBtn = findViewById(R.id.searchBtn)
        loadLocalBtn = findViewById(R.id.loadLocalBtn)
        localSearchToggle = findViewById(R.id.localSearchToggle)
        recyclerView = findViewById(R.id.memoryResults)

        adapter = MemoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 350
            removeDuration = 200
        }
        recyclerView.adapter = adapter

        // Set up toggle listener
        localSearchToggle.setOnCheckedChangeListener { _, isChecked ->
            Log.d("MemorySearch", "Toggle changed to: $isChecked")
            if (isChecked) {
                loadLocalBtn.visibility = View.VISIBLE
                Toast.makeText(this, "Local search enabled", Toast.LENGTH_SHORT).show()
            } else {
                loadLocalBtn.visibility = View.GONE
                adapter.setItems(emptyList())
                Toast.makeText(this, "Local search disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Load local memories button
        loadLocalBtn.setOnClickListener {
            Log.d("MemorySearch", "Load local button clicked")
            loadLocalMemories()
        }

        // Search button
        searchBtn.setOnClickListener {
            val query = queryInput.text.toString().trim()
            Log.d("MemorySearch", "Search button clicked. Query: '$query'")
            if (query.isNotEmpty()) {
                if (localSearchToggle.isChecked) {
                    Log.d("MemorySearch", "Searching local memory")
                    searchLocalMemory(query)
                } else {
                    Log.d("MemorySearch", "Searching remote memory")
                    // Test connection first
                    testConnection { isConnected ->
                        if (isConnected) {
                            searchRemoteMemory(query)
                        } else {
                            Toast.makeText(this@MemorySearchActivity, "Cannot connect to server. Check your network.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Please enter a search query", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom navigation setup
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_search
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_timeline -> {
                    startActivity(Intent(this, TimelineActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_search -> false // Already here
                R.id.nav_assistant -> {
                    startActivity(Intent(this, AssistantActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadLocalMemories() {
        try {
            Log.d("MemorySearch", "Loading local memories...")
            val memories = dbHelper.getAllMemories()
            Log.d("MemorySearch", "Found ${memories.size} local memories")
            val emptyLottie = findViewById<LottieAnimationView>(R.id.emptyLottie)
            if (memories.isNotEmpty()) {
                adapter.setItems(memories)
                recyclerView.visibility = View.VISIBLE
                emptyLottie.visibility = View.GONE
                Toast.makeText(this, "Loaded ${memories.size} local memories", Toast.LENGTH_SHORT).show()
            } else {
                adapter.setItems(emptyList())
                recyclerView.visibility = View.GONE
                emptyLottie.visibility = View.VISIBLE
                Toast.makeText(this, "No local memories found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MemorySearch", "Error loading local memories: ${e.message}", e)
            Toast.makeText(this, "Error loading local memories: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun searchLocalMemory(query: String) {
        try {
            Log.d("MemorySearch", "Searching local memory for: '$query'")
            val memories = dbHelper.searchMemoriesLocal(query)
            Log.d("MemorySearch", "Found ${memories.size} local memories matching query")
            val emptyLottie = findViewById<LottieAnimationView>(R.id.emptyLottie)
            if (memories.isNotEmpty()) {
                adapter.setItems(memories)
                recyclerView.visibility = View.VISIBLE
                emptyLottie.visibility = View.GONE
                Toast.makeText(this, "Found ${memories.size} local memories", Toast.LENGTH_SHORT).show()
            } else {
                adapter.setItems(emptyList())
                recyclerView.visibility = View.GONE
                emptyLottie.visibility = View.VISIBLE
                Toast.makeText(this, "No local memories match your search", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MemorySearch", "Error searching local memories: ${e.message}", e)
            Toast.makeText(this, "Error searching local memories: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun searchRemoteMemory(query: String) {
        Log.d("MemorySearch", "searchRemoteMemory called with query: '$query'")
        
        // Show loading indicator
        Toast.makeText(this, "Searching remote memories...", Toast.LENGTH_SHORT).show()
        
        val body = HashMap<String, String>()
        body["query"] = query

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.13:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(ApiService::class.java)
        service.searchMemory(body).enqueue(object : Callback<List<MemoryItem>> {
            override fun onResponse(call: Call<List<MemoryItem>>, response: Response<List<MemoryItem>>) {
                Log.d("MemorySearch", "Remote search response: ${response.code()}")
                Log.d("MemorySearch", "Response body: ${response.body()}")
                
                val emptyLottie = findViewById<LottieAnimationView>(R.id.emptyLottie)
                if (response.isSuccessful) {
                    val memories = response.body() ?: emptyList()
                    Log.d("MemorySearch", "Received ${memories.size} remote memories")
                    
                    adapter.setItems(memories)
                    if (memories.isNotEmpty()) {
                        recyclerView.visibility = View.VISIBLE
                        emptyLottie.visibility = View.GONE
                        Toast.makeText(this@MemorySearchActivity, "Found ${memories.size} remote memories", Toast.LENGTH_SHORT).show()
                    } else {
                        recyclerView.visibility = View.GONE
                        emptyLottie.visibility = View.VISIBLE
                        Toast.makeText(this@MemorySearchActivity, "No remote memories match your search", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("MemorySearch", "Remote search failed with code: ${response.code()}")
                    Log.e("MemorySearch", "Error body: ${response.errorBody()?.string()}")
                    Toast.makeText(this@MemorySearchActivity, "Search failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<MemoryItem>>, t: Throwable) {
                Log.e("MemorySearch", "Remote search failure: ${t.message}", t)
                val errorMessage = when {
                    t.message?.contains("timeout") == true -> "Connection timeout. Please try again."
                    t.message?.contains("Unable to resolve host") == true -> "Cannot reach server. Check your network connection."
                    t.message?.contains("Connection refused") == true -> "Server connection refused. Is the server running?"
                    else -> "Network error: ${t.message}"
                }
                Toast.makeText(this@MemorySearchActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun testConnection(callback: (Boolean) -> Unit) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.13:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(ApiService::class.java)
        
        // Try a simple search to test connection
        val testBody = HashMap<String, String>()
        testBody["query"] = "test"
        
        service.searchMemory(testBody).enqueue(object : Callback<List<MemoryItem>> {
            override fun onResponse(call: Call<List<MemoryItem>>, response: Response<List<MemoryItem>>) {
                callback(true) // Connection successful
            }

            override fun onFailure(call: Call<List<MemoryItem>>, t: Throwable) {
                Log.e("MemorySearch", "Connection test failed: ${t.message}", t)
                callback(false) // Connection failed
            }
        })
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        return sharedPrefs.getBoolean("isLoggedIn", false)
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbHelper.close()
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
