package com.speedywatch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class SavedSummariesDialog {
    interface Host {
        void openVideo(String url);
    }

    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(185, 185, 185);

    private final Activity activity;
    private final SavedSummaryStore store;
    private final Host host;

    private Dialog dialog;
    private TextView status;
    private EditText search;
    private SavedSummaryAdapter adapter;
    private Button creatorFilterButton;
    private Button sortDirectionButton;
    private String selectedCreator;
    private boolean sortDescending = true;

    SavedSummariesDialog(Activity activity, SavedSummaryStore store, Host host) {
        this.activity = activity;
        this.store = store;
        this.host = host;
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
        TextView title = text("Saved", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        headerText.addView(title);
        status = text("Loading saved items...", 12, MUTED);
        headerText.addView(status);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close saved items");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);

        search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search saved content or creator...");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(175, 175, 175));
        search.setTextSize(14);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        searchParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(search, searchParams);
        LinearLayout sorting = horizontalLayout();
        creatorFilterButton = button("All creators ▾");
        creatorFilterButton.setSingleLine(true);
        creatorFilterButton.setEllipsize(TextUtils.TruncateAt.END);
        creatorFilterButton.setContentDescription("Filter saved items by creator");
        creatorFilterButton.setOnClickListener(ignored -> showCreatorPicker());
        sorting.addView(creatorFilterButton, new LinearLayout.LayoutParams(0, dp(44), 2f));

        sortDirectionButton = button("Newest");
        sortDirectionButton.setContentDescription("Show oldest saved items first");
        sortDirectionButton.setOnClickListener(ignored -> {
            sortDescending = !sortDescending;
            updateSortControls();
            adapter.filter(search.getText().toString());
        });
        LinearLayout.LayoutParams directionParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        directionParams.setMarginStart(dp(8));
        sorting.addView(sortDirectionButton, directionParams);
        LinearLayout.LayoutParams sortingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sortingParams.setMargins(0, 0, 0, dp(8));
        content.addView(sorting, sortingParams);

        FrameLayout body = new FrameLayout(activity);
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(0);
        adapter = new SavedSummaryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetail(adapter.getItem(position)));
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView empty = text("Saved summaries and quizzes appear here.", 14, MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(28), dp(28), dp(28));
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        list.setEmptyView(empty);
        content.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                adapter.filter(editable.toString());
                updateStatus();
            }
        });
        return content;
    }

    private void refresh() {
        try {
            adapter.setEntries(store.loadAll());
            if (selectedCreator != null && !adapter.hasCreator(selectedCreator)) {
                selectedCreator = null;
                updateSortControls();
                adapter.filter(search == null ? "" : search.getText().toString());
            }
            updateStatus();
        } catch (RuntimeException error) {
            adapter.setEntries(new ArrayList<>());
            status.setText("Saved items could not be loaded");
            Toast.makeText(activity, "Saved items could not be loaded", Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        int total = adapter.getTotalCount();
        int visible = adapter.getCount();
        status.setText(visible == total
                ? total + (total == 1 ? " saved item" : " saved items")
                : visible + " of " + total + " saved items");
    }

    private void updateSortControls() {
        String creatorLabel = selectedCreator == null
                ? "All creators"
                : (selectedCreator.isEmpty() ? "Unknown creator" : selectedCreator);
        creatorFilterButton.setText(creatorLabel + " ▾");
        creatorFilterButton.setContentDescription(
                "Filter saved items by creator. Current selection: " + creatorLabel
        );
        sortDirectionButton.setText(sortDescending ? "Newest" : "Oldest");
        sortDirectionButton.setContentDescription(sortDescending
                ? "Show oldest saved items first"
                : "Show newest saved items first");
    }

    private void showCreatorPicker() {
        Dialog picker = new Dialog(activity);
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = verticalLayout();
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        TextView title = text("Choose creator", 20, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close creator picker");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> picker.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);

        EditText creatorSearch = new EditText(activity);
        creatorSearch.setSingleLine(true);
        creatorSearch.setHint("Search creators...");
        creatorSearch.setTextColor(Color.WHITE);
        creatorSearch.setHintTextColor(Color.rgb(175, 175, 175));
        creatorSearch.setTextSize(14);
        creatorSearch.setPadding(dp(10), 0, dp(10), 0);
        creatorSearch.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        creatorSearch.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        LinearLayout.LayoutParams creatorSearchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        creatorSearchParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(creatorSearch, creatorSearchParams);

        CreatorPickerAdapter creatorAdapter = new CreatorPickerAdapter();
        creatorAdapter.setCreators(adapter.creatorCounts(), adapter.getTotalCount());
        ListView creators = new ListView(activity);
        creators.setDivider(null);
        creators.setDividerHeight(0);
        creators.setAdapter(creatorAdapter);
        creators.setOnItemClickListener((parent, view, position, id) -> {
            selectedCreator = creatorAdapter.getItem(position).channelName;
            updateSortControls();
            adapter.filter(search.getText().toString());
            updateStatus();
            picker.dismiss();
        });
        int availableHeight = activity.getResources().getDisplayMetrics().heightPixels - dp(260);
        content.addView(creators, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(440), Math.max(dp(132), availableHeight))
        ));

        creatorSearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                creatorAdapter.filter(editable.toString());
            }
        });

        picker.setContentView(content);
        Window window = picker.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        picker.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private void showDetail(SavedSummaryStore.Entry entry) {
        Dialog detail = new Dialog(activity);
        detail.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = verticalLayout();
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        LinearLayout headerText = verticalLayout();
        TextView title = text(entry.videoTitle, 20, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        headerText.addView(title);
        TextView metadata = text(detailMetadata(entry), 12, MUTED);
        headerText.addView(metadata);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close saved item");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> detail.dismiss());
        LinearLayout.LayoutParams detailCloseParams =
                new LinearLayout.LayoutParams(dp(42), dp(42));
        detailCloseParams.setMarginStart(dp(8));
        header.addView(close, detailCloseParams);
        content.addView(header);

        TextView sourceLabel = text("Original video URL", 12, MUTED);
        LinearLayout.LayoutParams sourceLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sourceLabelParams.setMargins(0, dp(12), 0, dp(8));
        content.addView(sourceLabel, sourceLabelParams);

        TextView sourceUrl = text(entry.sourceUrl, 13, Color.rgb(90, 180, 255));
        sourceUrl.setTextIsSelectable(true);
        sourceUrl.setMaxLines(2);
        sourceUrl.setEllipsize(TextUtils.TruncateAt.END);
        sourceUrl.setPadding(dp(10), dp(8), dp(10), dp(8));
        sourceUrl.setBackground(panelBackground(PANEL, Color.rgb(70, 70, 70)));
        sourceUrl.setOnClickListener(ignored -> openVideo(entry, detail));
        content.addView(sourceUrl);

        LinearLayout actions = horizontalLayout();
        Button openVideo = button("Open video");
        openVideo.setBackground(panelBackground(ACTIVE, ACTIVE));
        openVideo.setOnClickListener(ignored -> openVideo(entry, detail));
        actions.addView(openVideo, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button share = button("Share");
        share.setOnClickListener(ignored -> TextShare.showChooser(
                activity,
                entry.videoTitle,
                entry.summaryLabel,
                entry.summaryText,
                entry.sourceUrl
        ));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        shareParams.setMarginStart(dp(8));
        actions.addView(share, shareParams);

        Button delete = button("Delete");
        delete.setTextColor(ACTIVE);
        delete.setOnClickListener(ignored -> confirmDelete(entry, detail));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        deleteParams.setMarginStart(dp(8));
        actions.addView(delete, deleteParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(actions, actionsParams);

        TextView summary = text("", 14, Color.WHITE);
        summary.setTextIsSelectable(true);
        summary.setMovementMethod(LinkMovementMethod.getInstance());
        summary.setLinkTextColor(Color.rgb(90, 180, 255));
        summary.setLineSpacing(0, 1.18f);
        summary.setPadding(dp(10), dp(8), dp(10), dp(10));
        summary.setText(MarkdownRenderer.render(
                entry.summaryText,
                activity.getResources().getDisplayMetrics().density
        ));
        ScrollView summaryScroll = new ScrollView(activity);
        summaryScroll.addView(summary);
        content.addView(summaryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        detail.setContentView(content);
        Window window = detail.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        detail.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private void openVideo(SavedSummaryStore.Entry entry, Dialog detail) {
        if (!SavedSummaryStore.isSupportedSourceUrl(entry.sourceUrl)) {
            Toast.makeText(activity, "Original video URL is unavailable", Toast.LENGTH_LONG).show();
            return;
        }
        host.openVideo(entry.sourceUrl);
        detail.dismiss();
        dialog.dismiss();
    }

    private void confirmDelete(SavedSummaryStore.Entry entry, Dialog detail) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete saved item?")
                .setMessage("This removes the saved item from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (confirmation, which) -> {
                    try {
                        if (store.delete(entry.id)) {
                            detail.dismiss();
                            refresh();
                            Toast.makeText(activity, "Saved item deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, "Saved item could not be deleted", Toast.LENGTH_LONG).show();
                        }
                    } catch (RuntimeException error) {
                        Toast.makeText(activity, "Saved item could not be deleted", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private String formatDate(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(timestamp));
    }

    private String formatTime(long timestamp) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(timestamp));
    }

    private String detailMetadata(SavedSummaryStore.Entry entry) {
        String channel = entry.channelName.isEmpty() ? "" : " | " + entry.channelName;
        return entry.summaryLabel + channel + " | " + formatDate(entry.createdAt);
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

    private Button button(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
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

    private LinearLayout groupDivider() {
        LinearLayout divider = horizontalLayout();
        divider.setPadding(dp(10), dp(8), dp(10), dp(4));
        View leadingLine = new View(activity);
        leadingLine.setBackgroundColor(Color.rgb(70, 70, 70));
        divider.addView(leadingLine, new LinearLayout.LayoutParams(0, dp(1), 1f));
        TextView label = text("", 10, MUTED);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(dp(8), 0, dp(8), 0);
        divider.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        View trailingLine = new View(activity);
        trailingLine.setBackgroundColor(Color.rgb(70, 70, 70));
        divider.addView(trailingLine, new LinearLayout.LayoutParams(0, dp(1), 1f));
        return divider;
    }

    private final class SavedSummaryAdapter extends BaseAdapter {
        private final List<SavedSummaryStore.Entry> all = new ArrayList<>();
        private final List<SavedSummaryStore.Entry> visible = new ArrayList<>();

        void setEntries(List<SavedSummaryStore.Entry> entries) {
            all.clear();
            all.addAll(entries);
            filter(search == null ? "" : search.getText().toString());
        }

        void filter(String query) {
            visible.clear();
            for (SavedSummaryStore.Entry entry : all) {
                if (SavedListOrder.matchesEntry(entry, query, selectedCreator)) {
                    visible.add(entry);
                }
            }
            visible.sort((left, right) ->
                    SavedListOrder.compareEntries(left, right, sortDescending));
            notifyDataSetChanged();
        }

        int getTotalCount() {
            return all.size();
        }

        List<SavedListOrder.CreatorCount> creatorCounts() {
            return SavedListOrder.creatorCounts(all);
        }

        boolean hasCreator(String creator) {
            for (SavedSummaryStore.Entry entry : all) {
                if (creator.isEmpty()
                        ? entry.channelName.isEmpty()
                        : creator.equalsIgnoreCase(entry.channelName)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public SavedSummaryStore.Entry getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            LinearLayout divider;
            TextView dividerLabel;
            LinearLayout item;
            TextView title;
            TextView metadata;
            TextView excerpt;
            if (convertView instanceof LinearLayout existing) {
                row = existing;
                divider = (LinearLayout) row.getChildAt(0);
                dividerLabel = (TextView) divider.getChildAt(1);
                item = (LinearLayout) row.getChildAt(1);
                title = (TextView) item.getChildAt(0);
                metadata = (TextView) item.getChildAt(1);
                excerpt = (TextView) item.getChildAt(2);
            } else {
                row = verticalLayout();
                divider = groupDivider();
                dividerLabel = (TextView) divider.getChildAt(1);
                row.addView(divider);

                item = verticalLayout();
                item.setPadding(dp(10), dp(8), dp(10), dp(10));
                title = text("", 15, Color.WHITE);
                title.setTypeface(title.getTypeface(), Typeface.BOLD);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                item.addView(title);
                metadata = text("", 12, MUTED);
                LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                metadataParams.setMargins(0, dp(2), 0, dp(5));
                item.addView(metadata, metadataParams);
                excerpt = text("", 13, Color.rgb(225, 225, 225));
                excerpt.setMaxLines(3);
                excerpt.setEllipsize(TextUtils.TruncateAt.END);
                excerpt.setLineSpacing(0, 1.1f);
                item.addView(excerpt);
                row.addView(item, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            SavedSummaryStore.Entry entry = getItem(position);
            boolean startsGroup = position == 0 || !sameGroup(entry, getItem(position - 1));
            divider.setVisibility(startsGroup ? View.VISIBLE : View.GONE);
            if (startsGroup) {
                dividerLabel.setText(groupLabel(entry));
            }
            title.setText(entry.videoTitle);
            metadata.setText(listMetadata(entry));
            excerpt.setText(preview(entry.summaryText));
            row.setBackgroundColor(BACKGROUND);
            return row;
        }

        private boolean sameGroup(
                SavedSummaryStore.Entry left,
                SavedSummaryStore.Entry right
        ) {
            Calendar leftDate = Calendar.getInstance();
            leftDate.setTimeInMillis(left.createdAt);
            Calendar rightDate = Calendar.getInstance();
            rightDate.setTimeInMillis(right.createdAt);
            return leftDate.get(Calendar.ERA) == rightDate.get(Calendar.ERA)
                    && leftDate.get(Calendar.YEAR) == rightDate.get(Calendar.YEAR)
                    && leftDate.get(Calendar.DAY_OF_YEAR) == rightDate.get(Calendar.DAY_OF_YEAR);
        }

        private String groupLabel(SavedSummaryStore.Entry entry) {
            return SavedListOrder.dayLabel(
                    entry.createdAt,
                    System.currentTimeMillis(),
                    Locale.getDefault(),
                    TimeZone.getDefault()
            );
        }

        private String listMetadata(SavedSummaryStore.Entry entry) {
            String channel = entry.channelName.isEmpty() ? "" : " | " + entry.channelName;
            return entry.summaryLabel + channel + " | " + formatTime(entry.createdAt);
        }
    }

    private final class CreatorPickerAdapter extends BaseAdapter {
        private final List<SavedListOrder.CreatorCount> all = new ArrayList<>();
        private final List<SavedListOrder.CreatorCount> visible = new ArrayList<>();

        void setCreators(List<SavedListOrder.CreatorCount> creators, int totalCount) {
            all.clear();
            all.add(new SavedListOrder.CreatorCount(null, totalCount));
            all.addAll(creators);
            filter("");
        }

        void filter(String query) {
            String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (SavedListOrder.CreatorCount creator : all) {
                if (normalized.isEmpty()
                        || creator.displayName().toLowerCase(Locale.ROOT).contains(normalized)) {
                    visible.add(creator);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public SavedListOrder.CreatorCount getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView existing
                    ? existing
                    : text("", 14, Color.WHITE);
            SavedListOrder.CreatorCount creator = getItem(position);
            boolean selected = selectedCreator == null
                    ? creator.channelName == null
                    : creator.channelName != null
                    && selectedCreator.equalsIgnoreCase(creator.channelName);
            row.setText(
                    (selected ? "✓ " : "")
                            + creator.displayName()
                            + " ("
                            + creator.count
                            + ")"
            );
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(44));
            row.setPadding(dp(10), 0, dp(10), 0);
            row.setBackground(selected
                    ? panelBackground(PANEL, ACTIVE)
                    : panelBackground(BACKGROUND, BACKGROUND));
            return row;
        }
    }

    private static String preview(String value) {
        StringBuilder result = new StringBuilder(Math.min(183, value.length()));
        boolean pendingSpace = false;
        boolean lineStart = true;
        boolean truncated = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (lineStart && (character == '#' || character == ' ' || character == '\t')) {
                continue;
            }
            if (Character.isWhitespace(character)) {
                pendingSpace = result.length() > 0;
                lineStart = character == '\n' || character == '\r';
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            if (result.length() >= 180) {
                truncated = true;
                break;
            }
            result.append(character);
            lineStart = false;
        }
        if (truncated) {
            result.append("...");
        }
        return result.toString();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence value, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence value, int start, int before, int count) {
        }
    }
}
