/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;

/**
 * The "AI Logic" window: a provider dropdown (LM Studio | Groq | Claude | Gemini | Grok), a large
 * multi-line request field, provider-specific settings, and the Generate / Clear / Close controls.
 *
 * <p>For the LM Studio provider the API key is hidden and not required; the local URL and model are
 * shown instead. For Groq, Claude, Gemini and Grok the corresponding API key field is shown and
 * required before generating; Gemini and Grok also show a model field.
 */
public final class LogicAssistantUI {

  private static final String TITLE = "AI Logic";
  private static final String GENERATE_TEXT = "Сгенерировать";
  private static final String GENERATING_TEXT = "Генерация...";
  private static final String CLEAR_TEXT = "Очистить";
  private static final String CLOSE_TEXT = "Закрыть";
  private static final String PROVIDER_LABEL = "AI Provider:";
  private static final String API_KEY_LABEL = "API ключ:";
  private static final String URL_LABEL = "URL LM Studio:";
  private static final String MODEL_LABEL = "Модель:";
  private static final String GROQ_KEY_HINT = "API ключ Groq";
  private static final String ANTHROPIC_KEY_HINT = "API ключ Anthropic";
  private static final String GEMINI_KEY_HINT = "API ключ Gemini";
  private static final String GEMINI_MODEL_HINT =
      "Например: " + LogicAssistantConfig.GEMINI_DEFAULT_MODEL;
  private static final String GROK_KEY_HINT = "API ключ Grok";
  private static final String GROK_MODEL_HINT =
      "Например: " + LogicAssistantConfig.GROK_DEFAULT_MODEL;

  private static final float INPUT_MIN_WIDTH = 560f;
  private static final float INPUT_HEIGHT = 220f;
  private static final float BUTTON_WIDTH = 160f;
  private static final float BUTTON_HEIGHT = 54f;
  private static final float FIELD_HEIGHT = 40f;
  private static final float LABEL_WIDTH = 140f;
  private static final float DROPDOWN_WIDTH = 220f;

  private LogicAssistantUI() {}

  public static void show() {
    Dialog dialog = new Dialog(TITLE);

    TextArea request = new TextArea("");
    request.setMessageText(
        "Опишите, какой Logic Processor нужен.\n"
            + "Например: включить насос, когда уровень жидкости ниже 50%, и выключить выше 80%.");
    request.setMaxLength(8000);

    Label error = new Label("");
    error.setColor(Color.scarlet);
    error.setWrap(true);

    dialog.cont.margin(12f);
    dialog.cont.add(request).growX().height(INPUT_HEIGHT).minWidth(INPUT_MIN_WIDTH).row();
    dialog.cont.add(error).growX().padTop(4f).row();

    // Provider settings (rebuilt when the provider changes).
    Table settings = new Table();

    TextField groqKey = new TextField(LogicAssistantConfig.getGroqApiKey());
    groqKey.setMessageText(GROQ_KEY_HINT);
    groqKey.setMaxLength(200);

    TextField anthropicKey = new TextField(LogicAssistantConfig.getAnthropicApiKey());
    anthropicKey.setMessageText(ANTHROPIC_KEY_HINT);
    anthropicKey.setMaxLength(200);

    TextField geminiKey = new TextField(LogicAssistantConfig.getGeminiApiKey());
    geminiKey.setMessageText(GEMINI_KEY_HINT);
    geminiKey.setMaxLength(200);

    TextField geminiModel = new TextField(LogicAssistantConfig.getGeminiModel());
    geminiModel.setMessageText(GEMINI_MODEL_HINT);
    geminiModel.setMaxLength(200);

    TextField grokKey = new TextField(LogicAssistantConfig.getGrokApiKey());
    grokKey.setMessageText(GROK_KEY_HINT);
    grokKey.setMaxLength(200);

    TextField grokModel = new TextField(LogicAssistantConfig.getGrokModel());
    grokModel.setMessageText(GROK_MODEL_HINT);
    grokModel.setMaxLength(200);

    TextField url = new TextField(LogicAssistantConfig.getLocalUrl());
    url.setMessageText(LogicAssistantConfig.DEFAULT_LOCAL_URL);
    url.setMaxLength(300);

    TextField model = new TextField(LogicAssistantConfig.getLocalModel());
    model.setMessageText("Имя модели из LM Studio");
    model.setMaxLength(200);

    groqKey.changed(() -> LogicAssistantConfig.setGroqApiKey(groqKey.getText()));
    anthropicKey.changed(() -> LogicAssistantConfig.setAnthropicApiKey(anthropicKey.getText()));
    geminiKey.changed(() -> LogicAssistantConfig.setGeminiApiKey(geminiKey.getText()));
    geminiModel.changed(() -> LogicAssistantConfig.setGeminiModel(geminiModel.getText()));
    grokKey.changed(() -> LogicAssistantConfig.setGrokApiKey(grokKey.getText()));
    grokModel.changed(() -> LogicAssistantConfig.setGrokModel(grokModel.getText()));
    url.changed(() -> LogicAssistantConfig.setLocalUrl(url.getText()));
    model.changed(() -> LogicAssistantConfig.setLocalModel(model.getText()));

    Runnable rebuildSettings =
        () -> {
          settings.clearChildren();
          settings.defaults().growX();
          String provider = LogicAssistantConfig.getProvider();
          if (LogicAssistantConfig.PROVIDER_GROQ.equals(provider)) {
            addKeyField(settings, API_KEY_LABEL, groqKey);
          } else if (LogicAssistantConfig.PROVIDER_ANTHROPIC.equals(provider)) {
            addKeyField(settings, API_KEY_LABEL, anthropicKey);
          } else if (LogicAssistantConfig.PROVIDER_GEMINI.equals(provider)) {
            addKeyAndModelField(settings, API_KEY_LABEL, geminiKey, MODEL_LABEL, geminiModel);
          } else if (LogicAssistantConfig.PROVIDER_GROK.equals(provider)) {
            addKeyAndModelField(settings, API_KEY_LABEL, grokKey, MODEL_LABEL, grokModel);
          } else {
            settings.add(new Label(URL_LABEL)).left().padRight(8f).width(LABEL_WIDTH);
            settings.add(url).height(FIELD_HEIGHT).row();
            settings.add(new Label(MODEL_LABEL)).left().padRight(8f).width(LABEL_WIDTH);
            settings.add(model).height(FIELD_HEIGHT);
          }
        };

    TextButton providerButton =
        new TextButton(
            LogicAssistantConfig.providerDisplayName(LogicAssistantConfig.getProvider()));
    providerButton.clicked(() -> showProviderMenu(providerButton, rebuildSettings));

    dialog
        .cont
        .table(
            t -> {
              t.add(new Label(PROVIDER_LABEL)).padRight(8f);
              t.add(providerButton).width(DROPDOWN_WIDTH).height(FIELD_HEIGHT);
            })
        .growX()
        .padTop(6f)
        .row();

    dialog.cont.add(settings).growX().padTop(6f).row();

    TextButton generate = new TextButton(GENERATE_TEXT);
    TextButton clear = new TextButton(CLEAR_TEXT);
    TextButton close = new TextButton(CLOSE_TEXT);

    dialog
        .cont
        .table(
            t -> {
              t.defaults().size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(4f);
              t.add(generate);
              t.add(clear);
              t.add(close);
            })
        .growX()
        .padTop(8f);

    clear.clicked(() -> request.setText(""));
    close.clicked(dialog::hide);

    generate.clicked(() -> generate(dialog, request, error, generate));

    rebuildSettings.run();
    dialog.show();
  }

