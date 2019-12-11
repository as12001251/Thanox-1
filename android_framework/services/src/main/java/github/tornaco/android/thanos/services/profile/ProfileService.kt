package github.tornaco.android.thanos.services.profile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.UserHandle
import android.util.Log
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.io.Files
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.tornaco.android.thanos.BuildProp
import github.tornaco.android.thanos.core.Res
import github.tornaco.android.thanos.core.T
import github.tornaco.android.thanos.core.app.AppResources
import github.tornaco.android.thanos.core.app.event.IEventSubscriber
import github.tornaco.android.thanos.core.app.event.ThanosEvent
import github.tornaco.android.thanos.core.compat.NotificationCompat
import github.tornaco.android.thanos.core.compat.NotificationManagerCompat
import github.tornaco.android.thanos.core.persist.RepoFactory
import github.tornaco.android.thanos.core.persist.StringMapRepo
import github.tornaco.android.thanos.core.persist.StringSetRepo
import github.tornaco.android.thanos.core.pm.AppInfo
import github.tornaco.android.thanos.core.pref.IPrefChangeListener
import github.tornaco.android.thanos.core.profile.*
import github.tornaco.android.thanos.core.secure.ops.AppOpsManager
import github.tornaco.android.thanos.core.util.*
import github.tornaco.android.thanos.core.util.collection.ArrayMap
import github.tornaco.android.thanos.services.*
import github.tornaco.android.thanos.services.apihint.ExecuteBySystemHandler
import github.tornaco.android.thanos.services.app.EventBus
import github.tornaco.android.thanos.services.n.NotificationHelper
import github.tornaco.android.thanos.services.n.NotificationIdFactory
import github.tornaco.android.thanos.services.n.SystemUI
import github.tornaco.android.thanos.services.pm.PackageMonitor
import github.tornaco.android.thanos.services.profile.handle.Handle
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rules
import org.jeasy.rules.api.RulesEngine
import org.jeasy.rules.core.DefaultRulesEngine
import org.jeasy.rules.mvel.MVELRuleFactory
import org.jeasy.rules.support.JsonRuleDefinitionReader
import org.jeasy.rules.support.YamlRuleDefinitionReader
import util.CollectionUtils
import util.ObjectsUtils
import java.io.File
import java.io.StringReader
import java.util.*
import java.util.concurrent.TimeUnit

class ProfileService(s: S) : ThanoxSystemService(s), IProfileManager {

    private val notificationHelper: NotificationHelper = NotificationHelper()

    private var autoApplyForNewInstalledAppsEnabled = false
    private var profileEnabled = false

    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    private val rulesMapping: ArrayMap<String, RuleInfoExt> = ArrayMap()
    private val rules: Rules = Rules(Sets.newHashSet())
    private val rulesEngine: RulesEngine = DefaultRulesEngine()

    private val ruleFactoryJson = MVELRuleFactory(JsonRuleDefinitionReader())
    private val ruleFactoryYaml = MVELRuleFactory(YamlRuleDefinitionReader())

    private lateinit var enabledRuleNameRepo: StringSetRepo
    private lateinit var globalRuleVarRepo: StringMapRepo

    private val gson = Gson()

    private val monitor = object : PackageMonitor() {
        override fun onPackageAdded(packageName: String, uid: Int) {
            super.onPackageAdded(packageName, uid)
            Timber.v("onPackageAdded: %s", packageName)

            val disposable = Observable.just(packageName).delay(1, TimeUnit.SECONDS)
                .observeOn(ThanosSchedulers.serverThread())
                .observeOn(ThanosSchedulers.serverThread())
                .subscribe {
                    setupAutoConfigForNewInstalledAppsIfNeed(packageName)

                    if (FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
                        val pkgFacts = Facts()
                        pkgFacts.put("pkgAdded", true)
                        pkgFacts.put("pkgName", packageName)
                        publishFacts(pkgFacts)
                    }
                }
            compositeDisposable.add(disposable)
        }
    }

    private val frontEventSubscriber = object : IEventSubscriber.Stub() {
        override fun onEvent(e: ThanosEvent) {
            val intent = e.intent
            val from = intent.getStringExtra(T.Actions.ACTION_FRONT_PKG_CHANGED_EXTRA_PACKAGE_FROM)
            val to = intent.getStringExtra(T.Actions.ACTION_FRONT_PKG_CHANGED_EXTRA_PACKAGE_TO)

            if (FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
                val pkgFacts = Facts()
                pkgFacts.put("from", from)
                pkgFacts.put("to", to)
                pkgFacts.put("frontPkgChanged", true)
                publishFacts(pkgFacts)
            }
        }
    }

