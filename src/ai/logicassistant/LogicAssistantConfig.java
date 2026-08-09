package ai.logicassistant;

import arc.Core;

public final class LogicAssistantConfig {
    private static final String API_KEY_SETTING = "logic-assistant-api-key";

    private LogicAssistantConfig() {
    }

    public static String getApiKey() {
        return Core.settings.getString(API_KEY_SETTING, "");
    }

    public static void setApiKey(String apiKey) {
        Core.settings.put(API_KEY_SETTING, apiKey == null ? "" : apiKey.trim());
    }

    public static boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }
}
