package com.speedywatch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

final class MegaBookmarksDialog {
    interface CurrentTimeCallback {
        void onTime(double seconds);
    }

    interface FolderNameCallback {
        void onName(String name);
    }

    interface Host {
        String currentMegaUrl();

        void currentMegaFolderName(FolderNameCallback callback);

        void currentTime(CurrentTimeCallback callback);

        void openMegaUrl(String url, double positionSeconds);
    }

    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(185, 185, 185);

    private final Activity activity;
    private final MegaBookmarkStore store;
    private final Host host;
    private final String initialUrl;
    private final List<MegaBookmarkStore.Entry> entries = new ArrayList<>();

    private Dialog dialog;
    private TextView status;
    private BookmarkAdapter adapter;
    private EditText urlInput;

    MegaBookmarksDialog(
            Activity activity,
            MegaBookmarkStore store,
            Host host,
            String initialUrl
    ) {
        this.activity = activity;
        this.store = store;
        this.host = host;
        this.initialUrl = initialUrl;
    }

    void show() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildContent());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
        refresh();
    }

    private View buildContent() {
        LinearLayout content = verticalLayout();
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        LinearLayout headerText = verticalLayout();
        TextView title = text("MEGA bookmarks", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        headerText.addView(title);
        status = text("Saved securely on this phone", 12, MUTED);
        headerText.addView(status);
        header.addView(headerText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close MEGA bookmarks");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);

        urlInput = new EditText(activity);
        urlInput.setSingleLine(true);
        urlInput.setHint("Complete MEGA folder or file link");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(MUTED);
        urlInput.setTextSize(14);
        urlInput.setPadding(dp(10), 0, dp(10), 0);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        if (initialUrl != null) {
            urlInput.setText(initialUrl);
            urlInput.setSelection(urlInput.length());
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        inputParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(urlInput, inputParams);

        LinearLayout actions = horizontalLayout();
        Button open = button("Open link");
        open.setBackground(panelBackground(ACTIVE, ACTIVE));
        open.setOnClickListener(ignored -> openEnteredUrl(urlInput));
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button bookmarkCurrent = button("Bookmark current");
        String currentUrl = host.currentMegaUrl();
        boolean canBookmark = SupportedSite.megaBookmarkIdentity(currentUrl) != null;
        bookmarkCurrent.setEnabled(canBookmark);
        bookmarkCurrent.setAlpha(canBookmark ? 1f : 0.45f);
        bookmarkCurrent.setContentDescription(canBookmark
                ? "Bookmark the current MEGA folder and playback position"
                : "Open a MEGA folder before bookmarking");
        bookmarkCurrent.setOnClickListener(ignored -> showNamePrompt());
        LinearLayout.LayoutParams bookmarkParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        bookmarkParams.setMarginStart(dp(8));
        actions.addView(bookmarkCurrent, bookmarkParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(0, 0, 0, dp(8));
        content.addView(actions, actionParams);

        TextView section = text("Saved folders", 14, Color.WHITE);
        section.setTypeface(section.getTypeface(), Typeface.BOLD);
        section.setPadding(0, dp(4), 0, dp(8));
        content.addView(section);

        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(0);
        adapter = new BookmarkAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                openBookmark(adapter.getItem(position)));
        TextView empty = text("Bookmark a MEGA folder while viewing it to return here later.", 14, MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(28), dp(28), dp(28));
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        content.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        list.setEmptyView(empty);

        urlInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openEnteredUrl(urlInput);
                return true;
            }
            return false;
        });
        return content;
    }

    private void openBookmark(MegaBookmarkStore.Entry entry) {
        String resolved = MegaBookmarkStore.resolveResumeUrl(entry, null);
        String candidateUrl = null;
        if (resolved == null) {
            candidateUrl = host.currentMegaUrl();
            resolved = MegaBookmarkStore.resolveResumeUrl(entry, candidateUrl);
        }
        if (resolved == null) {
            candidateUrl = urlInput.getText().toString();
            resolved = MegaBookmarkStore.resolveResumeUrl(entry, candidateUrl);
        }
        if (resolved == null) {
            urlInput.setError("Paste this older bookmark's complete shared link once");
            urlInput.requestFocus();
            Toast.makeText(
                    activity,
                    "This older bookmark needs its complete shared link once.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        if (candidateUrl != null) {
            try {
                store.rememberCompleteUrl(entry, candidateUrl);
            } catch (GeneralSecurityException error) {
                Toast.makeText(
                        activity,
                        "Could not secure this MEGA bookmark",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
        }
        host.openMegaUrl(resolved, entry.positionSeconds);
        dialog.dismiss();
    }

    private void openEnteredUrl(EditText input) {
        String valid = SupportedSite.supportedUrlFromText(input.getText().toString());
        if (valid == null || SupportedSite.forUrl(valid) != SupportedSite.MEGA) {
            input.setError("Paste a complete MEGA folder or file link");
            return;
        }
        host.openMegaUrl(valid, 0);
        dialog.dismiss();
    }

    private void showNamePrompt() {
        String currentUrl = host.currentMegaUrl();
        if (SupportedSite.megaBookmarkIdentity(currentUrl) == null) {
            Toast.makeText(activity, "Open a MEGA folder first", Toast.LENGTH_SHORT).show();
            return;
        }
        host.currentMegaFolderName(folderName -> {
            if (dialog != null && dialog.isShowing()) {
                showNamePrompt(currentUrl, folderName);
            }
        });
    }

    private void showNamePrompt(String currentUrl, String folderName) {
        EditText nameInput = new EditText(activity);
        nameInput.setSingleLine(true);
        nameInput.setText(MegaBookmarkStore.suggestedName(folderName));
        nameInput.setSelectAllOnFocus(true);
        nameInput.setTextColor(Color.WHITE);
        nameInput.setHintTextColor(MUTED);
        FrameNameInput container = new FrameNameInput(activity, nameInput, dp(20), dp(8));
        AlertDialog prompt = new AlertDialog.Builder(activity)
                .setTitle("Bookmark this MEGA folder")
                .setMessage("The complete shared link, selected item, and playback position are encrypted on this phone.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        prompt.setOnShowListener(ignored -> prompt
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String name = MegaBookmarkStore.normalizeName(nameInput.getText().toString());
                    host.currentTime(seconds -> saveCurrent(name, currentUrl, seconds, prompt));
                }));
        prompt.show();
    }

    private void saveCurrent(String name, String url, double seconds, AlertDialog prompt) {
        try {
            store.save(name, url, seconds);
            prompt.dismiss();
            refresh();
            Toast.makeText(activity, "MEGA folder bookmarked", Toast.LENGTH_SHORT).show();
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "Could not save this MEGA bookmark", Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        try {
            entries.clear();
            entries.addAll(store.list());
            adapter.notifyDataSetChanged();
            status.setText(entries.isEmpty()
                    ? "Saved securely on this phone"
                    : entries.size() + (entries.size() == 1 ? " saved folder" : " saved folders"));
        } catch (GeneralSecurityException error) {
            entries.clear();
            adapter.notifyDataSetChanged();
            status.setText("Bookmarks could not be unlocked");
        }
    }

    private void confirmDelete(MegaBookmarkStore.Entry entry) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete MEGA bookmark?")
                .setMessage(entry.name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (ignored, which) -> {
                    try {
                        store.delete(entry.folderPath);
                        refresh();
                    } catch (GeneralSecurityException error) {
                        Toast.makeText(
                                activity,
                                "Could not delete this MEGA bookmark",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .show();
    }

    private final class BookmarkAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public MegaBookmarkStore.Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MegaBookmarkStore.Entry entry = getItem(position);
            LinearLayout row = horizontalLayout();
            row.setPadding(dp(10), dp(6), dp(6), dp(6));
            row.setBackground(panelBackground(PANEL, Color.rgb(58, 58, 58)));

            LinearLayout labels = verticalLayout();
            TextView name = text(entry.name, 15, Color.WHITE);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);
            TextView resume = text(MegaBookmarkStore.resumeLabel(entry.positionSeconds), 12, MUTED);
            labels.addView(resume);
            row.addView(labels, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            ImageButton open = iconButton(
                    R.drawable.ic_open_external,
                    "Open " + entry.name,
                    Color.WHITE
            );
            open.setOnClickListener(ignored -> openBookmark(entry));
            LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            openParams.setMarginStart(dp(8));
            row.addView(open, openParams);

            ImageButton delete = iconButton(
                    R.drawable.ic_close,
                    "Delete " + entry.name,
                    ACTIVE
            );
            delete.setOnClickListener(ignored -> confirmDelete(entry));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            deleteParams.setMarginStart(dp(8));
            row.addView(delete, deleteParams);
            LinearLayout wrapper = verticalLayout();
            wrapper.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            wrapper.setPadding(0, 0, 0, dp(8));
            return wrapper;
        }
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private Button button(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(panelBackground(BUTTON, BUTTON));
        return button;
    }

    private ImageButton iconButton(int drawable, String description, int tint) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(drawable);
        button.setColorFilter(tint);
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(panelBackground(BUTTON, BUTTON));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable panelBackground(int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(6));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class FrameNameInput extends android.widget.FrameLayout {
        FrameNameInput(Activity activity, View input, int horizontalPadding, int verticalPadding) {
            super(activity);
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
            addView(input, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.getResources().getDisplayMetrics().density >= 1f
                            ? Math.round(48 * activity.getResources().getDisplayMetrics().density)
                            : 48
            ));
        }
    }
}