    private val taskEventSubscriber = object : IEventSubscriber.Stub() {
        override fun onEvent(e: ThanosEvent) {
            val intent = e.intent
            val userId = intent.getIntExtra(
                T.Actions.ACTION_TASK_REMOVED_EXTRA_USER_ID,
                UserHandle.getCallingUserId()
            )
            val pkgName = intent.getStringExtra(T.Actions.ACTION_TASK_REMOVED_EXTRA_PACKAGE_NAME)

            if (FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
                val pkgFacts = Facts()
                pkgFacts.put("userId", userId)
                pkgFacts.put("pkgName", pkgName)
                pkgFacts.put("taskRemoved", true)
                publishFacts(pkgFacts)
            }
        }
    }

    private val activityResumedEventSubscriber = object : IEventSubscriber.Stub() {
        override fun onEvent(e: ThanosEvent) {
            val intent = e.intent
            val name = intent.getParcelableExtra<ComponentName>(
                T.Actions.ACTION_ACTIVITY_RESUMED_EXTRA_COMPONENT_NAME
            )
            val pkgName =
                intent.getStringExtra(T.Actions.ACTION_ACTIVITY_RESUMED_EXTRA_PACKAGE_NAME)

            if (FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
                val pkgFacts = Facts()
                pkgFacts.put("componentName", name)
                pkgFacts.put("componentNameAsString", name.flattenToString())
                pkgFacts.put("componentNameAsShortString", name.flattenToShortString())
                pkgFacts.put("pkgName", pkgName)
                pkgFacts.put("activityResumed", true)
                publishFacts(pkgFacts)
            }
        }
    }

    private val packageStoppedEventSubscriber = object : IEventSubscriber.Stub() {
        override fun onEvent(e: ThanosEvent) {
            if (FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
                val intent = e.intent
                val pkg = intent.getStringExtra(T.Actions.ACTION_PACKAGE_STOPPED_EXTRA_PACKAGE_NAME)
                val pkgFacts = Facts()
                pkgFacts.put("pkgKilled", true)
                pkgFacts.put("pkgName", pkg)
                publishFacts(pkgFacts)
            }
        }
    }

    override fun onStart(context: Context) {
        super.onStart(context)
        enabledRuleNameRepo =
            RepoFactory.get().getOrCreateStringSetRepo(T.profileEnabledRulesRepoFile().path)
        globalRuleVarRepo =
            RepoFactory.get().getOrCreateStringMapRepo(T.globalRuleVarsRepoFile().path)
    }

    override fun systemReady() {
        super.systemReady()
        registerReceivers()
        initPrefs()
        registerRules()
    }

    override fun shutdown() {
        super.shutdown()
        monitor.unregister()
        compositeDisposable.clear()
    }

    private fun initPrefs() {
        readPrefs()
        listenToPrefs()
    }

    private fun registerReceivers() {
        monitor.register(context, UserHandle.CURRENT, true, BackgroundThread.getHandler())

        EventBus.getInstance().registerEventSubscriber(
            IntentFilter(T.Actions.ACTION_FRONT_PKG_CHANGED),
            frontEventSubscriber
        )
        EventBus.getInstance().registerEventSubscriber(
            IntentFilter(T.Actions.ACTION_TASK_REMOVED),
            taskEventSubscriber
        )
        EventBus.getInstance().registerEventSubscriber(
            IntentFilter(T.Actions.ACTION_ACTIVITY_RESUMED),
            activityResumedEventSubscriber
        )
        EventBus.getInstance().registerEventSubscriber(
            IntentFilter(T.Actions.ACTION_PACKAGE_STOPPED),
            packageStoppedEventSubscriber
        )
    }

    private fun registerRules() {
        if (FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
            rules.clear()
            rulesMapping.clear()
            rulesMapping.putAll(LocalRuleScanner().getRulesUnder(T.profileRulesDir()))
            rulesMapping.forEach {
                if (enabledRuleNameRepo.has(it.value.rule.name)) {
                    Timber.v("Register rule: ${it.key}")
                    enableRule(it.value.rule.name)
                } else {
                    Timber.v("Not enabled rule: ${it.key}")
                }
            }
        }
    }

