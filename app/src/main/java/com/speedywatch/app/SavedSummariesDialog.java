package com.speedywatch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.style.BackgroundColorSpan;
import android.text.InputType;
import android.text.TextUtils;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.concurrent.ExecutorService;

final class SavedSummariesDialog {
    interface Host {
        void openVideo(String url);

        void openLink(String url);
    }

    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(185, 185, 185);

    private final Activity activity;
    private final SavedSummaryStore store;
    private final ScrapedLinkStore scrapedLinkStore;
    private final ExecutorService executor;
    private final boolean thumbnailsEnabled;
    private final Host host;
    private Dialog dialog;
    private TextView status;
    private EditText search;
    private SavedSummaryAdapter adapter;
    private Button creatorFilterButton;
    private Button typeFilterButton;
    private Button sortDirectionButton;
    private String selectedCreator;
    private boolean sortDescending = true;
    private int typeFilter = TYPE_ALL;

    private static final int TYPE_ALL = 0;
    private static final int TYPE_VIDEOS = 1;
    private static final int TYPE_LINKS = 2;
    SavedSummariesDialog(
            Activity activity,
            SavedSummaryStore store,
            ScrapedLinkStore scrapedLinkStore,
            ExecutorService executor,
            boolean thumbnailsEnabled,
            Host host
    ) {
        this.activity = activity;
        this.store = store;
        this.scrapedLinkStore = scrapedLinkStore;
        this.executor = executor;
        this.thumbnailsEnabled = thumbnailsEnabled;
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

        typeFilterButton = button("All ▾");
        typeFilterButton.setSingleLine(true);
        typeFilterButton.setEllipsize(TextUtils.TruncateAt.END);
        typeFilterButton.setContentDescription("Filter saved items by type");
        typeFilterButton.setOnClickListener(ignored -> showTypePicker());
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        typeParams.setMarginStart(dp(8));
        sorting.addView(typeFilterButton, typeParams);

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
        list.setOnItemClickListener((parent, view, position, id) -> {
            SavedItem item = adapter.getItem(position);
            if (item.isLink) {
                showLinkDetail(item);
            } else {
                showDetail(item.saved);
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView empty = text("Saved summaries, quizzes, and links appear here.", 14, MUTED);
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
            List<SavedItem> items = new ArrayList<>();
            for (SavedSummaryStore.Entry entry : store.loadAll()) {
                items.add(SavedItem.forSaved(entry));
            }
            for (ScrapedLinkStore.Entry link : scrapedLinkStore.list()) {
                items.add(SavedItem.forLink(link));
            }
            adapter.setItems(items);
            if (selectedCreator != null && !adapter.hasCreator(selectedCreator)) {
                selectedCreator = null;
                updateSortControls();
                adapter.filter(search == null ? "" : search.getText().toString());
            }
            updateStatus();
        } catch (RuntimeException error) {
            adapter.setItems(new ArrayList<>());
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
        String typeLabel = typeFilter == TYPE_VIDEOS
                ? "Videos"
                : (typeFilter == TYPE_LINKS ? "Links" : "All");
        typeFilterButton.setContentDescription(
                "Filter saved items by type. Current selection: " + typeLabel
        );
        sortDirectionButton.setText(sortDescending ? "Newest" : "Oldest");
        sortDirectionButton.setContentDescription(sortDescending
                ? "Show oldest saved items first"
                : "Show newest saved items first");
    }

    private void showTypePicker() {
        CharSequence[] options = {"All", "Videos (summaries and quizzes)", "Links"};
        new AlertDialog.Builder(activity)
                .setTitle("Filter by type")
                .setSingleChoiceItems(
                        options,
                        typeFilter,
                        (dialog, which) -> {
                            typeFilter = which;
                            updateSortControls();
                            adapter.filter(search.getText().toString());
                            updateStatus();
                            dialog.dismiss();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
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
        boolean supportsThumbnail = thumbnailsEnabled
                && SavedThumbnail.urlFor(entry.sourceUrl) != null;
        ImageView thumbnailPreview = new ImageView(activity);
        thumbnailPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnailPreview.setBackground(panelBackground(Color.BLACK, Color.rgb(70, 70, 70)));
        thumbnailPreview.setClipToOutline(true);
        thumbnailPreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Bitmap detailBitmap = decodeThumbnail(entry.thumbnail);
        if (detailBitmap == null) {
            thumbnailPreview.setVisibility(View.GONE);
        } else {
            thumbnailPreview.setImageBitmap(detailBitmap);
        }
        if (supportsThumbnail) {
            LinearLayout.LayoutParams thumbnailParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(150)
            );
            thumbnailParams.setMargins(0, dp(12), 0, 0);
            content.addView(thumbnailPreview, thumbnailParams);
        }


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

        LinearLayout actions = horizontalLayout();
        ImageButton openVideo = detailIconButton(
                R.drawable.ic_open_external, "Open original video");
        openVideo.setBackground(panelBackground(ACTIVE, ACTIVE));
        openVideo.setOnClickListener(ignored -> openVideo(entry, detail));
        actions.addView(openVideo, detailActionParams(false));

        ImageButton findToggle = detailIconButton(R.drawable.ic_search, "Search in summary");
        actions.addView(findToggle, detailActionParams(true));

        ImageButton share = detailIconButton(R.drawable.ic_share, "Share summary");
        share.setOnClickListener(ignored -> TextShare.showChooser(
                activity,
                entry.videoTitle,
                entry.summaryLabel,
                entry.summaryText,
                entry.sourceUrl
        ));
        actions.addView(share, detailActionParams(true));

        if (supportsThumbnail) {
            ImageButton thumbnailAction = detailIconButton(
                    R.drawable.ic_refresh,
                    detailBitmap == null
                            ? "Add video thumbnail"
                            : "Refresh video thumbnail"
            );
            thumbnailAction.setOnClickListener(ignored ->
                    regenerateThumbnail(entry, detail, thumbnailPreview, thumbnailAction));
            actions.addView(thumbnailAction, detailActionParams(true));
        }

        ImageButton delete = detailIconButton(R.drawable.ic_delete, "Delete saved item");
        delete.setOnClickListener(ignored -> confirmDelete(entry, detail));
        actions.addView(delete, detailActionParams(true));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(actions, actionsParams);

        LinearLayout findBar = horizontalLayout();
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setVisibility(View.GONE);
        EditText findInput = new EditText(activity);
        findInput.setSingleLine(true);
        findInput.setHint("Find in summary");
        findInput.setTextSize(13);
        findInput.setTextColor(Color.WHITE);
        findInput.setHintTextColor(MUTED);
        findInput.setBackground(panelBackground(PANEL, Color.rgb(70, 70, 70)));
        findInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams findInputParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        findBar.addView(findInput, findInputParams);
        TextView findCount = text("", 12, MUTED);
        findCount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams findCountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        findCountParams.setMarginStart(dp(8));
        findBar.addView(findCount, findCountParams);
        SummaryFinder finder = new SummaryFinder(summary, summaryScroll, findCount);
        findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                finder.update(s.toString());
            }
        });
        ImageButton findPrev = detailIconButton(R.drawable.ic_arrow_up, "Previous match");
        findPrev.setOnClickListener(ignored -> finder.go(-1));
        findBar.addView(findPrev, findArrowParams());
        ImageButton findNext = detailIconButton(R.drawable.ic_arrow_down, "Next match");
        findNext.setOnClickListener(ignored -> finder.go(1));
        findBar.addView(findNext, findArrowParams());
        ImageButton findClose = detailIconButton(R.drawable.ic_close, "Close search");
        findClose.setOnClickListener(ignored -> {
            finder.clear();
            findInput.setText("");
            toggleFindBar(findBar, findInput, thumbnailPreview);
        });
        findBar.addView(findClose, findArrowParams());
        LinearLayout.LayoutParams findBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        findBarParams.setMargins(0, dp(8), 0, 0);
        content.addView(findBar, findBarParams);
        findToggle.setOnClickListener(ignored -> toggleFindBar(findBar, findInput, thumbnailPreview));

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

    /** One row in the unified Saved list: either a saved summary/quiz or a link. */
    private static final class SavedItem {
        final SavedSummaryStore.Entry saved;
        final ScrapedLinkStore.Entry link;
        final boolean isLink;

        private SavedItem(SavedSummaryStore.Entry saved, ScrapedLinkStore.Entry link) {
            this.saved = saved;
            this.link = link;
            this.isLink = link != null;
        }

        static SavedItem forSaved(SavedSummaryStore.Entry entry) {
            return new SavedItem(entry, null);
        }

        static SavedItem forLink(ScrapedLinkStore.Entry entry) {
            return new SavedItem(null, entry);
        }

        long datedAt() {
            return isLink ? link.datedAt() : saved.createdAt;
        }

        long sortId() {
            return isLink ? link.id : saved.id;
        }
    }

    private void showLinkDetail(SavedItem item) {
        ScrapedLinkStore.Entry link = item.link;
        Dialog detail = new Dialog(activity);
        detail.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = verticalLayout();
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        LinearLayout headerText = verticalLayout();
        TextView title = text(link.displayText.isEmpty() ? link.url : link.displayText,
                20, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        headerText.addView(title);
        String poster = link.posterName.isEmpty() ? "" : " | " + link.posterName;
        headerText.addView(text(
                "Link" + poster + " | " + formatDate(link.datedAt()), 12, MUTED));
        header.addView(headerText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close link");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> detail.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(panelBackground(Color.BLACK, Color.rgb(70, 70, 70)));
        preview.setClipToOutline(true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Bitmap linkBitmap = decodePreview(link.preview);
        if (linkBitmap != null) {
            preview.setImageBitmap(linkBitmap);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)
            );
            previewParams.setMargins(0, dp(12), 0, 0);
            content.addView(preview, previewParams);
        }

        TextView sourceLabel = text("Link URL", 12, MUTED);
        LinearLayout.LayoutParams sourceLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sourceLabelParams.setMargins(0, dp(12), 0, dp(8));
        content.addView(sourceLabel, sourceLabelParams);
        TextView urlView = text(link.url, 13, Color.rgb(90, 180, 255));
        urlView.setTextIsSelectable(true);
        urlView.setMaxLines(4);
        urlView.setEllipsize(TextUtils.TruncateAt.END);
        urlView.setPadding(dp(10), dp(8), dp(10), dp(8));
        urlView.setBackground(panelBackground(PANEL, Color.rgb(70, 70, 70)));
        urlView.setOnClickListener(ignored -> host.openLink(link.url));
        content.addView(urlView);

        LinearLayout actions = horizontalLayout();
        ImageButton open = detailIconButton(R.drawable.ic_open_external, "Open link");
        open.setBackground(panelBackground(ACTIVE, ACTIVE));
        open.setOnClickListener(ignored -> host.openLink(link.url));
        actions.addView(open, detailActionParams(false));

        ImageButton copy = detailIconButton(R.drawable.ic_copy, "Copy link");
        copy.setOnClickListener(ignored -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager)
                            activity.getSystemService(Activity.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                    "Link", link.url));
            Toast.makeText(activity, "Link copied", Toast.LENGTH_SHORT).show();
        });
        actions.addView(copy, detailActionParams(true));

        ImageButton share = detailIconButton(R.drawable.ic_share, "Share link");
        share.setOnClickListener(ignored -> TextShare.showChooser(
                activity,
                link.displayText.isEmpty() ? "Link" : link.displayText,
                "Link",
                link.url,
                link.url
        ));
        actions.addView(share, detailActionParams(true));

        ImageButton previewAction = detailIconButton(
                R.drawable.ic_refresh,
                linkBitmap == null ? "Add link preview" : "Refresh link preview"
        );
        previewAction.setOnClickListener(ignored ->
                regenerateLinkPreview(link, detail, preview, previewAction));
        actions.addView(previewAction, detailActionParams(true));

        ImageButton delete = detailIconButton(R.drawable.ic_delete, "Delete link");
        delete.setOnClickListener(ignored -> confirmDeleteLink(link, detail));
        actions.addView(delete, detailActionParams(true));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(actions, actionsParams);

        detail.setContentView(content);
        Window window = detail.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        detail.show();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
        }
    }

    private void confirmDeleteLink(ScrapedLinkStore.Entry link, Dialog detail) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete link?")
                .setMessage("This removes the saved link from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (confirmation, which) -> {
                    scrapedLinkStore.delete(link.id);
                    detail.dismiss();
                    refresh();
                    Toast.makeText(activity, "Link deleted", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void regenerateLinkPreview(
            ScrapedLinkStore.Entry entry,
            Dialog detail,
            ImageView preview,
            ImageButton action
    ) {
        action.setEnabled(false);
        executor.execute(() -> {
            try {
                byte[] image = OpenGraphPreview.fetch(entry.url);
                if (image == null) {
                    // YouTube watch links serve no cookie-free og:image; use the
                    // canonical video thumbnail instead.
                    image = SavedThumbnail.fetch(entry.url);
                }
                if (image == null || !scrapedLinkStore.updatePreview(entry.id, image)) {
                    throw new IllegalStateException("Link preview is unavailable");
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                if (bitmap == null) {
                    throw new IllegalStateException("Link preview is unavailable");
                }
                activity.runOnUiThread(() -> {
                    if (!detail.isShowing()) {
                        return;
                    }
                    preview.setImageBitmap(bitmap);
                    preview.setVisibility(View.VISIBLE);
                    action.setContentDescription("Refresh link preview");
                    action.setEnabled(true);
                    refresh();
                    Toast.makeText(activity, "Link preview updated", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!detail.isShowing()) {
                        return;
                    }
                    action.setContentDescription("Add link preview");
                    action.setEnabled(true);
                    Toast.makeText(
                            activity,
                            "Link preview could not be updated",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void regenerateThumbnail(
            SavedSummaryStore.Entry entry,
            Dialog detail,
            ImageView preview,
            ImageButton action
    ) {
        action.setEnabled(false);
        executor.execute(() -> {
            try {
                byte[] thumbnail = SavedThumbnail.fetch(entry.sourceUrl);
                if (thumbnail == null || !store.updateThumbnail(entry.id, thumbnail)) {
                    throw new IllegalStateException("Video thumbnail is unavailable");
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
                if (bitmap == null) {
                    throw new IllegalStateException("Video thumbnail is unavailable");
                }
                activity.runOnUiThread(() -> {
                    if (!detail.isShowing()) {
                        return;
                    }
                    preview.setImageBitmap(bitmap);
                    preview.setVisibility(View.VISIBLE);
                    action.setContentDescription("Refresh video thumbnail");
                    action.setEnabled(true);
                    refresh();
                    Toast.makeText(activity, "Thumbnail updated", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!detail.isShowing()) {
                        return;
                    }
                    action.setEnabled(true);
                    Toast.makeText(
                            activity,
                            "Thumbnail could not be updated",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }
    private Bitmap decodePreview(byte[] preview) {
        return preview == null
                ? null
                : BitmapFactory.decodeByteArray(preview, 0, preview.length);
    }

    private Bitmap decodeThumbnail(byte[] thumbnail) {
        return !thumbnailsEnabled || thumbnail == null
                ? null
                : BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
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
    private LinearLayout.LayoutParams detailActionParams(boolean hasLeadingGap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        if (hasLeadingGap) {
            params.setMarginStart(dp(4));
        }
        return params;
    }
    private ImageButton detailIconButton(int icon, String description) {
        ImageButton action = new ImageButton(activity);
        action.setImageResource(icon);
        action.setContentDescription(description);
        action.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        action.setPadding(dp(10), dp(10), dp(10), dp(10));
        action.setBackground(panelBackground(BUTTON, BUTTON));
        return action;
    }

    private LinearLayout.LayoutParams findArrowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.setMarginStart(dp(4));
        return params;
    }

    private void hideKeyboard(EditText input) {
        InputMethodManager keyboard = (InputMethodManager)
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void toggleFindBar(
            LinearLayout findBar,
            EditText findInput,
            ImageView thumbnailPreview
    ) {
        boolean showing = findBar.getVisibility() == View.VISIBLE;
        findBar.setVisibility(showing ? View.GONE : View.VISIBLE);
        // The keyboard plus the thumbnail crowd out the summary text, so the
        // thumbnail hides while search is open and returns when it closes.
        thumbnailPreview.setVisibility(
                !showing || thumbnailPreview.getDrawable() == null
                        ? View.GONE
                        : View.VISIBLE
        );
        InputMethodManager keyboard = (InputMethodManager)
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (!showing) {
            findInput.requestFocus();
            keyboard.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            keyboard.hideSoftInputFromWindow(findInput.getWindowToken(), 0);
        }
    }

    /**
     * Browser-style find over the rendered summary: highlights every case-insensitive
     * match, reports "current of total", and jumps between matches with wrap-around.
     */
    private final class SummaryFinder {
        private static final int MATCH_COLOR = Color.rgb(255, 213, 0);
        private static final int CURRENT_COLOR = Color.rgb(255, 112, 0);

        private final TextView summaryView;
        private final ScrollView scroller;
        private final TextView countView;
        private final List<int[]> ranges = new ArrayList<>();
        private final List<BackgroundColorSpan> spans = new ArrayList<>();
        private int index = -1;

        SummaryFinder(TextView summaryView, ScrollView scroller, TextView countView) {
            this.summaryView = summaryView;
            this.scroller = scroller;
            this.countView = countView;
        }

        void update(String query) {
            Spannable content = (Spannable) summaryView.getText();
            clearHighlights(content);
            ranges.clear();
            index = -1;
            if (query.isEmpty()) {
                countView.setText("");
                return;
            }
            String haystack = content.toString().toLowerCase(Locale.US);
            String needle = query.toLowerCase(Locale.US);
            int at = haystack.indexOf(needle);
            while (at >= 0) {
                ranges.add(new int[]{at, at + needle.length()});
                at = haystack.indexOf(needle, at + needle.length());
            }
            if (ranges.isEmpty()) {
                countView.setText("0 of 0");
                return;
            }
            for (int[] range : ranges) {
                applySpan(content, range, MATCH_COLOR);
            }
            jump(0);
        }

        void go(int delta) {
            if (ranges.isEmpty()) {
                return;
            }
            jump(Math.floorMod(index + delta, ranges.size()));
        }

        void clear() {
            clearHighlights((Spannable) summaryView.getText());
            ranges.clear();
            index = -1;
            countView.setText("");
        }

        private void jump(int target) {
            index = target;
            countView.setText((index + 1) + " of " + ranges.size());
            Spannable content = (Spannable) summaryView.getText();
            for (int position = 0; position < ranges.size(); position++) {
                content.removeSpan(spans.get(position));
                BackgroundColorSpan span = new BackgroundColorSpan(
                        position == index ? CURRENT_COLOR : MATCH_COLOR
                );
                spans.set(position, span);
                content.setSpan(
                        span,
                        ranges.get(position)[0],
                        ranges.get(position)[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            summaryView.post(() -> {
                Layout layout = summaryView.getLayout();
                if (layout == null) {
                    return;
                }
                int line = layout.getLineForOffset(ranges.get(index)[0]);
                int y = Math.max(0, layout.getLineTop(line) - dp(12));
                scroller.smoothScrollTo(0, y);
            });
        }

        private void applySpan(Spannable content, int[] range, int color) {
            BackgroundColorSpan span = new BackgroundColorSpan(color);
            spans.add(span);
            content.setSpan(span, range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        private void clearHighlights(Spannable content) {
            for (BackgroundColorSpan span : spans) {
                content.removeSpan(span);
            }
            spans.clear();
        }
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
        private final List<SavedItem> all = new ArrayList<>();
        private final List<SavedItem> visible = new ArrayList<>();

        void setItems(List<SavedItem> items) {
            all.clear();
            all.addAll(items);
            filter(search == null ? "" : search.getText().toString());
        }

        void filter(String query) {
            visible.clear();
            String normalizedQuery = query == null
                    ? "" : query.trim().toLowerCase(Locale.ROOT);
            for (SavedItem item : all) {
                if (!passesTypeFilter(item)) {
                    continue;
                }
                if (item.isLink) {
                    if (matchesLink(item.link, normalizedQuery)) {
                        visible.add(item);
                    }
                } else if (SavedListOrder.matchesEntry(item.saved, query, selectedCreator)) {
                    visible.add(item);
                }
            }
            visible.sort((left, right) -> {
                int dateOrder = sortDescending
                        ? Long.compare(right.datedAt(), left.datedAt())
                        : Long.compare(left.datedAt(), right.datedAt());
                if (dateOrder != 0) {
                    return dateOrder;
                }
                return sortDescending
                        ? Long.compare(right.sortId(), left.sortId())
                        : Long.compare(left.sortId(), right.sortId());
            });
            notifyDataSetChanged();
        }

        private boolean passesTypeFilter(SavedItem item) {
            if (typeFilter == TYPE_VIDEOS) {
                return !item.isLink;
            }
            if (typeFilter == TYPE_LINKS) {
                return item.isLink;
            }
            return true;
        }

        private boolean matchesLink(ScrapedLinkStore.Entry link, String normalizedQuery) {
            if (selectedCreator != null) {
                boolean creatorMatches = selectedCreator.isEmpty()
                        ? link.posterName.isEmpty()
                        : selectedCreator.equalsIgnoreCase(link.posterName);
                if (!creatorMatches) {
                    return false;
                }
            }
            if (normalizedQuery.isEmpty()) {
                return true;
            }
            return link.displayText.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || link.url.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || link.posterName.toLowerCase(Locale.ROOT).contains(normalizedQuery);
        }

        int getTotalCount() {
            return all.size();
        }

        List<SavedListOrder.CreatorCount> creatorCounts() {
            java.util.TreeMap<String, Integer> known = new java.util.TreeMap<>(
                    String.CASE_INSENSITIVE_ORDER
            );
            int unknown = 0;
            for (SavedItem item : all) {
                String creator = item.isLink ? item.link.posterName : item.saved.channelName;
                if (creator.isEmpty()) {
                    unknown++;
                } else {
                    known.merge(creator, 1, Integer::sum);
                }
            }
            List<SavedListOrder.CreatorCount> counts =
                    new ArrayList<>(known.size() + (unknown == 0 ? 0 : 1));
            for (java.util.Map.Entry<String, Integer> creator : known.entrySet()) {
                counts.add(new SavedListOrder.CreatorCount(
                        creator.getKey(), creator.getValue()));
            }
            if (unknown > 0) {
                counts.add(new SavedListOrder.CreatorCount("", unknown));
            }
            return counts;
        }

        boolean hasCreator(String creator) {
            for (SavedItem item : all) {
                String name = item.isLink ? item.link.posterName : item.saved.channelName;
                if (creator.isEmpty() ? name.isEmpty() : creator.equalsIgnoreCase(name)) {
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
        public SavedItem getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).sortId();
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            LinearLayout divider;
            TextView dividerLabel;
            LinearLayout item;
            ImageView thumbnail;
            LinearLayout copy;
            TextView title;
            TextView metadata;
            TextView excerpt;
            if (convertView instanceof LinearLayout existing) {
                row = existing;
                divider = (LinearLayout) row.getChildAt(0);
                dividerLabel = (TextView) divider.getChildAt(1);
                item = (LinearLayout) row.getChildAt(1);
                thumbnail = (ImageView) item.getChildAt(0);
                copy = (LinearLayout) item.getChildAt(1);
                title = (TextView) copy.getChildAt(0);
                metadata = (TextView) copy.getChildAt(1);
                excerpt = (TextView) copy.getChildAt(2);
            } else {
                row = verticalLayout();
                divider = groupDivider();
                dividerLabel = (TextView) divider.getChildAt(1);
                row.addView(divider);

                item = horizontalLayout();
                item.setGravity(Gravity.TOP);
                item.setPadding(dp(10), dp(8), dp(10), dp(10));
                thumbnail = new ImageView(activity);
                thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbnail.setBackground(panelBackground(Color.BLACK, Color.rgb(70, 70, 70)));
                thumbnail.setClipToOutline(true);
                thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(thumbnail, new LinearLayout.LayoutParams(dp(112), dp(63)));

                copy = verticalLayout();
                title = text("", 15, Color.WHITE);
                title.setTypeface(title.getTypeface(), Typeface.BOLD);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(title);
                metadata = text("", 12, MUTED);
                LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                metadataParams.setMargins(0, dp(2), 0, dp(5));
                copy.addView(metadata, metadataParams);
                excerpt = text("", 13, Color.rgb(225, 225, 225));
                excerpt.setMaxLines(3);
                excerpt.setEllipsize(TextUtils.TruncateAt.END);
                excerpt.setLineSpacing(0, 1.1f);
                copy.addView(excerpt);
                item.addView(copy, new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                ));
                row.addView(item, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            SavedItem rowItem = getItem(position);
            boolean startsGroup =
                    position == 0 || !sameGroup(rowItem, getItem(position - 1));
            divider.setVisibility(startsGroup ? View.VISIBLE : View.GONE);
            if (startsGroup) {
                dividerLabel.setText(groupLabel(rowItem));
            }
            Bitmap bitmap = rowItem.isLink
                    ? decodePreview(rowItem.link.preview)
                    : decodeThumbnail(rowItem.saved.thumbnail);
            LinearLayout.LayoutParams copyParams =
                    (LinearLayout.LayoutParams) copy.getLayoutParams();
            if (bitmap == null) {
                thumbnail.setImageBitmap(null);
                thumbnail.setVisibility(View.GONE);
                copyParams.setMarginStart(0);
            } else {
                thumbnail.setImageBitmap(bitmap);
                thumbnail.setVisibility(View.VISIBLE);
                copyParams.setMarginStart(dp(8));
            }
            copy.setLayoutParams(copyParams);
            if (rowItem.isLink) {
                title.setText(rowItem.link.displayText.isEmpty()
                        ? rowItem.link.url : rowItem.link.displayText);
                metadata.setText(listLinkMetadata(rowItem.link));
                excerpt.setText(rowItem.link.url);
            } else {
                title.setText(rowItem.saved.videoTitle);
                metadata.setText(listMetadata(rowItem.saved));
                excerpt.setText(preview(rowItem.saved.summaryText));
            }
            row.setBackgroundColor(BACKGROUND);
            return row;
        }

        private boolean sameGroup(SavedItem left, SavedItem right) {
            Calendar leftDate = Calendar.getInstance();
            leftDate.setTimeInMillis(left.datedAt());
            Calendar rightDate = Calendar.getInstance();
            rightDate.setTimeInMillis(right.datedAt());
            return leftDate.get(Calendar.ERA) == rightDate.get(Calendar.ERA)
                    && leftDate.get(Calendar.YEAR) == rightDate.get(Calendar.YEAR)
                    && leftDate.get(Calendar.DAY_OF_YEAR) == rightDate.get(Calendar.DAY_OF_YEAR);
        }

        private String groupLabel(SavedItem item) {
            return SavedListOrder.dayLabel(
                    item.datedAt(),
                    System.currentTimeMillis(),
                    Locale.getDefault(),
                    TimeZone.getDefault()
            );
        }

        private String listMetadata(SavedSummaryStore.Entry entry) {
            String channel = entry.channelName.isEmpty() ? "" : " | " + entry.channelName;
            return entry.summaryLabel + channel + " | " + formatTime(entry.createdAt);
        }

        private String listLinkMetadata(ScrapedLinkStore.Entry link) {
            String poster = link.posterName.isEmpty() ? "" : " | " + link.posterName;
            return "Link" + poster + " | " + formatTime(link.datedAt());
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
