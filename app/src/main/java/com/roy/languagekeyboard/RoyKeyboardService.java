package com.roy.languagekeyboard;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class RoyKeyboardService extends InputMethodService {

    private EditText etEnglishInput;
    private Translator englishChineseTranslator;
    private String lastTranslatedText = "";
    private boolean isNumMode = false;
    private boolean isUpperCase = false;
    private View mRootView;
    private String savedDraftText = "";
    private boolean isInternalDeleting = false; // 标志位：是否正在执行内部删除逻辑

    // 状态标记
    private boolean isModelReady = false;

    private Handler deleteHandler = new Handler();
    private long lastInternalActionTime = 0; // 记录最后一次内部操作的时间戳
    private Runnable deleteRunnable = new Runnable() {
        @Override
        public void run() {
            performDeletion();
            deleteHandler.postDelayed(this, 100);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        initTranslator(); // 封装初始化逻辑
    }

    private void initTranslator() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build();
        englishChineseTranslator = Translation.getClient(options);

        // 【修复点 1】强制允许移动网络下载，不设任何限制
        DownloadConditions conditions = new DownloadConditions.Builder()
                .build();

        englishChineseTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    isModelReady = true;
                    Log.d("RoyKey", "翻译引擎就绪");
                })
                .addOnFailureListener(e -> {
                    isModelReady = false;
                    Log.e("RoyKey", "模型下载/加载失败: " + e.getMessage());
                    // 如果失败，5秒后尝试静默重启
                    new Handler().postDelayed(this::initTranslator, 5000);
                });
    }

    @Override
    public View onCreateInputView() {
        if (etEnglishInput != null) {
            savedDraftText = etEnglishInput.getText().toString();
        }

        int layoutId = isNumMode ? R.layout.keyboard_numbers : R.layout.keyboard_main;
        View view = getLayoutInflater().inflate(layoutId, null);
        mRootView = view;

        etEnglishInput = view.findViewById(R.id.et_english_input);

        if (etEnglishInput != null) {
            etEnglishInput.setText(savedDraftText);
            etEnglishInput.setSelection(savedDraftText.length());
        }

        setupSpecialKeys(view);
        setupKeys(view);

        if (etEnglishInput != null) {
            etEnglishInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    realTranslate(s.toString());
                }
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        return view;
    }

    private void setupSpecialKeys(View view) {
        View btn123 = view.findViewById(R.id.btn_123);
        if (btn123 != null) btn123.setOnClickListener(v -> {
            isNumMode = true;
            setInputView(onCreateInputView());
        });

        View btnReturn = view.findViewById(R.id.btn_return_abc);
        if (btnReturn != null) btnReturn.setOnClickListener(v -> {
            isNumMode = false;
            setInputView(onCreateInputView());
        });

        View btnBackspace = view.findViewById(R.id.btn_backspace);
        if (btnBackspace != null) {
            btnBackspace.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    performDeletion();
                    deleteHandler.postDelayed(deleteRunnable, 500);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    deleteHandler.removeCallbacks(deleteRunnable);
                }
                return true;
            });
        }

        View btnSpace = view.findViewById(R.id.btn_space);
        if (btnSpace != null) {
            btnSpace.setOnClickListener(v -> {
                if (etEnglishInput != null) etEnglishInput.append(" ");
            });
        }

        View btnShift = view.findViewById(R.id.btn_shift);
        if (btnShift != null) {
            btnShift.setOnClickListener(v -> {
                isUpperCase = !isUpperCase;
                updateKeyBoardLetterCase();
            });
        }
    }

    private void setupKeys(View root) {
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) setupKeys(group.getChildAt(i));
        } else if (root instanceof Button) {
            Button btn = (Button) root;
            int id = btn.getId();

            if (id == R.id.btn_123 || id == R.id.btn_return_abc ||
                    id == R.id.btn_backspace || id == R.id.btn_space || id == R.id.btn_shift) {
                return;
            }

            btn.setOnClickListener(v -> {
                String text = btn.getText().toString();
                if (text.equals("↵")) {
                    handleEnterKey();
                } else {
                    if (isUpperCase && text.length() == 1 && Character.isLetter(text.charAt(0))) {
                        etEnglishInput.append(text.toUpperCase());
                    } else {
                        etEnglishInput.append(text.toLowerCase());
                    }
                }
            });
        }
    }

    private void updateKeyBoardLetterCase() {
        if (mRootView == null) return;
        updateButtonsRecursive(mRootView);
    }

    private void updateButtonsRecursive(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) updateButtonsRecursive(group.getChildAt(i));
        } else if (view instanceof Button) {
            Button btn = (Button) view;
            String text = btn.getText().toString();
            if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
                btn.setText(isUpperCase ? text.toUpperCase() : text.toLowerCase());
                btn.setBackgroundTintList(ColorStateList.valueOf(isUpperCase ? Color.LTGRAY : Color.WHITE));
            }
        }
    }

    private void realTranslate(String text) {
        if (text.isEmpty()) {
            lastTranslatedText = "";
            return;
        }

        if (!isModelReady) {
            initTranslator();
            return;
        }

        englishChineseTranslator.translate(text)
                .addOnSuccessListener(translatedText -> {
                    // 【核心修复】重新定义 cleanChinese 变量
                    String cleanChinese = translatedText.replace(" ", "");

                    if (cleanChinese.equals(lastTranslatedText)) return;

                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        // 【关键逻辑】记录内部操作的时间戳，防止退格 Bug
                        lastInternalActionTime = System.currentTimeMillis();

                        ic.beginBatchEdit();
                        ic.deleteSurroundingText(lastTranslatedText.length(), 0);
                        ic.commitText(cleanChinese, 1);
                        ic.endBatchEdit();
                        lastTranslatedText = cleanChinese;
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("RoyKey", "翻译执行错误: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("closed")) {
                        isModelReady = false;
                        initTranslator();
                    }
                });
    }

    private void performDeletion() {
        String currentEng = etEnglishInput.getText().toString();
        InputConnection ic = getCurrentInputConnection();

        // 记录操作时间
        lastInternalActionTime = System.currentTimeMillis();

        if (currentEng.length() > 0) {
            if (ic != null) ic.deleteSurroundingText(lastTranslatedText.length(), 0);
            // ... 后续代码不变

            String newEng = currentEng.substring(0, currentEng.length() - 1);
            etEnglishInput.setText(newEng);
            etEnglishInput.setSelection(newEng.length());

            if (newEng.isEmpty()) {
                lastTranslatedText = "";
            }

            // 【核心修改】删除结束，关闭标志位
            isInternalDeleting = false;
        } else {
            if (ic != null) ic.deleteSurroundingText(1, 0);
        }
    }

    private void handleEnterKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            // 发送回车
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            // 清空本地输入和记录
            if (etEnglishInput != null) etEnglishInput.setText("");
            lastTranslatedText = "";
        }
    }

    @Override
    public void onDestroy() {
        if (englishChineseTranslator != null) englishChineseTranslator.close();
        super.onDestroy();
    }
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);

        // 【核心防御】如果距离上次内部删字/翻译不到 300 毫秒，说明这是系统误报，直接返回
        if (System.currentTimeMillis() - lastInternalActionTime < 300) {
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence textBefore = ic.getTextBeforeCursor(1, 0);
            CharSequence textAfter = ic.getTextAfterCursor(1, 0);

            // 只有在确定不是我们在操作，且框真的空了时，才清空英文框
            if ((textBefore == null || textBefore.length() == 0) && (textAfter == null || textAfter.length() == 0)) {
                if (etEnglishInput != null && etEnglishInput.getText().length() > 0) {
                    etEnglishInput.setText("");
                    lastTranslatedText = "";
                    Log.d("RoyKey", "同步清空：检测到用户主动点击了发送按钮");
                }
            }
        }
    }
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // 每次重新进入输入框时，如果对方是空的，我们就重置自己的状态
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence current = ic.getTextBeforeCursor(1, 0);
            if (current == null || current.length() == 0) {
                if (etEnglishInput != null) etEnglishInput.setText("");
                lastTranslatedText = "";
                savedDraftText = "";
            }
        }
    }
}