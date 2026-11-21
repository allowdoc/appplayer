package org.headless.runner.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FRAMEWORK RESOURCE PACKAGE - the runner's framework-res.apk equivalent.
 *
 * On a real device, framework classes (com.android.internal.policy.DecorView,
 * PhoneWindow, AnimationUtils, ArrayAdapter...) resolve resources from the
 * system package (package 0x01): android:interpolator/*, android:anim/*,
 * android:animator/*, android:layout/*, android:color/*, config_* values...
 *
 * This runner ships those definitions as compiled-in AOSP resource XML text
 * and maps ids through the framework jar's own R classes (android.R.* and
 * com.android.internal.R.*) by reflection, so ids always match the framework
 * bytecode the app is linked against. Resource values are AOSP-faithful:
 * accelerate_cubic = accelerateInterpolator(factor 1.5), fast_out_slow_in =
 * pathInterpolator M0,0 C0.4,0 0.2,1 1,1, etc.
 *
 * App resources (package 0x7f) NEVER route here - those live in the APK's own
 * resources.arsc (ArscParser). This class is only consulted for the system
 * package, exactly like androidfw falls back from app assets to system assets.
 */
public final class FrameworkRes {

    /** id -> {type, name} for the system resource package (0x01). */
    private static volatile Map<Integer, String[]> sIdToEntry;
    /** framework attr name -> attr id (android.R.attr). */
    private static volatile Map<String, Integer> sAttrIds;
    /** framework id name -> view id (android.R.id + internal R.id). */
    private static volatile Map<String, Integer> sViewIds;
    /** generic type->(name->id) maps for reference resolution (@type/name). */
    private static volatile Map<String, Map<String, Integer>> sTypeMaps;

    private FrameworkRes() {}

    // ------------------------------------------------------------------
    // AOSP framework resource definitions (framework-res res/* XML text).
    // ------------------------------------------------------------------
    private static final String XMLNS =
            "xmlns:android=\"http://schemas.android.com/apk/res/android\"";

    private static final Map<String, String> DEFS = buildDefs();

