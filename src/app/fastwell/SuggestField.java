package app.fastwell;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A text field that shows the rest of a matching name as faded text after what's typed. Tapping the faded
 * text or pressing the keyboard's Done key takes it; carrying on typing or leaving the field ignores it.
 */
final class SuggestField {
    final FrameLayout view;
    final EditText edit;
    private final TextView ghost;
    private final List<String> names;
    private String completion;

    SuggestField(Context c, String hint, List<String> names) {
        this.names = names;
        view = new FrameLayout(c);
        edit = Ui.field(c, hint, Ui.TXT);
        edit.setSelectAllOnFocus(false);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        view.addView(edit, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // Drawn over the field with the same padding and size, so its clear start lines up with the typed text.
        ghost = new TextView(c);
        ghost.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ghost.setTextColor(Ui.col(c, R.color.text2));
        ghost.setSingleLine(true);
        ghost.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        ghost.setPadding(edit.getPaddingLeft(), edit.getPaddingTop(), edit.getPaddingRight(), edit.getPaddingBottom());
        ghost.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ghost.setVisibility(View.GONE);
        view.addView(ghost, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        edit.addTextChangedListener(new Ui.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) { update(); }
        });
        edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focused) { update(); }
        });
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int action, KeyEvent e) {
                accept();
                return false;
            }
        });
        ghost.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                // Only the faded part is claimed; touches anywhere else fall through to the field.
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return onFadedText(e.getX());
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    accept();
                    v.performClick();
                }
                return true;
            }
        });
    }

    private boolean onFadedText(float x) {
        float start = edit.getPaddingLeft() + ghost.getPaint().measureText(edit.getText().toString());
        return x >= start && x <= edit.getPaddingLeft() + ghost.getPaint().measureText(completion);
    }

    private void accept() {
        if (completion == null) return;
        edit.setText(completion);
        edit.setSelection(edit.length());
    }

    private void update() {
        String typed = edit.getText().toString();
        completion = edit.hasFocus() ? FastingLogic.liftCompletion(typed, names) : null;
        // A name too long for the field would be cut off under the text, so it isn't offered.
        int room = edit.getWidth() - edit.getPaddingLeft() - edit.getPaddingRight();
        if (completion != null && ghost.getPaint().measureText(completion) > room) completion = null;
        if (completion == null) {
            ghost.setVisibility(View.GONE);
            return;
        }
        // The typed part stays clear so only the rest of the name shows.
        SpannableString text = new SpannableString(typed + completion.substring(typed.length()));
        text.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), 0, typed.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ghost.setText(text);
        ghost.setVisibility(View.VISIBLE);
    }
}
