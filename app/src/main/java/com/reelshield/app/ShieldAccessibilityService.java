package com.reelshield.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.Locale;

/**
 * ReelShield v1.7 — smooth multi-app short-form blocker.
 *
 * Fast navigation clicks block short-form destinations immediately. Tree scans are
 * debounced and capped. Instagram DM Reels get a dedicated session so the block is
 * not cleared merely because Instagram exposes the underlying Messages tree.
 */
public class ShieldAccessibilityService extends AccessibilityService {
    private static final String INSTAGRAM = "com.instagram.android";
    private static final String YOUTUBE = "com.google.android.youtube";
    private static final String SNAPCHAT = "com.snapchat.android";
    private static final String FACEBOOK = "com.facebook.katana";
    private static final String FACEBOOK_LITE = "com.facebook.lite";

    private enum App { NONE, INSTAGRAM, YOUTUBE, SNAPCHAT, FACEBOOK }
    private enum Destination { UNKNOWN, HOME, REELS, CHAT_REEL, MESSAGES, OTHER, SHORT_FORM }

    private static final long SCAN_DEBOUNCE_MS = 140L;
    private static final long HOME_SCAN_DELAY_MS = 90L;
    private static final long HOME_SKIP_RECHECK_MS = 110L;

    private WindowManager windowManager;
    private View blockView;
    private App app = App.NONE;
    private Destination destination = Destination.UNKNOWN;
    private long lastScanAt;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable scheduledScan = new Runnable() {
        @Override public void run() { evaluateActiveWindow(); }
    };

    private final Runnable scheduledHomeScan = new Runnable() {
        @Override public void run() {
            if (app == App.INSTAGRAM && destination == Destination.HOME && blockView == null) {
                evaluateActiveWindow();
            }
        }
    };