    private fun readPrefs() {
        val preferenceManagerService = s.preferenceManagerService
        this.autoApplyForNewInstalledAppsEnabled = preferenceManagerService.getBoolean(
            T.Settings.PREF_AUTO_CONFIG_NEW_INSTALLED_APPS_ENABLED.key,
            T.Settings.PREF_AUTO_CONFIG_NEW_INSTALLED_APPS_ENABLED.defaultValue
        )
        this.profileEnabled = preferenceManagerService.getBoolean(
            T.Settings.PREF_PROFILE_ENABLED.key,
            T.Settings.PREF_PROFILE_ENABLED.defaultValue
        )

        ensureAutomationState()
    }

    private fun ensureAutomationState() {
        executeInternal(Runnable { ensureAutomationStateInternal() })
    }

    @ExecuteBySystemHandler
    private fun ensureAutomationStateInternal() {
        if (!FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE_A11Y)) {
            return
        }
        Timber.v("ensureAutomationStateInternal")
        if (profileEnabled) {
            s.windowManagerService.ensureAutomationConnected()
        } else {
            s.windowManagerService.ensureAutomationDisConnected()
        }
    }

    private fun listenToPrefs() {
        val listener = object : IPrefChangeListener.Stub() {
            override fun onPrefChanged(key: String) {
                if (ObjectsUtils.equals(
                        T.Settings.PREF_AUTO_CONFIG_NEW_INSTALLED_APPS_ENABLED.key,
                        key
                    )
                    || ObjectsUtils.equals(
                        T.Settings.PREF_PROFILE_ENABLED.key,
                        key
                    )
                ) {
                    Timber.i("Pref changed: $key, reload.")
                    readPrefs()
                }
            }
        }
        s.preferenceManagerService.registerSettingsChangeListener(listener)
    }

    override fun setAutoApplyForNewInstalledAppsEnabled(enable: Boolean) {
        autoApplyForNewInstalledAppsEnabled = enable

        val preferenceManagerService = s.preferenceManagerService
        preferenceManagerService.putBoolean(
            T.Settings.PREF_AUTO_CONFIG_NEW_INSTALLED_APPS_ENABLED.key,
            enable
        )
    }

    override fun isAutoApplyForNewInstalledAppsEnabled(): Boolean {
        return autoApplyForNewInstalledAppsEnabled
    }

    private fun setupAutoConfigForNewInstalledAppsIfNeed(pkg: String) {
        Timber.v("setupAutoConfigForNewInstalledAppsIfNeed: %s", pkg)
        if (!autoApplyForNewInstalledAppsEnabled) return

        val appInfo: AppInfo? = s.pkgManagerService.getAppInfo(pkg)

        if (appInfo == null) {
            Timber.e("setupAutoConfigForNewInstalledAppsIfNeed app not installed!")
            return
        }

        s.activityManagerService.setPkgBgRestrictEnabled(
            pkg,
            s.activityManagerService.isPkgBgRestricted(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.activityManagerService.setPkgStartBlockEnabled(
            pkg,
            s.activityManagerService.isPkgStartBlocking(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.activityManagerService.setPkgCleanUpOnTaskRemovalEnabled(
            pkg,
            s.activityManagerService.isPkgCleanUpOnTaskRemovalEnabled(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.activityManagerService.setPkgRecentTaskBlurEnabled(
            pkg,
            s.activityManagerService.isPkgRecentTaskBlurEnabled(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.privacyService.setPkgPrivacyDataCheat(
            pkg,
            s.privacyService.isPkgPrivacyDataCheat(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.appOpsService.setPkgOpRemindEnable(
            pkg,
            s.appOpsService.isPkgOpRemindEnable(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.notificationManagerService.setScreenOnNotificationEnabledForPkg(
            pkg,
            s.notificationManagerService.isScreenOnNotificationEnabledForPkg(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )
        s.activityManagerService.setPkgSmartStandByEnabled(
            pkg,
            s.activityManagerService.isPkgSmartStandByEnabled(ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME)
        )

        // Set ops.
        val numOp = AppOpsManager._NUM_OP
        for (i in 0 until numOp) {
            val templateMode = s.appOpsService.checkOperation(
                i,
                -1,
                ProfileManager.PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME
            )
            if (templateMode != AppOpsManager.MODE_IGNORED) {
                Timber.d("Op $i is not MODE_IGNORED, no need to set...")
                continue
            }
            Timber.v("Set op by template: $i, mode: $templateMode")
            try {
                s.appOpsService.setMode(i, appInfo.uid, appInfo.pkgName, templateMode)
            } catch (e: Throwable) {
                Timber.e(e, "Fail set mode $templateMode for app $appInfo")
            }
        }

        Timber.d("setupAutoConfigForNewInstalledApps done: %s", pkg)

        showAutoConfigAppNotification(pkg)
    }

    private fun showAutoConfigAppNotification(pkg: String) {
        if (!isNotificationPostReady) return

        Timber.v("Will show showAutoConfigAppNotification for pkg: %s", pkg)

        // For oreo.
        notificationHelper.createSilenceNotificationChannel(context!!)

        val builder = NotificationCompat.Builder(
            context,
            T.serviceSilenceNotificationChannel()
        )

        val appResource = AppResources(context, BuildProp.THANOS_APP_PKG_NAME)

        SystemUI.overrideNotificationAppName(
            context!!, builder,
            appResource.getString(Res.Strings.STRING_SERVICE_NOTIFICATION_OVERRIDE_THANOS)
        )

        val appLabel = PkgUtils.loadNameByPkgName(context, pkg)

        val viewer = Intent()
        viewer.setPackage(BuildProp.THANOS_APP_PKG_NAME)
        viewer.setClassName(
            BuildProp.THANOS_APP_PKG_NAME,
            BuildProp.ACTIVITY_APP_DETAILS
        )

        val appInfo = s.pkgManagerService.getAppInfo(pkg)
        viewer.putExtra("app", appInfo)
        viewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // TODO Extract string res.
        val n = builder
            .setContentTitle("自动配置")
            .setContentText("${appLabel}已经自动启用模板配置")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NotificationIdFactory.getIdByTag(UUID.randomUUID().toString()),
                    viewer,
                    0
                )
            )
            .build()

        if (OsUtils.isMOrAbove()) {
            n.smallIcon = appResource.getIcon(Res.Drawables.DRAWABLE_CHECK_DOUBLE_FILL)
        }

        NotificationManagerCompat.from(context)
            .notify(NotificationIdFactory.getIdByTag(UUID.randomUUID().toString()), n)
    }

    override fun addRule(ruleJson: String?, callback: IRuleAddCallback?, format: Int) {
        ensureProfileFeature()
        enforceCallingPermissions()
        Timber.v("addRule: $ruleJson, format is: $format")
        Objects.requireNonNull(ruleJson, "RuleInfo content is null")
        when (format) {
            ProfileManager.RULE_FORMAT_JSON -> addRule(
                ruleJson,
                callback,
                ruleFactoryJson,
                ".json",
                ProfileManager.RULE_FORMAT_JSON
            )
            ProfileManager.RULE_FORMAT_YAML -> addRule(
                ruleJson,
                callback,
                ruleFactoryYaml,
                ".yml",
                ProfileManager.RULE_FORMAT_YAML
            )
            else -> throw IllegalArgumentException("Bad format: $format")
        }
    }


    @Suppress("UnstableApiUsage")
    @ExecuteBySystemHandler
    private fun addRule(
        ruleJson: String?,
        callback: IRuleAddCallback?,
        factory: MVELRuleFactory,
        suffix: String,
        format: Int
    ) {
        executeInternal(Runnable {
            try {
                val rule = factory.createRule(StringReader(ruleJson!!))
                if (rule != null) {
                    // Using name as id.
                    val ruleName = rule.name
                    val infoExt = RuleInfoExt(
                        RuleInfo(
                            rule.name,
                            rule.description,
                            ruleJson,
                            "tornaco",
                            System.currentTimeMillis(),
                            false,
                            format
                        ),
                        rule
                    )

                    val exists = isRuleExists(ruleName)
                    if (exists) {
                        Timber.w("Disable rule $ruleName since it has changed.")
                        disableRule(ruleName)
                    }

                    rulesMapping[ruleName] = infoExt
                    // Persist.
                    val f = File(T.profileRulesDir(), "$ruleName$suffix")
                    Files.createParentDirs(f)
                    Files.asByteSink(f)
                        .asCharSink(Charsets.UTF_8)
                        .write(ruleJson)
                    callback?.onRuleAddSuccess()
                } else {
                    callback?.onRuleAddFail(0, "Create json rule fail with unknown error.")
                }
            } catch (e: Exception) {
                Timber.e(e, "addJsonRule: $rulesEngine")
                callback?.onRuleAddFail(0, Log.getStackTraceString(e))
            }
        })
    }

    override fun deleteRule(ruleName: String?) {
        ensureProfileFeature()
        enforceCallingPermissions()
        disableRule(ruleName)
        val r = rulesMapping.remove(ruleName!!)
        // Delete file.
        if (r != null) {
            val f = when {
                r.ruleInfo.format == ProfileManager.RULE_FORMAT_JSON -> File(
                    T.profileRulesDir(),
                    "${r.rule.name}.json"
                )
                r.ruleInfo.format == ProfileManager.RULE_FORMAT_YAML -> File(
                    T.profileRulesDir(),
                    "${r.rule.name}.yml"
                )
                else -> null
            }
            if (f != null) {
                Timber.d("Delete rule file: $f, exists? ${f.exists()}")
                DevNull.accept(f.delete())
            } else {
                Timber.e("Rule not exists: ${r.rule}")
            }
        }
        Timber.v("deleteRule: $r")
    }

    override fun enableRule(ruleName: String?): Boolean {
        ensureProfileFeature()
        enforceCallingPermissions()
        val rule = rulesMapping[ruleName]
        return if (rule != null) {
            rules.register(rule.rule)
            enabledRuleNameRepo.add(ruleName)
            Timber.d("Enabled rule: $ruleName")
            true
        } else {
            Timber.e("RuleInfo with name $ruleName not found..")
            false
        }
    }

    override fun disableRule(ruleName: String?): Boolean {
        ensureProfileFeature()
        enforceCallingPermissions()
        Timber.d("Disable rule: $ruleName")
        rules.unregister(ruleName)
        return enabledRuleNameRepo.remove(ruleName)
    }

    override fun checkRule(ruleString: String?, callback: IRuleCheckCallback?, format: Int) {
        ensureProfileFeature()
        enforceCallingPermissions()
        when (format) {
            ProfileManager.RULE_FORMAT_JSON -> checkJsonRule(ruleString, callback)
            ProfileManager.RULE_FORMAT_YAML -> checkYamlRule(ruleString, callback)
            else -> callback?.onInvalid(0, "Invalid format.")
        }
    }

    private fun checkJsonRule(ruleJson: String?, callback: IRuleCheckCallback?) {
        Timber.v("checkJsonRule: $ruleJson")
        try {
            val rule = ruleFactoryJson.createRule(StringReader(ruleJson!!))
            if (rule != null) {
                callback?.onValid()
            } else {
                callback?.onInvalid(0, "Check rule fail with unknown error.")
            }
        } catch (e: Exception) {
            Timber.e(e, "checkJsonRule: $rulesEngine")
            callback?.onInvalid(0, Log.getStackTraceString(e))
        }
    }

    private fun checkYamlRule(ruleYaml: String?, callback: IRuleCheckCallback?) {
        Timber.v("checkYamlRule: $ruleYaml")
        try {
            val rule = ruleFactoryYaml.createRule(StringReader(ruleYaml!!))
            if (rule != null) {
                callback?.onValid()
            } else {
                callback?.onInvalid(0, "Check rule fail with unknown error.")
            }
        } catch (e: Exception) {
            Timber.e(e, "checkYamlRule: $rulesEngine")
            callback?.onInvalid(0, Log.getStackTraceString(e))
        }
    }

    override fun isRuleEnabled(ruleName: String?): Boolean {
        ensureProfileFeature()
        return enabledRuleNameRepo.has(ruleName!!)
    }

    override fun isRuleExists(ruleName: String?): Boolean {
        ensureProfileFeature()
        return ruleName != null && rulesMapping.containsKey(ruleName)
    }

    override fun getAllRules(): Array<RuleInfo?> {
        ensureProfileFeature()
        val res = arrayListOf<RuleInfo>()
        rulesMapping.values.forEach {
            it.ruleInfo.enabled = isRuleEnabled(it.ruleInfo.name)
            res.add(it.ruleInfo)
        }
        return res.toArray(emptyArray())
    }

    override fun getEnabledRules(): Array<RuleInfo> {
        ensureProfileFeature()
        val res = arrayListOf<RuleInfo>()
        rulesMapping.values.forEach {
            if (isRuleEnabled(it.rule.name)) {
                it.ruleInfo.enabled = true
                res.add(it.ruleInfo)
            }
        }
        return res.toArray(emptyArray())
    }

    override fun isProfileEnabled(): Boolean {
        return profileEnabled
    }

    override fun isGlobalRuleVarByNameExists(varName: String?): Boolean {
        return globalRuleVarRepo.containsKey(varName)
    }

    override fun removeGlobalRuleVar(varName: String?): Boolean {
        return globalRuleVarRepo.remove(varName) != null
    }

    override fun addGlobalRuleVar(varName: String, varArray: Array<out String>): Boolean {
        Timber.v("addGlobalRuleVar: %s %s", varName, varArray)
        enforceCallingPermissions()
        // Force write.
        val varList = mutableListOf<String>()
        varList.addAll(varArray)
        val json = gson.toJson(varList)
        globalRuleVarRepo[varName] = json
        return true
    }

    override fun appendGlobalRuleVar(varName: String, varArray: Array<out String>): Boolean {
        Timber.v("addGlobalRuleVar: %s %s", varName, varArray)
        enforceCallingPermissions()
        val varList = mutableListOf<String>()
        varList.addAll(varArray)
        varList.addAll(getGlobalRuleVarByName(varName))
        val json = gson.toJson(varList)
        globalRuleVarRepo[varName] = json
        return true
    }

    override fun getGlobalRuleVarByName(varName: String?): Array<String> {
        if (globalRuleVarRepo.containsKey(varName)) {
            try {
                val current = globalRuleVarRepo[varName]
                val currentList: List<String> =
                    gson.fromJson<List<String>>(current, object : TypeToken<List<String>>() {}.type)
                if (!CollectionUtils.isNullOrEmpty(currentList)) {
                    return currentList.toTypedArray()
                }
            } catch (e: Throwable) {
                Timber.e(e, "getAllGlobalRuleVarByName")
            }
        }
        return emptyArray()
    }

    override fun getAllGlobalRuleVarNames(): Array<String> {
        return globalRuleVarRepo.keys.toTypedArray()
    }

    override fun getAllGlobalRuleVar(): Array<GlobalVar> {
        val res = arrayListOf<GlobalVar>()
        allGlobalRuleVarNames.forEach {
            res.add(GlobalVar(it, getGlobalRuleVarByName(it).toMutableList()))
        }
        return res.toTypedArray()
    }

    override fun setProfileEnabled(enable: Boolean) {
        ensureProfileFeature()
        enforceCallingPermissions()
        profileEnabled = enable

        val preferenceManagerService = s.preferenceManagerService
        preferenceManagerService.putBoolean(
            T.Settings.PREF_PROFILE_ENABLED.key,
            enable
        )
    }


    fun publishFacts(facts: Facts) {
        executeInternal(Runnable {
            publishFactsInternal(facts)
        })
    }

    @ExecuteBySystemHandler
    fun publishFactsInternal(facts: Facts) {
        if (!FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
            return
        }
        if (!isProfileEnabled) {
            Timber.v("Profile not enabled, won't fire any fact.")
            return
        }

        injectGlobalVars(facts)
        injectHandles(facts)

        rulesEngine.fire(rules, facts)
    }

    private fun injectHandles(facts: Facts): Facts {
        return Handle.inject(context, s, facts)
    }

    private fun injectGlobalVars(facts: Facts): Facts {
        allGlobalRuleVarNames.forEach {
            val list = Lists.newArrayList<String>(getGlobalRuleVarByName(it).toList())
            facts.put("globalVarOf$$it", list)
        }
        return facts
    }

    override fun asBinder(): IBinder {
        return Noop.notSupported()
    }

    private fun ensureProfileFeature() {
        if (!FeatureManager.hasFeature(BuildProp.THANOX_FEATURE_PROFILE)) {
            throw UnsupportedOperationException("No feature: ${BuildProp.THANOX_FEATURE_PROFILE}")
        }
    }
}