  private static void addKeyField(Table settings, String label, TextField field) {
    settings.add(new Label(label)).left().padRight(8f).width(LABEL_WIDTH);
    settings.add(field).height(FIELD_HEIGHT);
  }

  private static void addKeyAndModelField(
      Table settings,
      String keyLabel,
      TextField keyField,
      String modelLabel,
      TextField modelField) {
    addKeyField(settings, keyLabel, keyField);
    settings.row();
    settings.add(new Label(modelLabel)).left().padRight(8f).width(LABEL_WIDTH);
    settings.add(modelField).height(FIELD_HEIGHT);
  }

  /** Opens a small dialog listing all providers; picking one updates the button and the fields. */
  private static void showProviderMenu(TextButton providerButton, Runnable rebuildSettings) {
    Dialog menu = new Dialog("Выберите провайдера");
    menu.cont.defaults().height(FIELD_HEIGHT).pad(4f);
    addProviderOption(menu, providerButton, rebuildSettings, LogicAssistantConfig.PROVIDER_LOCAL);
    addProviderOption(menu, providerButton, rebuildSettings, LogicAssistantConfig.PROVIDER_GROQ);
    addProviderOption(
        menu, providerButton, rebuildSettings, LogicAssistantConfig.PROVIDER_ANTHROPIC);
    addProviderOption(menu, providerButton, rebuildSettings, LogicAssistantConfig.PROVIDER_GEMINI);
    addProviderOption(menu, providerButton, rebuildSettings, LogicAssistantConfig.PROVIDER_GROK);
    menu.show();
  }

  private static void addProviderOption(
      Dialog menu, TextButton providerButton, Runnable rebuildSettings, String provider) {
    TextButton option = new TextButton(LogicAssistantConfig.providerDisplayName(provider));
    option.clicked(
        () -> {
          LogicAssistantConfig.setProvider(provider);
          providerButton.setText(LogicAssistantConfig.providerDisplayName(provider));
          rebuildSettings.run();
          menu.hide();
        });
    menu.cont.add(option).width(DROPDOWN_WIDTH).row();
  }

  private static void generate(Dialog dialog, TextArea request, Label error, TextButton generate) {
    error.setText("");

    String text = request.getText();
    if (text == null || text.trim().isEmpty()) {
      error.setText("Запрос пуст. Опишите, какой процессор нужен.");
      return;
    }

    // Loading state: prevent multiple concurrent requests.
    generate.setDisabled(true);
    generate.setText(GENERATING_TEXT);

    LogicCodeGenerator.generate(
        text,
        result -> {
          Core.app.post(
              () -> {
                // The dialog may have been closed while the request was in flight.
                if (!dialog.isShown()) return;

                generate.setDisabled(false);
                generate.setText(GENERATE_TEXT);

                if (result.ok) {
                  dialog.hide();
                  LogicProcessorOpener.open(result.code, LogicAssistantUI::show);
                } else {
                  error.setText(result.error == null ? "Неизвестная ошибка." : result.error);
                }
              });
        });
  }
}
