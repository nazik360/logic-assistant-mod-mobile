package ai.logicassistant;

import arc.Events;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.mod.Mod;

public class LogicAssistantMod extends Mod {

    public LogicAssistantMod() {
        Log.info("[Logic Assistant] Mod loaded!");
        Events.on(EventType.ClientLoadEvent.class, event -> {
            Log.info("[Logic Assistant] Ready.");
            LogicAssistantUI.show();
        });
    }
}