    private static Map<String, String> buildDefs() {
        Map<String, String> d = new HashMap<>();
        // ---- interpolator/* (real AOSP values) ----
        d.put("interpolator:accelerate_cubic",
                "<accelerateInterpolator " + XMLNS + " android:factor=\"1.5\"/>");
        d.put("interpolator:accelerate_decelerate",
                "<accelerateDecelerateInterpolator " + XMLNS + "/>");
        d.put("interpolator:accelerate_quad",
                "<accelerateInterpolator " + XMLNS + " android:factor=\"1.0\"/>");
        d.put("interpolator:accelerate_quint",
                "<accelerateInterpolator " + XMLNS + " android:factor=\"2.0\"/>");
        d.put("interpolator:anticipate",
                "<anticipateInterpolator " + XMLNS + " android:extraTension=\"1.5\" android:tension=\"2.0\"/>");
        d.put("interpolator:anticipate_overshoot",
                "<anticipateOvershootInterpolator " + XMLNS + " android:extraTension=\"1.5\" android:tension=\"2.0\"/>");
        d.put("interpolator:bounce", "<bounceInterpolator " + XMLNS + "/>");
        d.put("interpolator:cycle", "<cycleInterpolator " + XMLNS + " android:cycles=\"1.0\"/>");
        d.put("interpolator:decelerate_cubic",
                "<decelerateInterpolator " + XMLNS + " android:factor=\"1.5\"/>");
        d.put("interpolator:decelerate_quad",
                "<decelerateInterpolator " + XMLNS + " android:factor=\"1.0\"/>");
        d.put("interpolator:decelerate_quint",
                "<decelerateInterpolator " + XMLNS + " android:factor=\"2.0\"/>");
        d.put("interpolator:fast_out_extra_slow_in",
                "<pathInterpolator " + XMLNS + " android:pathData=\"M 0,0 C 0.4,0 0.2,1 1,1\"/>");
        d.put("interpolator:fast_out_linear_in",
                "<pathInterpolator " + XMLNS + " android:pathData=\"M 0,0 L 1,1\"/>");
        d.put("interpolator:fast_out_slow_in",
                "<pathInterpolator " + XMLNS + " android:pathData=\"M 0,0 C 0.4,0 0.2,1 1,1\"/>");
        d.put("interpolator:linear", "<linearInterpolator " + XMLNS + "/>");
        d.put("interpolator:linear_out_slow_in",
                "<pathInterpolator " + XMLNS + " android:pathData=\"M 0,0 C 0,0 0.2,1 1,1\"/>");
        d.put("interpolator:overshoot",
                "<overshootInterpolator " + XMLNS + " android:tension=\"2.0\"/>");

        // ---- anim/* (activity/window transitions, ListView position anims) ----
        d.put("anim:fade_in",
                "<alpha " + XMLNS + " android:fromAlpha=\"0.0\" android:toAlpha=\"1.0\"/>");
        d.put("anim:fade_out",
                "<alpha " + XMLNS + " android:fromAlpha=\"1.0\" android:toAlpha=\"0.0\"/>");
        d.put("anim:slide_in_left",
                "<set " + XMLNS + "><translate android:fromXDelta=\"-100%p\" android:toXDelta=\"0\"/>"
                        + "<alpha android:fromAlpha=\"0.0\" android:toAlpha=\"1.0\"/></set>");
        d.put("anim:slide_out_right",
                "<set " + XMLNS + "><translate android:fromXDelta=\"0\" android:toXDelta=\"100%p\"/>"
                        + "<alpha android:fromAlpha=\"1.0\" android:toAlpha=\"0.0\"/></set>");
        d.put("anim:accelerate_decelerate_interpolator",
                "<accelerateDecelerateInterpolator " + XMLNS + "/>");
        d.put("anim:accelerate_interpolator", "<accelerateInterpolator " + XMLNS + " android:factor=\"1.0\"/>");
        d.put("anim:anticipate_interpolator",
                "<anticipateInterpolator " + XMLNS + " android:extraTension=\"1.5\" android:tension=\"2.0\"/>");
        d.put("anim:anticipate_overshoot_interpolator",
                "<anticipateOvershootInterpolator " + XMLNS + " android:extraTension=\"1.5\" android:tension=\"2.0\"/>");
        d.put("anim:bounce_interpolator", "<bounceInterpolator " + XMLNS + "/>");
        d.put("anim:cycle_interpolator", "<cycleInterpolator " + XMLNS + " android:cycles=\"1.0\"/>");
        d.put("anim:decelerate_interpolator", "<decelerateInterpolator " + XMLNS + " android:factor=\"1.0\"/>");
        d.put("anim:linear_interpolator", "<linearInterpolator " + XMLNS + "/>");
        d.put("anim:overshoot_interpolator", "<overshootInterpolator " + XMLNS + " android:tension=\"2.0\"/>");

        // ---- layout/* (decor + adapter row layouts, AOSP-faithful ids) ----
        String stub = "<ViewStub android:id=\"@+id/action_mode_bar_stub\""
                + " android:inflatedId=\"@+id/action_mode_bar\""
                + " android:layout=\"@layout/action_mode_bar\""
                + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\"/>";
        d.put("layout:screen_simple",
                "<LinearLayout " + XMLNS + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\" android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\">" + stub
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:fitSystemWindows=\"true\" android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_simple_overlay_action_mode",
                "<LinearLayout " + XMLNS + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\" android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\" android:overlayActionBar=\"true\">" + stub
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:fitSystemWindows=\"true\" android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_swipe_dismiss",
                "<LinearLayout " + XMLNS + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\" android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\" android:id=\"@+id/action_bar_root\">" + stub
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_title",
                "<LinearLayout " + XMLNS + " android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\">" + stub
                        + "<RelativeLayout android:id=\"@+id/title_container\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\">"
                        + "<TextView android:id=\"@android:id/title\""
                        + " android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\""
                        + " android:layout_alignParentLeft=\"true\" android:layout_centerVertical=\"true\"/>"
                        + "</RelativeLayout>"
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_custom_title",
                "<LinearLayout " + XMLNS + " android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\">" + stub
                        + "<FrameLayout android:id=\"@+id/custom_title_container\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\">"
                        + "</FrameLayout>"
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_title_icons",
                "<LinearLayout " + XMLNS + " android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\">" + stub
                        + "<RelativeLayout android:id=\"@+id/title_container\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\">"
                        + "<ImageView android:id=\"@android:id/icon\""
                        + " android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\""
                        + " android:layout_alignParentLeft=\"true\"/>"
                        + "<TextView android:id=\"@android:id/title\""
                        + " android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\""
                        + " android:layout_toRightOf=\"@android:id/icon\"/></RelativeLayout>"
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_progress",
                "<LinearLayout " + XMLNS + " android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\">" + stub
                        + "<ProgressBar android:id=\"@android:id/progress\""
                        + " android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"/>"
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        d.put("layout:screen_action_bar",
                "<LinearLayout " + XMLNS + " android:orientation=\"vertical\""
                        + " android:fitsSystemWindows=\"true\">" + stub
                        + "<ViewStub android:id=\"@+id/action_bar_stub\""
                        + " android:inflatedId=\"@+id/action_bar\""
                        + " android:layout=\"@layout/action_bar\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\"/>"
                        + "<FrameLayout android:id=\"@android:id/content\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/></LinearLayout>");
        // adapter rows (ArrayAdapter/Spinner/AlertDialog defaults)
        d.put("layout:simple_list_item_1",
                "<TextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:textAppearance=\"?android:attr/textAppearanceListItem\""
                        + " android:gravity=\"center_vertical\" android:minHeight=\"?android:attr/listPreferredItemHeight\""
                        + " android:paddingStart=\"16dp\" android:paddingEnd=\"16dp\"/>");
        d.put("layout:simple_list_item_2",
                "<TwoLineListItem " + XMLNS + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"wrap_content\" android:minHeight=\"?android:attr/listPreferredItemHeight\">"
                        + "<TextView android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:textAppearance=\"?android:attr/textAppearanceListItem\""
                        + " android:gravity=\"center_vertical\"/>"
                        + "<TextView android:id=\"@android:id/text2\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:textAppearance=\"?android:attr/textAppearanceSmall\"/></TwoLineListItem>");
        d.put("layout:simple_list_item_checked",
                "<CheckedTextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:checkMark=\"?android:attr/listChoiceIndicatorMultiple\""
                        + " android:textAppearance=\"?android:attr/textAppearanceListItem\""
                        + " android:gravity=\"center_vertical\" android:minHeight=\"?android:attr/listPreferredItemHeight\"/>");
        d.put("layout:simple_spinner_item",
                "<TextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:singleLine=\"true\" android:ellipsize=\"marquee\""
                        + " android:textAppearance=\"?android:attr/textAppearanceListItemSmall\""
                        + " android:gravity=\"center\"/>");
        d.put("layout:simple_spinner_dropdown_item",
                "<CheckedTextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"?android:attr/listPreferredItemHeightSmall\""
                        + " android:singleLine=\"true\" android:ellipsize=\"marquee\""
                        + " android:textAppearance=\"?android:attr/textAppearanceListItemSmall\""
                        + " android:gravity=\"center_vertical\" android:checkMark=\"?android:attr/listChoiceIndicatorSingle\""
                        + " android:paddingStart=\"8dp\" android:paddingEnd=\"8dp\"/>");
        d.put("layout:select_dialog_item",
                "<TextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:minHeight=\"?android:attr/listPreferredItemHeight\""
                        + " android:textAppearance=\"?android:attr/textAppearanceLarge\""
                        + " android:gravity=\"center_vertical\" android:paddingStart=\"14dp\" android:paddingEnd=\"14dp\"/>");
        d.put("layout:select_dialog_singlechoice",
                "<CheckedTextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:minHeight=\"?android:attr/listPreferredItemHeight\""
                        + " android:checkMark=\"?android:attr/listChoiceIndicatorSingle\""
                        + " android:textAppearance=\"?android:attr/textAppearanceLarge\""
                        + " android:gravity=\"center_vertical\" android:paddingStart=\"14dp\" android:paddingEnd=\"14dp\"/>");
        d.put("layout:select_dialog_multichoice",
                "<CheckedTextView " + XMLNS + " android:id=\"@android:id/text1\""
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:minHeight=\"?android:attr/listPreferredItemHeight\""
                        + " android:checkMark=\"?android:attr/listChoiceIndicatorMultiple\""
                        + " android:textAppearance=\"?android:attr/textAppearanceLarge\""
                        + " android:gravity=\"center_vertical\" android:paddingStart=\"14dp\" android:paddingEnd=\"14dp\"/>");
        return d;
    }

    // ------------------------------------------------------------------
    // Id mapping via the framework jar's own R constants (always in sync
    // with the bytecode the app is compiled against).
    // ------------------------------------------------------------------
    private static void init() {
        if (sIdToEntry != null) return;
        synchronized (FrameworkRes.class) {
            if (sIdToEntry != null) return;
            Map<Integer, String[]> ids = new HashMap<>();
            for (String cls : new String[]{
                    "android.R$interpolator", "com.android.internal.R$interpolator",
                    "android.R$anim", "com.android.internal.R$anim",
                    "android.R$animator", "com.android.internal.R$animator",
                    "android.R$layout", "com.android.internal.R$layout"}) {
                try {
                    Class<?> c = Class.forName(cls);
                    String type = cls.endsWith("$interpolator") ? "interpolator"
                            : cls.endsWith("$animator") ? "animator"
                            : cls.endsWith("$layout") ? "layout" : "anim";
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() != int.class) continue;
                        try {
                            int id = f.getInt(null);
                            if (id != 0 && !ids.containsKey(id)) {
                                ids.put(id, new String[]{type, f.getName()});
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            sIdToEntry = ids;

            Map<String, Map<String, Integer>> typeMaps = new HashMap<>();
            for (String cls : new String[]{
                    "android.R$attr", "com.android.internal.R$attr",
                    "android.R$id", "com.android.internal.R$id",
                    "android.R$layout", "com.android.internal.R$layout",
                    "android.R$drawable", "com.android.internal.R$drawable",
                    "android.R$string", "com.android.internal.R$string",
                    "android.R$style", "com.android.internal.R$style",
                    "android.R$anim", "com.android.internal.R$anim",
                    "android.R$interpolator", "com.android.internal.R$interpolator",
                    "android.R$color", "com.android.internal.R$color",
                    "android.R$bool", "com.android.internal.R$bool",
                    "android.R$integer", "com.android.internal.R$integer",
                    "android.R$dimen", "com.android.internal.R$dimen"}) {
                try {
                    Class<?> c = Class.forName(cls);
                    String type = cls.substring(cls.lastIndexOf('$') + 1).toLowerCase(java.util.Locale.US);
                    Map<String, Integer> map = typeMaps.computeIfAbsent(type, k -> new HashMap<>());
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() != int.class) continue;
                        try {
                            int id = f.getInt(null);
                            if (id != 0) map.putIfAbsent(f.getName(), id);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            sTypeMaps = typeMaps;
            sAttrIds = typeMaps.getOrDefault("attr", new HashMap<>());
            sViewIds = typeMaps.getOrDefault("id", new HashMap<>());
        }
    }

    /** True if this id belongs to the system package (0x01) and is known here. */
    public static boolean has(int resid) {
        init();
        return sIdToEntry.containsKey(resid);
    }

    /**
     * Parse the framework resource into a RealXmlResourceParser.Node tree
     * (same node model the binary-XML pipeline produces, so LayoutInflater /
     * AnimationUtils / TypedArray consume it unchanged).
     */
    public static RealXmlResourceParser.Node getNode(int resid) {
        init();
        String[] entry = sIdToEntry.get(resid);
        if (entry == null) return null;
        String xml = DEFS.get(entry[0] + ":" + entry[1]);
        if (xml == null) return null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            if (root == null) return null;
            return fromElement(root, null);
        } catch (Throwable t) {
            System.out.println("[-] [FrameworkRes] parse failed for #0x"
                    + Integer.toHexString(resid) + " (" + entry[0] + "/" + entry[1] + "): " + t);
            return null;
        }
    }

    private static RealXmlResourceParser.Node fromElement(Element e, RealXmlResourceParser.Node parent) {
        RealXmlResourceParser.Node node = new RealXmlResourceParser.Node();
        node.tag = e.getTagName();
        node.parent = parent;
        if (parent != null) parent.children.add(node);

        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node a = attrs.item(i);
            String qname = a.getNodeName();
            if (qname.startsWith("xmlns")) continue;
            String name = qname.contains(":")
                    ? qname.substring(qname.lastIndexOf(':') + 1) : qname;
            String value = a.getNodeValue();
            if (value == null) continue;
            node.attributes.put(name, value);
            Integer attrId = sAttrIds.get(name);
            if (attrId != null) {
                node.attrValues.put(attrId, value);
            }
            // resolve resource references exactly like aapt2 compiled them:
            // @android:id/x, @id/x, @+id/x, @layout/x, @drawable/x ...
            if (value.startsWith("@")) {
                int ref = resolveReference(value);
                if (ref != 0) {
                    node.resIds.put(name, ref);
                    if (attrId != null) node.attrResIds.put(attrId, ref);
                }
            }
        }
        // child elements
        org.w3c.dom.Node child = e.getFirstChild();
        while (child != null) {
            if (child instanceof Element) {
                fromElement((Element) child, node);
            } else if (child.getNodeValue() != null && !child.getNodeValue().trim().isEmpty()) {
                node.attributes.put("#text", child.getNodeValue());
            }
            child = child.getNextSibling();
        }
        return node;
    }

    /**
     * Resolve "@android:id/content", "@id/x", "@+id/x", "@layout/y",
     * "@drawable/z" to the framework's compiled resource id (aapt2 compile
     * semantics). Returns 0 when unresolvable.
     */
    private static int resolveReference(String value) {
        String v = value.startsWith("@+") ? value.substring(2) : value.substring(1);
        String type;
        String name;
        int slash = v.indexOf('/');
        if (slash < 0) return 0;
        if (v.startsWith("android:")) {
            v = v.substring("android:".length());
            slash = v.indexOf('/');
            if (slash < 0) return 0;
        }
        type = v.substring(0, slash);
        name = v.substring(slash + 1);
        Map<String, Integer> map = sTypeMaps.get(type);
        if (map == null) return 0;
        Integer id = map.get(name);
        return id != null ? id : 0;
    }

    /** All known entry names (for diagnostics). */
    public static List<String> knownEntries() {
        init();
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, String[]> e : sIdToEntry.entrySet()) {
            out.add("0x" + Integer.toHexString(e.getKey()) + " " + e.getValue()[0] + "/" + e.getValue()[1]);
        }
        return out;
    }
}
