package ai.logicassistant;

import arc.scene.Element;
import arc.scene.ui.Dialog;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;

public final class LogicAssistantUI {

    private LogicAssistantUI() {
    }

    public static void show() {
        Dialog dialog = new Dialog("Logic Assistant");

        TextArea request = new TextArea("");
        request.setMessageText("Например: сделай процессор для сортировки меди...");
        dialog.cont.add((Element) request).growX().height(120f).row();

        TextButton generate = new TextButton("Сгенерировать");
        TextButton close = new TextButton("Закрыть");

        dialog.cont.add((Element) generate).pad(5f).row();
        dialog.cont.add((Element) close).pad(5f).row();

        generate.clicked(() -> {
            String result = LogicCodeGenerator.generate(request.getText());
            Dialog resultDialog = new Dialog("Результат");
            TextArea output = new TextArea(result);
            resultDialog.cont.add((Element) output).grow().minWidth(500f).minHeight(300f);
            resultDialog.addCloseButton();
            resultDialog.show();
        });

        close.clicked(dialog::hide);

        dialog.addCloseButton();
        dialog.show();
    }
}