    private final Runnable scheduledHomeSkip = new Runnable() {
        @Override public void run() {
            if (app == App.INSTAGRAM && destination == Destination.HOME && blockView == null) {
                skipInlineHomeReel();
            }
        }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 25;
        setServiceInfo(info);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        resetState();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        App incoming = appForPackage(pkg);
        if (incoming == App.NONE) {
            resetState();
            return;
        }
        if (incoming != app) {
            app = incoming;
            destination = Destination.UNKNOWN;
            cancelScans();
            removeBlock();
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Destination clicked = classifyNavigationClick(event);
            if (clicked != Destination.UNKNOWN) {
                destination = clicked;
                cancelScans();

                if (clicked == Destination.REELS || clicked == Destination.CHAT_REEL
                        || clicked == Destination.SHORT_FORM) {
                    showBlock(blockMessage());
                    return;
                }

                removeBlock();
                if (clicked == Destination.HOME) {
                    handler.postDelayed(scheduledHomeScan, HOME_SCAN_DELAY_MS);
                }
                return;
            }
        }

        if (blockView != null) {
            int type = event.getEventType();

            // An Instagram DM Reel remains protected even when Instagram keeps the
            // Messages tree underneath it. Only actual window transitions cause a
            // reevaluation, so scrolling inside the Reel cannot bypass the block.
            if (app == App.INSTAGRAM && destination == Destination.CHAT_REEL) {
                if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                    scheduleScan(90L);
                }
                return;
            }

            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                scheduleScan(90L);
            }
            return;
        }

        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            scheduleScan(SCAN_DEBOUNCE_MS);
        }
    }

    private App appForPackage(String pkg) {
        if (INSTAGRAM.equals(pkg)) return App.INSTAGRAM;
        if (YOUTUBE.equals(pkg)) return App.YOUTUBE;
        if (SNAPCHAT.equals(pkg)) return App.SNAPCHAT;
        if (FACEBOOK.equals(pkg) || FACEBOOK_LITE.equals(pkg)) return App.FACEBOOK;
        return App.NONE;
    }

    private String blockMessage() {
        switch (app) {
            case INSTAGRAM: return "ReelShield\nInstagram Reel blocked";
            case YOUTUBE: return "ReelShield\nYouTube Short blocked";
            case SNAPCHAT: return "ReelShield\nSnapchat Spotlight blocked";
            case FACEBOOK: return "ReelShield\nFacebook Reel blocked";
            default: return "ReelShield\nShort-form content blocked";
        }
    }

    private void scheduleScan(long delayMs) {
        handler.removeCallbacks(scheduledScan);
        handler.postDelayed(scheduledScan, delayMs);
    }

    private void cancelScans() {
        handler.removeCallbacks(scheduledScan);
        handler.removeCallbacks(scheduledHomeScan);
        handler.removeCallbacks(scheduledHomeSkip);
    }

    private Destination classifyNavigationClick(AccessibilityEvent event) {
        String s = eventStrings(event);
        if (s.isEmpty()) return Destination.UNKNOWN;

        switch (app) {
            case INSTAGRAM:
                // Check DM Reel clicks before generic Messages classification.
                if (isReelsLabel(s)) return Destination.REELS;
                if (isChatReelClick(s)) return Destination.CHAT_REEL;
                if (containsAny(s, "message", "messenger", "inbox", "direct", "chats")) return Destination.MESSAGES;
                if (isHomeLabel(s)) return Destination.HOME;
                if (containsAny(s, "search", "explore", "profile", "notification", "activity")) return Destination.OTHER;
                break;

            case YOUTUBE:
                if (containsAny(s, "shorts tab", "shorts selected", "shorts navigation")
                        || exactOrStarts(s, "shorts")) return Destination.SHORT_FORM;
                if (containsAny(s, "home", "subscriptions", "library", "you", "search", "channel")) return Destination.OTHER;
                break;

            case SNAPCHAT:
                if (containsAny(s, "spotlight selected", "spotlight tab", "spotlight navigation")
                        || exactOrStarts(s, "spotlight")) return Destination.SHORT_FORM;
                if (containsAny(s, "chat", "camera", "map", "stories", "discover", "profile")) return Destination.OTHER;
                break;

            case FACEBOOK:
                if (containsAny(s, "reels tab", "reels selected", "reels navigation")
                        || exactOrStarts(s, "reels")) return Destination.SHORT_FORM;
                if (containsAny(s, "home", "friends", "groups", "marketplace", "notifications", "menu", "profile", "search", "messenger")) return Destination.OTHER;
                break;

            default:
                break;
        }
        return Destination.UNKNOWN;
    }

    private boolean exactOrStarts(String s, String word) {
        return s.equals(word) || s.startsWith(word + " ")
                || s.startsWith(word + ",") || s.startsWith(word + " selected");
    }

    private String eventStrings(AccessibilityEvent event) {
        StringBuilder sb = new StringBuilder(180);
        if (event.getContentDescription() != null) sb.append(event.getContentDescription()).append(' ');
        if (event.getText() != null) {
            for (CharSequence c : event.getText()) if (c != null) sb.append(c).append(' ');
        }
        AccessibilityNodeInfo src = event.getSource();
        if (src != null) {
            try {
                appendNodeStrings(src, sb);
                AccessibilityNodeInfo parent = src.getParent();
                if (parent != null) {
                    try { appendNodeStrings(parent, sb); } finally { parent.recycle(); }
                }
            } finally {
                src.recycle();
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT).trim();
    }

    private void appendNodeStrings(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) sb.append(node.getText()).append(' ');
        if (node.getContentDescription() != null) sb.append(node.getContentDescription()).append(' ');
        String id = node.getViewIdResourceName();
        if (id != null) sb.append(id).append(' ');
    }

    private boolean isReelsLabel(String s) {
        return s.equals("reels") || s.startsWith("reels ") || s.contains("reels tab")
                || s.contains("reels selected") || s.contains("reels navigation");
    }

    private boolean isChatReelClick(String s) {
        if (!containsAny(s, "reel", "reels")) return false;
        return containsAny(s, "sent a reel", "shared a reel", "share a reel",
                "sent reel", "shared reel", "reel video", "reel from", "reel to",
                "view reel", "watch reel", "open reel", "reels video", "play reel");
    }

    private boolean isHomeLabel(String s) {
        return s.equals("home") || s.startsWith("home ") || s.contains("home tab")
                || s.contains("home selected") || s.contains("home navigation");
    }

    private boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private void evaluateActiveWindow() {
        long now = SystemClock.uptimeMillis();
        if (now - lastScanAt < 80L) return;
        lastScanAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
            App rootApp = appForPackage(pkg);
            if (rootApp == App.NONE) {
                resetState();
                return;
            }
            if (rootApp != app) {
                app = rootApp;
                destination = Destination.UNKNOWN;
                removeBlock();
            }

            Signals sig = new Signals();
            inspect(root, sig, new ScanBudget(1400));

            if (app == App.INSTAGRAM) {
                // Critical DM fix: if the current tree looks like Messages AND the
                // strong Reel viewer signature is present, treat it as the Reel viewer,
                // not as a safe Messages screen. This also catches cases where the
                // click event itself exposed no useful "Reel" text.
                boolean dmReelViewer = sig.reelViewerSignature()
                        && sig.isMessagingScreen()
                        && !sig.selectedHome;
                boolean activeChatSession = sig.reelViewerSignature()
                        && destination == Destination.CHAT_REEL;

                if (dmReelViewer || activeChatSession) {
                    destination = Destination.CHAT_REEL;
                    showBlock(blockMessage());
                    return;
                }

                // If we are already in a protected chat-Reel session but the viewer
                // disappeared, allow the normal Messages tree to clear the block.
                if (destination == Destination.CHAT_REEL) {
                    if (sig.isMessagingScreen() && !sig.reelViewerSignature()) {
                        destination = Destination.MESSAGES;
                        removeBlock();
                        return;
                    }
                    if (sig.reelViewerSignature()) {
                        showBlock(blockMessage());
                        return;
                    }
                }

                if (sig.selectedMessages || sig.isMessagingScreen()) {
                    destination = Destination.MESSAGES;
                } else if (sig.selectedReels) {
                    destination = Destination.REELS;
                } else if (sig.selectedHome) {
                    destination = Destination.HOME;
                }

                if (destination == Destination.MESSAGES || destination == Destination.OTHER) {
                    removeBlock();
                    return;
                }
                if (destination == Destination.REELS) {
                    showBlock(blockMessage());
                    return;
                }
                if (destination == Destination.HOME || destination == Destination.UNKNOWN) {
                    if (sig.inlineHomeReelSignature()) {
                        destination = Destination.HOME;
                        removeBlock();
                        scheduleHomeSkip();
                    } else if (destination == Destination.HOME) {
                        removeBlock();
                    }
                }
                return;
            }

            // Other supported apps retain the smooth v1.5 architecture.
            if (sig.selectedShortForm) {
                destination = Destination.SHORT_FORM;
            } else if (sig.selectedSafe) {
                destination = Destination.OTHER;
            }

            if (destination == Destination.SHORT_FORM) {
                if (shortFormConfirmed(sig)) showBlock(blockMessage());
                return;
            }
            if (destination == Destination.OTHER) {
                removeBlock();
                return;
            }

            if (inlineShortForm(sig)) showBlock(blockMessage());
        } finally {
            root.recycle();
        }
    }

    private void scheduleHomeSkip() {
        handler.removeCallbacks(scheduledHomeSkip);
        handler.postDelayed(scheduledHomeSkip, HOME_SKIP_RECHECK_MS);
    }

    private void skipInlineHomeReel() {
        if (app != App.INSTAGRAM || destination != Destination.HOME || blockView != null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            Signals sig = new Signals();
            inspect(root, sig, new ScanBudget(1400));
            if (!sig.inlineHomeReelSignature()) return;

            AccessibilityNodeInfo target = findHomeScrollTarget(root);
            if (target != null) {
                try {
                    target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                } finally {
                    target.recycle();
                }
                scheduleHomeSkip();
            }
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findHomeScrollTarget(AccessibilityNodeInfo root) {
        ScrollCandidate best = new ScrollCandidate();
        collectScrollCandidate(root, best);
        return best.node == null ? null : best.node.obtain();
    }

    private void collectScrollCandidate(AccessibilityNodeInfo node, ScrollCandidate best) {
        if (node == null) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        String cls = lower(node.getClassName());

        if (node.isScrollable() && r.width() >= 700 && r.height() >= 650
                && !cls.endsWith("seekbar")) {
            long area = (long) r.width() * (long) r.height();
            boolean vertical = r.height() >= r.width() * 0.75f;
            long score = area + (vertical ? area / 2 : 0);
            if (score > best.score) {
                best.score = score;
                best.node = node;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectScrollCandidate(child, best);
                child.recycle();
            }
        }
    }

    private static class ScrollCandidate {
        AccessibilityNodeInfo node;
        long score;
    }

    private void inspect(AccessibilityNodeInfo node, Signals s, ScanBudget budget) {
        if (node == null || budget.exhausted()) return;
        budget.count++;

        String text = lower(node.getText());
        String desc = lower(node.getContentDescription());
        String cls = lower(node.getClassName());
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        String both = text + " " + desc;

        if (node.isSelected()) {
            if (app == App.INSTAGRAM && (text.equals("reels") || desc.equals("reels"))) s.selectedReels = true;
            if (app == App.INSTAGRAM && (text.equals("home") || desc.equals("home"))) s.selectedHome = true;
            if (app == App.INSTAGRAM && (text.contains("message") || desc.contains("message")
                    || text.contains("inbox") || desc.contains("inbox")
                    || text.equals("chats") || desc.equals("chats"))) s.selectedMessages = true;
            if (app == App.YOUTUBE && (text.equals("shorts") || desc.equals("shorts"))) s.selectedShortForm = true;
            if (app == App.SNAPCHAT && (text.equals("spotlight") || desc.equals("spotlight"))) s.selectedShortForm = true;
            if (app == App.FACEBOOK && (text.equals("reels") || desc.equals("reels"))) s.selectedShortForm = true;
            if (isSafeSelected(text, desc)) s.selectedSafe = true;
        }

        if (app == App.INSTAGRAM && isMessageClue(text, desc)) s.messageClues++;
        if (cls.endsWith("viewpager") && node.isScrollable() && r.width() >= 650 && r.height() >= 950) s.largePager++;
        if ((cls.endsWith("framelayout") || cls.endsWith("surfaceview")
                || cls.endsWith("textureview") || cls.endsWith("playercontrolview"))
                && r.width() >= 600 && r.height() >= 650) s.largeMedia++;
        if (cls.endsWith("seekbar")) s.seekbar++;

        if (containsAny(both, "audio", "sound", "original audio")) s.originalAudio++;
        if (containsAny(both, "reels", "reel")) s.reelLabel++;
        if (containsAny(both, "shorts", "short")) s.shortLabel++;
        if (containsAny(both, "short video", "shorts video", "spotlight")) s.shortPhrase++;

        if (containsAction(text, desc, "like")) s.like++;
        if (containsAction(text, desc, "comment")) s.comment++;
        if (containsAction(text, desc, "share")) s.share++;
        if (containsAction(text, desc, "repost")) s.repost++;
        if (containsAction(text, desc, "save")) s.save++;
        if (containsAction(text, desc, "more")) s.more++;
        if (desc.contains("liked this reel") || text.contains("liked this reel")) s.reelPhrase++;
        if (desc.contains("original audio") || text.contains("original audio")) s.originalAudio++;

        if (app == App.INSTAGRAM) {
            if (text.equals("messages") || desc.equals("messages") || text.equals("chats") || desc.equals("chats")
                    || text.equals("inbox") || desc.equals("inbox")) s.messagesTitle++;
            if (text.equals("message…") || text.equals("message...") || desc.equals("message…") || desc.equals("message...")) s.messageComposer++;
            if (desc.contains("voice message") || text.contains("voice message")) s.voiceMessage++;
            if (desc.equals("gallery") || text.equals("gallery")) s.gallery++;
            if (desc.contains("audio call") || text.contains("audio call")) s.audioCall++;
            if (desc.contains("video call") || text.contains("video call")) s.videoCall++;
            if (text.equals("active now") || desc.equals("active now")) s.activeNow++;
            if (text.contains("new message") || desc.contains("new message")) s.newMessage++;
        }

        for (int i = 0; i < node.getChildCount() && !budget.exhausted(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                inspect(child, s, budget);
                child.recycle();
            }
        }
    }

    private boolean isSafeSelected(String text, String desc) {
        String x = text + " " + desc;
        switch (app) {
            case INSTAGRAM: return containsAny(x, "home selected", "messages selected", "search selected", "profile selected", "notifications selected");
            case YOUTUBE: return containsAny(x, "home selected", "subscriptions selected", "library selected", "you selected");
            case SNAPCHAT: return containsAny(x, "chat selected", "camera selected", "stories selected", "map selected");
            case FACEBOOK: return containsAny(x, "home selected", "friends selected", "groups selected", "marketplace selected", "notifications selected", "menu selected");
            default: return false;
        }
    }

    private boolean isMessageClue(String text, String desc) {
        String x = text + " " + desc;
        return containsAny(x, "messages", "message…", "message...", "voice message", "gallery",
                "audio call", "video call", "active now", "new message", "chats", "inbox");
    }

    private boolean containsAction(String text, String desc, String key) {
        return text.equals(key) || desc.equals(key) || desc.contains(key + " number")
                || desc.contains("view " + key) || desc.startsWith(key + ",") || desc.startsWith(key + " ");
    }

    private String lower(CharSequence c) {
        return c == null ? "" : c.toString().toLowerCase(Locale.ROOT).trim();
    }

    private static class ScanBudget {
        int count;
        final int max;
        ScanBudget(int max) { this.max = max; }
        boolean exhausted() { return count >= max; }
    }

    private static class Signals {
        boolean selectedReels, selectedHome, selectedMessages, selectedShortForm, selectedSafe;
        int messageClues;
        int largePager, largeMedia, seekbar, originalAudio, reelLabel, shortLabel, shortPhrase;
        int like, comment, repost, share, save, more, reelPhrase;
        int messagesTitle, messageComposer, voiceMessage, gallery, audioCall, videoCall, activeNow, newMessage;

        boolean actionRail() {
            int n = 0;
            if (like > 0) n++;
            if (comment > 0) n++;
            if (repost > 0) n++;
            if (share > 0) n++;
            if (save > 0) n++;
            if (more > 0) n++;
            return n >= 4;
        }

        boolean mediaContainer() { return largePager > 0 || largeMedia > 0; }

        boolean isMessagingScreen() {
            if (selectedMessages || messageComposer > 0 || newMessage > 0) return true;
            int clues = 0;
            if (messagesTitle > 0) clues++;
            if (voiceMessage > 0) clues++;
            if (gallery > 0) clues++;
            if (audioCall > 0) clues++;
            if (videoCall > 0) clues++;
            if (activeNow > 0) clues++;
            return clues >= 2;
        }

        boolean reelViewerSignature() {
            return largePager > 0 && actionRail() && originalAudio > 0 && seekbar > 0;
        }

        boolean inlineHomeReelSignature() {
            boolean strongReelClue = reelPhrase > 0 || originalAudio > 0 || reelLabel > 0;
            return mediaContainer() && actionRail() && strongReelClue;
        }
    }

    private boolean shortFormConfirmed(Signals s) {
        if (s.selectedShortForm) return true;
        switch (app) {
            case YOUTUBE: return s.shortLabel > 0 && s.mediaContainer() && s.actionRail();
            case SNAPCHAT: return s.shortLabel > 0 && s.mediaContainer() && s.actionRail();
            case FACEBOOK: return s.reelLabel > 0 && s.mediaContainer() && (s.actionRail() || s.seekbar > 0);
            default: return false;
        }
    }

    private boolean inlineShortForm(Signals s) {
        if (!s.mediaContainer() || !s.actionRail()) return false;
        switch (app) {
            case YOUTUBE: return s.shortLabel > 0 && (s.seekbar > 0 || s.shortPhrase > 0);
            case SNAPCHAT: return s.shortLabel > 0 && (s.seekbar > 0 || s.shortPhrase > 0);
            case FACEBOOK: return s.reelLabel > 0 && (s.seekbar > 0 || s.originalAudio > 0 || s.reelPhrase > 0);
            default: return false;
        }
    }

    private void showBlock(String message) {
        if (windowManager == null || blockView != null) return;
        TextView v = new TextView(this);
        v.setText(message);
        v.setTextSize(24);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.BLACK);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                -3);
        lp.gravity = Gravity.CENTER;
        try {
            windowManager.addView(v, lp);
            blockView = v;
        } catch (Exception ignored) { }
    }

    private void removeBlock() {
        if (windowManager != null && blockView != null) {
            try { windowManager.removeView(blockView); } catch (Exception ignored) { }
        }
        blockView = null;
    }

    private void resetState() {
        cancelScans();
        removeBlock();
        app = App.NONE;
        destination = Destination.UNKNOWN;
    }

    @Override public void onInterrupt() { resetState(); }

    @Override public void onDestroy() {
        resetState();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
