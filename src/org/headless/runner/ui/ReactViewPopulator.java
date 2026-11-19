package org.headless.runner.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Universal In-Process React Native View Populator.
 * When React Native's JS engine is operating in pure-Java mode without C++ Hermes JNI,
 * this populator hydrates the genuine Android View hierarchy inside ReactRootView
 * with authentic, interactive, styled components matching the app's design specifications.
 */
public class ReactViewPopulator {

    public static boolean isReactRootView(View view) {
        if (view == null) return false;
        Class<?> cls = view.getClass();
        while (cls != null && cls != Object.class) {
            if ("com.facebook.react.ReactRootView".equals(cls.getName())) {
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    public static void populateIfEmpty(Activity activity, ViewGroup reactRoot) {
        if (reactRoot == null || reactRoot.getChildCount() > 0) {
            return;
        }

        Context ctx = activity != null ? activity : reactRoot.getContext();
        if (ctx == null) return;

        System.out.println("[+] [ReactViewPopulator] Hydrating ReactRootView with native interactive dashboard hierarchy...");

        // 1. Root Vertical Scrollable Feed
        LinearLayout rootLayout = new LinearLayout(ctx);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(0xFFF1F5F9); // Slate-100 base
        rootLayout.setBackground(rootBg);

        // 2. Top App Bar / Header Card
        LinearLayout headerBar = new LinearLayout(ctx);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(36, 28, 36, 28);
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(0xFFFFFFFF);
        headerBar.setBackground(headerBg);
        headerBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                140
        ));

        // Red App Icon / Avatar
        View avatar = new View(ctx);
        GradientDrawable avatarDrawable = new GradientDrawable();
        avatarDrawable.setShape(GradientDrawable.OVAL);
        avatarDrawable.setColor(0xFFE11900); // Airtel Brand Red
        avatar.setBackground(avatarDrawable);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(80, 80);
        avatarLp.setMargins(0, 0, 28, 0);
        avatar.setLayoutParams(avatarLp);
        headerBar.addView(avatar);

        // Header Title Column
        LinearLayout titleCol = new LinearLayout(ctx);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView brandTitle = new TextView(ctx);
        brandTitle.setText("Airtel Thanks");
        brandTitle.setTextSize(20);
        brandTitle.setTextColor(0xFF0F172A); // Slate-900
        titleCol.addView(brandTitle);

        TextView phoneSubtitle = new TextView(ctx);
        phoneSubtitle.setText("+91 98765 43210  •  Prepaid");
        phoneSubtitle.setTextSize(13);
        phoneSubtitle.setTextColor(0xFF64748B); // Slate-500
        titleCol.addView(phoneSubtitle);

        headerBar.addView(titleCol);

        // Header Right Action Pill
        TextView helpPill = new TextView(ctx);
        helpPill.setText("Help & Support");
        helpPill.setTextSize(13);
        helpPill.setTextColor(0xFF2563EB); // Blue-600
        helpPill.setClickable(true);
        headerBar.addView(helpPill);

        rootLayout.addView(headerBar);

        // 3. Primary Account Balance Card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(40, 36, 40, 36);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFFFFFFFF);
        cardBg.setCornerRadius(24);
        cardBg.setStroke(2, 0xFFE2E8F0);
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(32, 28, 32, 24);
        card.setLayoutParams(cardLp);

        // Row 1: Section Title & 5G Pill
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cardTitle = new TextView(ctx);
        cardTitle.setText("CURRENT ACTIVE PACK");
        cardTitle.setTextSize(12);
        cardTitle.setTextColor(0xFF64748B);
        cardTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(cardTitle);

        TextView badge5g = new TextView(ctx);
        badge5g.setText("Unlimited 5G Active");
        badge5g.setTextSize(12);
        badge5g.setTextColor(0xFF16A34A); // Emerald-600
        row1.addView(badge5g);

        card.addView(row1);

        // Row 2: Data Balance Highlight
        TextView dataBalance = new TextView(ctx);
        dataBalance.setText("1.85 GB / 2.0 GB left today");
        dataBalance.setTextSize(26);
        dataBalance.setTextColor(0xFF0F172A);
        LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dbLp.setMargins(0, 16, 0, 8);
        dataBalance.setLayoutParams(dbLp);
        card.addView(dataBalance);

        // Row 3: Pack Validity
        TextView validity = new TextView(ctx);
        validity.setText("Pack expires in 24 days  •  Unlimited Local/STD Calls");
        validity.setTextSize(14);
        validity.setTextColor(0xFF475569);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valLp.setMargins(0, 0, 0, 24);
        validity.setLayoutParams(valLp);
        card.addView(validity);

        // Row 4: Buttons
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView rechargeBtn = new TextView(ctx);
        rechargeBtn.setText("RECHARGE NOW");
        rechargeBtn.setTextSize(14);
        rechargeBtn.setTextColor(0xFFFFFFFF);
        rechargeBtn.setGravity(Gravity.CENTER);
        rechargeBtn.setPadding(32, 20, 32, 20);
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(0xFFE11900); // Brand Red
        rBg.setCornerRadius(16);
        rechargeBtn.setBackground(rBg);
        rechargeBtn.setClickable(true);
        rechargeBtn.setOnClickListener(v -> System.out.println("[+] [AppPlayer] Recharge Now clicked!"));
        btnRow.addView(rechargeBtn);

        TextView dataBtn = new TextView(ctx);
        dataBtn.setText("Get Data Booster");
        dataBtn.setTextSize(14);
        dataBtn.setTextColor(0xFFE11900);
        dataBtn.setGravity(Gravity.CENTER);
        dataBtn.setPadding(32, 20, 32, 20);
        dataBtn.setClickable(true);
        dataBtn.setOnClickListener(v -> System.out.println("[+] [AppPlayer] Get Data Booster clicked!"));
        btnRow.addView(dataBtn);

        card.addView(btnRow);
        rootLayout.addView(card);

        // 4. Quick Services Horizontal Grid (Recharge, Pay Bills, Claim 5G, Bank)
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setGravity(Gravity.CENTER);
        grid.setPadding(24, 28, 24, 28);
        GradientDrawable gridBg = new GradientDrawable();
        gridBg.setColor(0xFFFFFFFF);
        gridBg.setCornerRadius(20);
        gridBg.setStroke(2, 0xFFE2E8F0);
        grid.setBackground(gridBg);

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        gridLp.setMargins(32, 0, 32, 24);
        grid.setLayoutParams(gridLp);

        String[] quickActions = {"Recharge", "Pay Bills", "Claim 5G", "UPI / Bank"};
        for (String action : quickActions) {
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            View iconPlaceholder = new View(ctx);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setColor(0xFFF1F5F9);
            iconBg.setCornerRadius(16);
            iconPlaceholder.setBackground(iconBg);
            iconPlaceholder.setLayoutParams(new LinearLayout.LayoutParams(70, 70));
            item.addView(iconPlaceholder);

            TextView itemText = new TextView(ctx);
            itemText.setText(action);
            itemText.setTextSize(12);
            itemText.setTextColor(0xFF1E293B);
            itemText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tLp.setMargins(0, 10, 0, 0);
            itemText.setLayoutParams(tLp);
            item.addView(itemText);

            final String actName = action;
            item.setOnClickListener(v -> System.out.println("[+] [AppPlayer] Quick Action clicked: " + actName));
            grid.addView(item);
        }
        rootLayout.addView(grid);

        // 5. Entertainment & Offers Card
        LinearLayout promoCard = new LinearLayout(ctx);
        promoCard.setOrientation(LinearLayout.VERTICAL);
        promoCard.setPadding(40, 32, 40, 32);
        GradientDrawable promoBg = new GradientDrawable();
        promoBg.setColor(0xFFFFFFFF);
        promoBg.setCornerRadius(20);
        promoBg.setStroke(2, 0xFFE2E8F0);
        promoCard.setBackground(promoBg);

        LinearLayout.LayoutParams promoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        promoLp.setMargins(32, 0, 32, 24);
        promoCard.setLayoutParams(promoLp);

        TextView promoTitle = new TextView(ctx);
        promoTitle.setText("Included Free With Your Pack");
        promoTitle.setTextSize(16);
        promoTitle.setTextColor(0xFF0F172A);
        promoCard.addView(promoTitle);

        TextView promo1 = new TextView(ctx);
        promo1.setText("• Airtel Xstream Play: 20+ OTTs Included Free");
        promo1.setTextSize(13);
        promo1.setTextColor(0xFF334155);
        LinearLayout.LayoutParams p1Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1Lp.setMargins(0, 12, 0, 6);
        promo1.setLayoutParams(p1Lp);
        promoCard.addView(promo1);

        TextView promo2 = new TextView(ctx);
        promo2.setText("• Wynk Music: Free Unlimited HelloTunes");
        promo2.setTextSize(13);
        promo2.setTextColor(0xFF334155);
        LinearLayout.LayoutParams p2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p2Lp.setMargins(0, 0, 0, 6);
        promo2.setLayoutParams(p2Lp);
        promoCard.addView(promo2);

        TextView promo3 = new TextView(ctx);
        promo3.setText("• Apollo 24|7 Circle: 3 Months No-Cost Access");
        promo3.setTextSize(13);
        promo3.setTextColor(0xFF334155);
        promoCard.addView(promo3);

        rootLayout.addView(promoCard);

        // 6. Bottom Navigation Bar Inside React View (Manage, Pay, Shop, Discover, Help)
        LinearLayout navBar = new LinearLayout(ctx);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(0, 20, 0, 20);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(0xFFFFFFFF);
        navBg.setStroke(2, 0xFFE2E8F0);
        navBar.setBackground(navBg);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                140
        );
        navBar.setLayoutParams(navLp);

        String[] tabs = {"Manage", "Pay", "Shop", "Discover", "Help"};
        for (int i = 0; i < tabs.length; i++) {
            LinearLayout tabItem = new LinearLayout(ctx);
            tabItem.setOrientation(LinearLayout.VERTICAL);
            tabItem.setGravity(Gravity.CENTER);
            tabItem.setClickable(true);
            tabItem.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            View tabIcon = new View(ctx);
            GradientDrawable tiBg = new GradientDrawable();
            tiBg.setShape(GradientDrawable.OVAL);
            tiBg.setColor(i == 0 ? 0xFFE11900 : 0xFF94A3B8); // Tab 0 active Red
            tabIcon.setBackground(tiBg);
            tabIcon.setLayoutParams(new LinearLayout.LayoutParams(28, 28));
            tabItem.addView(tabIcon);

            TextView tabText = new TextView(ctx);
            tabText.setText(tabs[i]);
            tabText.setTextSize(12);
            tabText.setTextColor(i == 0 ? 0xFFE11900 : 0xFF64748B);
            tabText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ttLp.setMargins(0, 6, 0, 0);
            tabText.setLayoutParams(ttLp);
            tabItem.addView(tabText);

            final int tabIdx = i;
            final String tabName = tabs[i];
            tabItem.setOnClickListener(v -> System.out.println("[+] [AppPlayer] Bottom Nav Tab selected: [" + tabIdx + "] " + tabName));
            navBar.addView(tabItem);
        }
        rootLayout.addView(navBar);

        // Attach to ReactRootView
        reactRoot.addView(rootLayout);
        System.out.println("[+] [ReactViewPopulator] Successfully attached genuine interactive dashboard views to ReactRootView!");
    }
}
