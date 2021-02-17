package com.agjk.repodepot.view

import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.agjk.repodepot.R
import com.agjk.repodepot.model.data.Repos
import com.agjk.repodepot.model.data.Users
import com.agjk.repodepot.model.DepotRepository
import com.agjk.repodepot.util.DebugLogger
import com.agjk.repodepot.view.adapter.MainFragmentAdapter
import com.agjk.repodepot.view.adapter.UserAdapter
import com.agjk.repodepot.view.fragment.MainUserRepoFragment
import com.agjk.repodepot.view.fragment.SplashScreenFragment
import com.agjk.repodepot.viewmodel.RepoViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.*

class MainActivity : AppCompatActivity() {

    // for splash screen
    private var isFreshLaunch = true

    private lateinit var navigationDrawer: DrawerLayout
    private lateinit var navMenuButton: ImageButton
    private lateinit var userRecyclerView: RecyclerView
    private lateinit var viewPager: ViewPager2
    private var viewPagePosition = 0
    private lateinit var mainUserRepoFragment: Fragment

    private var tokenSaved = ""
    private var repoList: List<Repos> = listOf()
    private var repoListPrivate: List<Repos> = listOf()


    private var firebaseAuth = FirebaseAuth.getInstance()

    private val userAdapter = UserAdapter(mutableListOf(), this)
    private lateinit var mainFragmentAdapter: MainFragmentAdapter

    private val repoViewModel: RepoViewModel by viewModels()

    // for search bar
    private lateinit var searchManager: SearchManager
    private lateinit var searchView: SearchView
    private lateinit var searchResultsContainer: FragmentContainerView
    private lateinit var loadingSpinner: CircularProgressIndicator
    private var searchTimer: Timer = Timer()

    override fun onCreate(savedInstanceState: Bundle?) {
        DebugLogger("MainActivity onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.log_out_button).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sign_out_alert))
                .setMessage(getString(R.string.sign_out_message))
                .setPositiveButton(getString(R.string.sign_out_alert_positive),
                    DialogInterface.OnClickListener() { dialog: DialogInterface, _ ->
                        dialog.dismiss()

                        // sign out user
                        Firebase.auth.signOut()

                        // start this activity fresh to unload data and display splash screen
                        startActivity(Intent(this, MainActivity::class.java).also { intent ->
                            intent.flags =
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        })

                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    })
                .setNegativeButton(getString(R.string.cancel),
                    DialogInterface.OnClickListener() { dialog: DialogInterface, _ ->
                        dialog.dismiss()
                    })
                .show()
        }

        // Show splash on launch
        if (isFreshLaunch) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.fade_in, android.R.anim.fade_out,
                    android.R.anim.fade_in, android.R.anim.fade_out
                )
                .add(R.id.splash_fragment_container, SplashScreenFragment())
                .addToBackStack(null)
                .commit()

            isFreshLaunch = false
        }
    }

    private fun performUserSearch(stringSearch: String) {
        repoViewModel.searchUsers(stringSearch)
    }

    fun closeSplash() {
        runOnUiThread {
            supportFragmentManager.popBackStack()
            initFirebase()
            initMainActivity()
            getData(FirebaseAuth.getInstance().currentUser?.displayName.toString())

        }
    }

    private fun initMainActivity() {
        searchViewSetup()
        viewPagerSetup()


        //mainFragmentAdapter.addFragmentToList(userList[0])

        // TODO: Store api call for users into userList
        // TODO: Update userAdapter with userList
        // TODO: Add user to MainFragmentAdapter.addFragmentToList(userList.fragment)

        // TODO: check on Observer
        //Testing viewmodel methods

        val userName: String = FirebaseAuth.getInstance().currentUser?.displayName.toString()
        DebugLogger("Username -----> ${userName}")

    }

    private fun viewPagerSetup() {

        viewPager = findViewById(R.id.main_view_pager_2)
        mainFragmentAdapter = MainFragmentAdapter(mutableListOf(), this)

        //mainFragmentAdapter.addFragmentToList(//)

        viewPager.adapter = mainFragmentAdapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // Whenever the user swipes to page position, the text in the recyclerView in the
                // navigation drawer is be colored green on the username selected
                userAdapter.selectedUser(position)
            }
        })
    }

    fun loadViewPagerFragment(i: Int) {
        viewPager.currentItem = i
    }

    private lateinit var blankView: View

    private fun searchViewSetup() {
        // Search bar
        searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView = findViewById(R.id.search_view)
        searchResultsContainer = findViewById(R.id.search_results_fragment_container)
        blankView = findViewById(R.id.blank_view)

        loadingSpinner = findViewById(R.id.results_loading_spinner)
        loadingSpinner.hide()

        searchView.apply {
            visibility = View.VISIBLE
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            setOnQueryTextFocusChangeListener { v, hasFocus ->
                Log.d("TAG_A", "on focus change, $hasFocus")
                if (hasFocus) {
                    searchResultsContainer.visibility = View.VISIBLE
                    val animFadeScale = AnimationUtils.loadAnimation(context, R.anim.search_page_anim_in)
                    searchResultsContainer.startAnimation(animFadeScale)

                    // TODO: animate this too! :)
                    blankView.visibility = View.GONE
                }
                else {
                    searchResultsContainer.visibility = View.GONE
                    val animFadeScale = AnimationUtils.loadAnimation(context, R.anim.search_page_anim_out)
                    searchResultsContainer.startAnimation(animFadeScale)

                    blankView.visibility = View.VISIBLE
                    DepotRepository.searchForUsers("")
                }
            }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    Log.d("TAG_B", "query -> $query")
                    query?.let { performUserSearch(query) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchTimer.cancel()
                    searchTimer = Timer()
                    loadingSpinner.show()

                    searchTimer.schedule(object : TimerTask() {
                            override fun run() {
                                Log.d("TAG_B", "$newText")
                                newText?.let { performUserSearch(newText) }

                                // HACK for timing - maybe okay, maybe not.
                                Thread.sleep(300)

                                runOnUiThread {
                                    loadingSpinner.hide()
                                }
                            }
                        }, 1000)

                    return true
                }
            })
        }

        navigationDrawer = findViewById(R.id.drawer_layout)
        userRecyclerView = findViewById(R.id.rv_users)

        userRecyclerView.adapter = userAdapter

        navMenuButton = findViewById(R.id.nav_drawer_menu_button)
        navMenuButton.setOnClickListener {
            if (navigationDrawer.isDrawerOpen(GravityCompat.START)) {
                navigationDrawer.closeDrawer(GravityCompat.START)
            } else {
                navigationDrawer.openDrawer(GravityCompat.START)
            }

            closeSearch()
        }

