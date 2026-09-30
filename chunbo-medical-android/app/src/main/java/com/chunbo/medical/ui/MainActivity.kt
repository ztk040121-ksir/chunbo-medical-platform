package com.chunbo.medical.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.chunbo.medical.R
import com.chunbo.medical.databinding.ActivityMainBinding
import com.chunbo.medical.ui.copilot.CopilotFragment
import com.chunbo.medical.ui.mall.MallFragment
import com.chunbo.medical.ui.patient.PatientFragment
import com.chunbo.medical.ui.profile.ProfileFragment
import com.chunbo.medical.util.QueueReminderManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val mallFragment by lazy { MallFragment() }
    private val copilotFragment by lazy { CopilotFragment() }
    private val patientFragment by lazy { PatientFragment() }
    private val profileFragment by lazy { ProfileFragment() }

    private var currentFragment: Fragment? = null

    companion object {
        private const val KEY_SELECTED_TAB = "key_selected_tab"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initToolbar()
        initBottomNavigation()
        applyNavigationBarInsets()
        requestNotificationPermission()
        requestRecordAudioPermission()
        QueueReminderManager.ensureChannel(this)

        // 恢复上次停留的标签页（进程重建/重进 App 不再强制跳回挂号页）
        val savedTab = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: R.id.nav_patient
        binding.bottomNavigation.selectedItemId = savedTab
        switchFragment(fragmentFor(savedTab), titleFor(savedTab))
    }

    /** 请求通知权限（Android 13+，用于就诊叫号提醒） */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1002
            )
        }
    }

    /** 请求麦克风权限（用于语音输入） */
    private fun requestRecordAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1003
            )
        }
    }

    /** 底部导航避开全面屏手势条 */
    private fun applyNavigationBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, binding.bottomNavigation.selectedItemId)
    }

    private fun fragmentFor(id: Int): Fragment = when (id) {
        R.id.nav_mall -> mallFragment
        R.id.nav_copilot -> copilotFragment
        R.id.nav_profile -> profileFragment
        else -> patientFragment
    }

    private fun titleFor(id: Int): String = when (id) {
        R.id.nav_mall -> getString(R.string.mall_title)
        R.id.nav_copilot -> getString(R.string.copilot_title)
        R.id.nav_profile -> getString(R.string.profile_title)
        else -> getString(R.string.patient_title)
    }

    private fun initToolbar() {
        binding.appBarLayout.elevation = 0f
        binding.appBarLayout.setLiftOnScroll(false)
        binding.appBarLayout.setBackgroundColor(getColor(R.color.primary))
        binding.topToolbar.setBackgroundColor(getColor(R.color.primary))

        binding.topToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_refresh -> {
                    triggerRefresh()
                    true
                }
                R.id.menu_server_settings -> {
                    com.chunbo.medical.ui.settings.AppSettingsDialog.show(
                        this,
                        onProfileEditRequested = {
                            binding.bottomNavigation.selectedItemId = R.id.nav_profile
                        },
                        onStateChanged = {
                            triggerRefresh()
                        }
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun initBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            switchFragment(fragmentFor(item.itemId), titleFor(item.itemId))
            true
        }
    }

    fun switchTab(menuItemId: Int) {
        binding.bottomNavigation.selectedItemId = menuItemId
    }

    /** 联动切换到商城 Tab 并聚焦特定药品 */
    fun switchToMallTab(searchKeyword: String? = null) {
        binding.bottomNavigation.selectedItemId = R.id.nav_mall
        if (!searchKeyword.isNullOrBlank()) {
            mallFragment.applySearchKeyword(searchKeyword)
        }
    }

    /** 联动切换到便民挂号/名医预约 */
    fun switchToPatient() {
        binding.bottomNavigation.selectedItemId = R.id.nav_patient
    }

    /** 联动切换到春播小药师智能体 */
    fun switchToCopilot() {
        binding.bottomNavigation.selectedItemId = R.id.nav_copilot
    }

    /** 切换到春播小药师并直接发起提问（就诊记录 AI 分析入口） */
    fun switchToCopilotAndAsk(question: String) {
        binding.bottomNavigation.selectedItemId = R.id.nav_copilot
        copilotFragment.askFromExternal(question)
    }

    /** 切换到春播小药师并发起带商品卡片的提问（订单咨询入口：药品卡片带真实图片） */
    fun switchToCopilotAndAskOrder(
        question: String,
        products: List<com.chunbo.medical.data.model.MallProductRecommendation>
    ) {
        binding.bottomNavigation.selectedItemId = R.id.nav_copilot
        copilotFragment.askOrderFromExternal(question, products)
    }

    private fun switchFragment(target: Fragment, title: String) {
        if (currentFragment == target) {
            binding.topToolbar.title = title
            return
        }

        val transaction = supportFragmentManager.beginTransaction()
        // 隐藏所有已添加的 fragment，只显示目标，避免 hide/show 状态错乱
        listOf(mallFragment, copilotFragment, patientFragment, profileFragment).forEach { f ->
            if (f.isAdded && f != target) transaction.hide(f)
        }

        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target)
        } else {
            transaction.show(target)
        }

        transaction.commit()
        currentFragment = target
        binding.topToolbar.title = title
    }

    private fun triggerRefresh() {
        when (currentFragment) {
            is MallFragment -> (currentFragment as MallFragment).refresh()
            is PatientFragment -> (currentFragment as PatientFragment).refresh()
            is ProfileFragment -> (currentFragment as ProfileFragment).refresh()
        }
    }
}
