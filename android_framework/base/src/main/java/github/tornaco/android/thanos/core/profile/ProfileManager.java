package github.tornaco.android.thanos.core.profile;

import lombok.SneakyThrows;

public class ProfileManager {
    public static final int RULE_FORMAT_JSON = 0;
    public static final int RULE_FORMAT_YAML = 1;

    /**
     * Dummy package name for new installed apps config.
     */
    public static final String PROFILE_AUTO_APPLY_NEW_INSTALLED_APPS_CONFIG_PKG_NAME
            = "github.tornaco.android.thanos.core.profile.AAC9A203-A42F-4AD6-976E-815D929D444A";

    private final IProfileManager server;

    public ProfileManager(IProfileManager server) {
        this.server = server;
    }

    @SneakyThrows
    public void setAutoApplyForNewInstalledAppsEnabled(boolean enable) {
        server.setAutoApplyForNewInstalledAppsEnabled(enable);
    }

    @SneakyThrows
    public boolean isAutoApplyForNewInstalledAppsEnabled() {
        return server.isAutoApplyForNewInstalledAppsEnabled();
    }

    @SneakyThrows
    public void addRule(String ruleString, RuleAddCallback callback, int format) {
        server.addRule(ruleString, callback.getStub(), format);
    }

    @SneakyThrows
    public void deleteRule(String ruleId) {
        server.deleteRule(ruleId);
    }

    @SneakyThrows
    public boolean enableRule(String ruleId) {
        return server.enableRule(ruleId);
    }

    @SneakyThrows
    public boolean disableRule(String ruleId) {
        return server.disableRule(ruleId);
    }

    @SneakyThrows
    public void checkRule(String ruleJson, RuleCheckCallback callback, int format) {
        server.checkRule(ruleJson, callback.getStub(), format);
    }

    @SneakyThrows
    public void isRuleEnabled(String ruleId) {
        server.isRuleEnabled(ruleId);
    }

    @SneakyThrows
    public boolean isRuleExists(String ruleName) {
        return server.isRuleExists(ruleName);
    }

    @SneakyThrows
    public RuleInfo[] getAllRules() {
        return server.getAllRules();
    }

    @SneakyThrows
    public RuleInfo[] getEnabledRules() {
        return server.getEnabledRules();
    }

    @SneakyThrows
    public void setProfileEnabled(boolean enable) {
        server.setProfileEnabled(enable);
    }

    @SneakyThrows
    public boolean isProfileEnabled() {
        return server.isProfileEnabled();
    }

    @SneakyThrows
    public boolean addGlobalRuleVar(String varName, String[] varArray) {
        return server.addGlobalRuleVar(varName, varArray);
    }

    @SneakyThrows
    public boolean appendGlobalRuleVar(String varName, String[] varArray) {
        return server.appendGlobalRuleVar(varName, varArray);
    }

    @SneakyThrows
    public boolean removeGlobalRuleVar(String varName) {
        return server.removeGlobalRuleVar(varName);
    }

    @SneakyThrows
    public String[] getAllGlobalRuleVarNames() {
        return server.getAllGlobalRuleVarNames();
    }

    @SneakyThrows
    public String[] getGlobalRuleVarByName(String varName) {
        return server.getGlobalRuleVarByName(varName);
    }

    @SneakyThrows
    public boolean isGlobalRuleVarByNameExists(String varName) {
        return server.isGlobalRuleVarByNameExists(varName);
    }

    @SneakyThrows
    public GlobalVar[] getAllGlobalRuleVar() {
        return server.getAllGlobalRuleVar();
    }
}