//        // Toggle is used to attach the toolbar and navigation drawer
//        val toggle = ActionBarDrawerToggle(
//            this,
//            navigationDrawer,
//            R.string.navigation_drawer_open,
//            R.string.navigation_drawer_close
//        )
//
//        navigationDrawer.addDrawerListener(toggle)
//        toggle.syncState() // Menu button default animation when drawer is open and closed
    }

    override fun onBackPressed() {
        when {
            searchResultsContainer.visibility == View.VISIBLE -> {
                closeSearch()
            }

            navigationDrawer.isDrawerOpen(GravityCompat.START) ->
                navigationDrawer.closeDrawer(GravityCompat.START)

            else -> super.onBackPressed()
        }
    }

    fun closeSearch() {
        searchView.setQuery("", false)
        searchView.clearFocus()
        searchView.isIconified = true
    }

    fun closeNavDrawer() {
        navigationDrawer.closeDrawers()
    }

    //Data methods for MainActivity

    private fun initFirebase() {
        repoViewModel.getUserPreferences().observe(this, {
            tokenSaved = it.gitHubAccessToken
        })
    }

    private fun getData(userName: String) {
        //repoViewModel.getProfile(userName)
        //repoViewModel.addUserToList("bladerjam7")
        repoViewModel.getUserList(userName).observe(this, { userget ->
            DebugLogger("userGET SIZE -> ${userget.size}")
            if (userget.isNotEmpty()) {
                userget.forEach { user ->
                    if (user.login != firebaseAuth.currentUser?.displayName) {
                        repoViewModel.getStoredReposForUser(user.login.toString())
                            .observe(this, { gitrepos ->
                                val listToSet = mutableListOf<Repos>()
                                for (repo in gitrepos) {
                                    listToSet.add(
                                        Repos(
                                            repo.name.toString(),
                                            repo.description.toString(),
                                            repo.language.toString()
                                        )
                                    )
                                }
                                val usersToReturn: MutableList<Users> = mutableListOf()
                                DebugLogger("listToSet SIZE ______> : ${listToSet.size}")
                                usersToReturn.add(
                                    Users(
                                        user.avatar_url.toString(),
                                        user.login.toString(),
                                        MainUserRepoFragment(listToSet)
                                    )
                                )
                                DebugLogger("usersToReturn: ------> $usersToReturn")
                                DebugLogger("listToSet: ------> $listToSet")
                                userAdapter.updateUsers(usersToReturn)
                                mainFragmentAdapter.addFragmentToList(usersToReturn)
                            })



                    } else {
                        repoViewModel.getStoredPrivateReposForUser(userName, tokenSaved)
                            .observe(this, { gitrepos ->
                                val listToSet = mutableListOf<Repos>()
                                for (repo in gitrepos) {
                                    listToSet.add(
                                        Repos(
                                            repo.name.toString(),
                                            repo.description.toString(),
                                            repo.language.toString()
                                        )
                                    )
                                }
                                val usersToReturnTwo: MutableList<Users> = mutableListOf()
                                DebugLogger("listToSet SIZE ______> : ${listToSet.size}")
                                DebugLogger("userstoRETURN -----> ${usersToReturnTwo}")
                                usersToReturnTwo.add(Users(user.avatar_url.toString(),
                                        user.login.toString(),
                                        MainUserRepoFragment(listToSet)))

                                userAdapter.updateUsers(usersToReturnTwo)
                                mainFragmentAdapter.addFragmentToList(usersToReturnTwo)
                            }
                            )
                    }
                }
            } else
                repoViewModel.addUserToList(userName)
        })
    }
}