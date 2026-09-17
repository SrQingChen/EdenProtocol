package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.network.ChooseClassPayload;
import com.srqingchen.eden.network.ClientTalentData;
import com.srqingchen.eden.network.ConvertTPPayload;
import com.srqingchen.eden.network.TalentClickPayload;
import com.srqingchen.eden.talent.ClassId;
import com.srqingchen.eden.talent.TalentNode;
import com.srqingchen.eden.talent.TalentNodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The talent / class screen (P3), drawn entirely in code (mirrors {@code ShopScreen}'s 26.1.2 pattern). Six tabs across
 * the top switch which tree is viewed (universal, or one of the five classes - clicking a class tab also makes it the
 * active class). The selected tree is a free, pannable/zoomable canvas (FTB-quest-book style): nodes live on a world
 * grid ({@code world = node.{x,y} * CELL}), mapped to screen by {@code screen = vp + world*zoom - pan}. Grid coords may
 * be NEGATIVE - every tree radiates from its origin at (0,0) (2026-09-16 radial matrix redesign). Drag with the
 * left button to PAN, scroll to ZOOM about the cursor, double-click to re-fit; opening / switching tabs auto-fits the
 * whole tree into the viewport. A click that did not drag (below {@code DRAG_THRESHOLD}) unlocks the node once (the
 * server re-validates tree / prereqs / cost).
 * <p><b>Star nodes</b>: nodes render as STAR SHAPES rasterized in code (scanline polygon fill over thin
 * {@code fill}s - {@code GuiGraphicsExtractor} has no polygon primitive): SMALL = 4-point spark, MEDIUM = 5-point star,
 * LARGE = 6-point star with a radial glow halo. Unlocked stars glow, available stars breathe slowly, keystones carry a
 * gold halo. Hit-testing uses the star's circumscribed circle.
 */
@OnlyIn(Dist.CLIENT)
public class TalentScreen extends Screen {

    private static final int TAB_Y = 20, TAB_H = 15;
    private static final double CELL = 64;                 // logical spacing between node centres (world units)
    private static final double ZOOM_MIN = 0.35, ZOOM_MAX = 2.2;
    private static final double DRAG_THRESHOLD = 4;        // px of movement before a press counts as a pan, not a click
    private static final int COL_UNLOCKED = 0xFF3FBF5F;
    private static final int COL_AVAILABLE = 0xFF4C8FD6;
    private static final int COL_POOR = 0xFF8A7A3F;
    private static final int COL_LOCKED = 0xFF4A4A55;
    private static final int COL_INACTIVE = 0xFF3A3A45;
    private static final int COL_LINE_LIT = 0xFF4FBF6F;
    private static final int COL_LINE_DIM = 0xFF33333D;
    /** Star radius per tier, in world units (multiplied by zoom when drawn). */
    private static final double R_SMALL = 14, R_MEDIUM = 19, R_LARGE = 25;

    private String selectedTree = "";
    private double panX = 0, panY = 0, zoom = 1;
    private boolean panning = false;
    private double dragDist = 0;
    private final List<NV> views = new ArrayList<>();
    private final Map<String, NV> byId = new HashMap<>();
    private NV hovered = null;

    private static final class NV {
        final TalentNode node; final int cx, cy; final double r;
        NV(TalentNode n, int cx, int cy, double r) { this.node = n; this.cx = cx; this.cy = cy; this.r = r; }
    }

    public TalentScreen() {
        super(Component.translatable("eden.talent.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TalentScreen());
    }

    @Override
    protected void init() {
        super.init();
        this.selectedTree = ClientTalentData.currentClass.isEmpty() ? "" : ClientTalentData.currentClass;
        this.addRenderableWidget(Button.builder(Component.translatable("eden.talent.convert"),
                        b -> ClientPacketDistributor.sendToServer(new ConvertTPPayload(1)))
                .pos(this.width - 172, 3).size(88, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("eden.talent.close"), b -> this.onClose())
                .pos(this.width - 80, 3).size(68, 16).build());
        fitView();
    }

    // ---------- viewport metrics ----------

    private int vpLeft() { return 12; }
    private int vpRight() { return this.width - 12; }
    private int vpTop() { return 52; }
    private int vpBottom() { return this.height - 22; }

    private static String treeForTab(int i) {
        if (i == 0) return "";
        ClassId[] cs = ClassId.values();
        return (i - 1) < cs.length ? cs[i - 1].id : "";
    }

    private static Component tabLabel(int i) {
        if (i == 0) return Component.translatable("eden.talent.tab.universal");
        return Component.translatable(ClassId.values()[i - 1].nameKey());
    }

    private int tabX1(int i) { return vpLeft() + i * (vpRight() - vpLeft()) / 6; }
    private int tabX2(int i) { return vpLeft() + (i + 1) * (vpRight() - vpLeft()) / 6; }

    // ---------- pan / zoom ----------

    private double sx(double wx) { return vpLeft() + wx * zoom - panX; }
    private double sy(double wy) { return vpTop() + wy * zoom - panY; }

    /** Fit the whole current tree into the viewport (scale to content, then centre it). Negative-safe. */
    private void fitView() {
        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (TalentNode n : TalentNodes.ofTree(this.selectedTree)) {
            minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y());
        }
        double cw = (maxX - minX) * CELL + CELL * 1.9;
        double ch = (maxY - minY) * CELL + CELL * 1.9;
        double vpW = vpRight() - vpLeft(), vpH = vpBottom() - vpTop();
        zoom = clamp(Math.min(vpW / cw, vpH / ch), ZOOM_MIN, ZOOM_MAX);
        panX = ((minX + maxX) * CELL / 2) * zoom - vpW / 2;
        panY = ((minY + maxY) * CELL / 2) * zoom - vpH / 2;
    }

    /** Zoom about a screen point (keeps the world point under the cursor fixed). */
    private void zoomAt(double mx, double my, double factor) {
        double wx = (mx - vpLeft() + panX) / zoom;
        double wy = (my - vpTop() + panY) / zoom;
        double nz = clamp(zoom * factor, ZOOM_MIN, ZOOM_MAX);
        panX = wx * nz - (mx - vpLeft());
        panY = wy * nz - (my - vpTop());
        zoom = nz;
    }

    private boolean inViewport(double mx, double my) {
        return mx >= vpLeft() && mx < vpRight() && my >= vpTop() && my < vpBottom();
    }

    // ---------- client TP accounting (mirrors TalentSystem) ----------

    private static int costOf(Map<String, Integer> ranks) {
        int s = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            TalentNode n = TalentNodes.get(e.getKey());
            if (n != null && e.getValue() != null) s += n.cost() * Math.max(0, e.getValue());
        }
        return s;
    }

    private static int availableTP() {
        int spent = costOf(ClientTalentData.universal);
        String cc = ClientTalentData.currentClass;
        if (!cc.isEmpty()) {
            Map<String, Integer> m = ClientTalentData.classTrees.get(cc);
            if (m != null) spent += costOf(m);
        }
        return Math.max(0, ClientTalentData.totalTP - spent);
    }

    private static boolean treeActive(String tree) {
        return tree.isEmpty() || tree.equals(ClientTalentData.currentClass);
    }

    /** 0 locked, 1 poor (prereq ok, unaffordable), 2 available, 3 unlocked. */
    private static int stateOf(TalentNode n, String tree) {
        if (ClientTalentData.rankOf(tree, n.id()) > 0) return 3;
        for (String pre : n.prereqs()) {
            if (ClientTalentData.rankOf(tree, pre) <= 0) return 0;
        }
        if (!treeActive(tree)) return 0;
        return n.cost() <= availableTP() ? 2 : 1;
    }

    private static int stateColor(int st) {
        return st == 3 ? COL_UNLOCKED : st == 2 ? COL_AVAILABLE : st == 1 ? COL_POOR : COL_LOCKED;
    }

    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    // ---------- rendering ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xF20B0B12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        g.text(this.font, this.title.getString(), vpLeft(), 6, 0xFFFFFFFF);
        String info = Component.translatable("eden.talent.tp", availableTP(), ClientTalentData.totalTP).getString()
                + "   " + Component.translatable("eden.talent.supply", ClientTalentData.supplyPoints).getString();
        g.text(this.font, info, vpLeft(), 39, 0xFF88FF88);
        ClassId cc = ClassId.byId(ClientTalentData.currentClass);
        String cur = cc == null ? Component.translatable("eden.talent.no_class").getString()
                : Component.translatable("eden.talent.active_class", Component.translatable(cc.nameKey())).getString();
        g.text(this.font, cur, vpRight() - this.font.width(cur), 39, 0xFFDDDD66);

        // tabs
        for (int i = 0; i < 6; i++) {
            int x1 = tabX1(i), x2 = tabX2(i);
            boolean sel = treeForTab(i).equals(this.selectedTree);
            g.fill(x1 + 1, TAB_Y, x2 - 1, TAB_Y + TAB_H, sel ? 0xFF2E4A6B : 0xFF1C1C26);
            g.centeredText(this.font, tabLabel(i), (x1 + x2) / 2, TAB_Y + 4, sel ? 0xFF9FD0FF : 0xFF9A9AA6);
        }

        // lay out the selected tree in world -> screen space
        this.views.clear();
        this.byId.clear();
        for (TalentNode n : TalentNodes.ofTree(this.selectedTree)) {
            int cx = (int) sx(n.x() * CELL), cy = (int) sy(n.y() * CELL);
            double r = (n.tier() == TalentNode.Tier.LARGE ? R_LARGE
                    : n.tier() == TalentNode.Tier.MEDIUM ? R_MEDIUM : R_SMALL) * clamp(zoom, 0.25, 2.5);
            NV nv = new NV(n, cx, cy, r);
            this.views.add(nv);
            this.byId.put(n.id(), nv);
        }

        g.enableScissor(vpLeft(), vpTop(), vpRight(), vpBottom());

        // connectors: direct edge-to-edge lines (elbow links used to run under other stars and
        // read as broken prerequisites - a trimmed straight line keeps every relation visible).
        for (NV nv : this.views) {
            for (String pre : nv.node.prereqs()) {
                NV p = this.byId.get(pre);
                if (p == null) continue;
                boolean lit = ClientTalentData.rankOf(this.selectedTree, pre) > 0
                        && ClientTalentData.rankOf(this.selectedTree, nv.node.id()) > 0;
                linkLine(g, p, nv, lit ? COL_LINE_LIT : COL_LINE_DIM);
            }
        }

        // star nodes
        boolean act = treeActive(this.selectedTree);
        boolean showName = CELL * zoom >= 34;
        int nameW = (int) (CELL * zoom);
        double breath = 0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 2400) / 2400.0 * Math.PI * 2);
        this.hovered = null;
        for (NV nv : this.views) {
            int x1 = (int) (nv.cx - nv.r * 1.3), y1 = (int) (nv.cy - nv.r * 1.3);
            int x2 = (int) (nv.cx + nv.r * 1.3), y2 = (int) (nv.cy + nv.r * 1.3);
            boolean hov = inViewport(mouseX, mouseY)
                    && (mouseX - nv.cx) * (mouseX - nv.cx) + (mouseY - nv.cy) * (mouseY - nv.cy) <= nv.r * nv.r * 1.7;
            if (hov) this.hovered = nv;
            if (x2 < vpLeft() || x1 > vpRight() || y2 < vpTop() || y1 > vpBottom()) continue;   // off-view cull
            int st = stateOf(nv.node, this.selectedTree);
            int col = act ? stateColor(st) : COL_INACTIVE;
            int pts = nv.node.tier() == TalentNode.Tier.LARGE ? 6
                    : nv.node.tier() == TalentNode.Tier.MEDIUM ? 5 : 4;

            // keystone halo (gold aura) + unlocked/available glow behind the star
            if (nv.node.tier() == TalentNode.Tier.LARGE) {
                g.fillGradient((int) (nv.cx - nv.r * 1.8), (int) (nv.cy - nv.r * 1.8),
                        (int) (nv.cx + nv.r * 1.8), (int) (nv.cy + nv.r * 1.8), 0x66FFD24A, 0x11FFD24A);
            }
            if (st == 3 && act) {
                star(g, nv.cx, nv.cy, nv.r * 1.45, pts, 0x303FBF5F); // unlocked soft glow
            } else if (st == 2 && act) {
                int a = 0x18 + (int) (0x30 * breath);                // available: slow breathing
                star(g, nv.cx, nv.cy, nv.r * 1.45, pts, (a << 24) | (COL_AVAILABLE & 0xFFFFFF));
            }
            star(g, nv.cx, nv.cy, nv.r, pts, col);
            // bright core sparkle on unlocked stars
            if (st == 3 && act && nv.r >= 8) {
                star(g, nv.cx, nv.cy, nv.r * 0.45, pts, 0xFFE8FFE8);
            }
            // hover ring
            if (hov) {
                ring(g, nv.cx, nv.cy, nv.r * 1.55, 0xFFFFFFFF);
            }
            if (showName) {
                String nm = fit(Component.translatable(nv.node.nameKey()).getString(), nameW);
                g.centeredText(this.font, nm, nv.cx, (int) (nv.cy + nv.r + 3),
                        !act ? 0xFF5A5A66 : st == 0 ? 0xFF6E6E7A : 0xFFC8C8D2);
            }
        }

        g.disableScissor();
        if (this.hovered != null) drawDetails(g, mouseX, mouseY);

        String hint = Component.translatable("eden.talent.hover_hint").getString();
        g.text(this.font, fit(hint, vpRight() - vpLeft()), vpLeft(), this.height - 13, 0xFF7A7A88);
    }

    // ---------- star rasterization (scanline polygon fill over 1px fills) ----------

    /**
     * Draw a star with {@code points} outer tips and an inner radius ratio per point count
     * (SMALL=4-point spark, MEDIUM=5-point star, LARGE=6-point star).
     */
    private static void star(GuiGraphicsExtractor g, double cx, double cy, double r, int points, int color) {
        int n = points * 2;
        double[] xs = new double[n], ys = new double[n];
        double inner = points <= 4 ? 0.38 : points == 5 ? 0.42 : 0.55;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / points;
            double rr = (i % 2 == 0) ? r : r * inner;
            xs[i] = cx + Math.cos(ang) * rr;
            ys[i] = cy + Math.sin(ang) * rr;
        }
        fillPolygon(g, xs, ys, color);
    }

    /** Even-odd scanline fill of a polygon via 1px {@code fill} spans. Fine for <=60px shapes. */
    private static void fillPolygon(GuiGraphicsExtractor g, double[] xs, double[] ys, int color) {
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double v : ys) { minY = Math.min(minY, v); maxY = Math.max(maxY, v); }
        List<Double> hits = new ArrayList<>();
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            double scan = y + 0.5;
            hits.clear();
            for (int i = 0; i < xs.length; i++) {
                int j = (i + 1) % xs.length;
                double y1 = ys[i], y2 = ys[j];
                if ((y1 <= scan && y2 > scan) || (y2 <= scan && y1 > scan)) {
                    double t = (scan - y1) / (y2 - y1);
                    hits.add(xs[i] + t * (xs[j] - xs[i]));
                }
            }
            if (hits.size() < 2) continue;
            Collections.sort(hits);
            for (int k = 0; k + 1 < hits.size(); k += 2) {
                int x1 = (int) Math.round(hits.get(k)), x2 = (int) Math.round(hits.get(k + 1));
                if (x2 >= x1) g.fill(x1, y, x2 + 1, y + 1, color);
            }
        }
    }

    /** Thin circle outline (a hover ring), rasterized as short 1px spans. */
    private static void ring(GuiGraphicsExtractor g, double cx, double cy, double r, int color) {
        int steps = Math.max(12, (int) (r * 2.5));
        for (int i = 0; i < steps; i++) {
            double a1 = i * Math.PI * 2 / steps, a2 = (i + 0.5) * Math.PI * 2 / steps;
            int x1 = (int) Math.round(cx + Math.cos(a1) * r), y1 = (int) Math.round(cy + Math.sin(a1) * r);
            int x2 = (int) Math.round(cx + Math.cos(a2) * r), y2 = (int) Math.round(cy + Math.sin(a2) * r);
            g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        }
    }

    private void drawDetails(GuiGraphicsExtractor g, int mx, int my) {
        TalentNode n = this.hovered.node;
        int st = stateOf(n, this.selectedTree);
        List<String> lines = new ArrayList<>();
        lines.add(Component.translatable(n.nameKey()).getString());
        lines.add(Component.translatable(n.descKey()).getString());
        lines.add(Component.translatable("eden.talent.cost", n.cost()).getString());
        // LARGE keystones additionally consume one ancient relic (§19.5) when unlocking.
        int relicLine = -1;
        if (n.tier() == TalentNode.Tier.LARGE) {
            lines.add(Component.translatable("eden.talent.relic_cost").getString());
            relicLine = lines.size() - 1;
        }
        String stKey = st == 3 ? "eden.talent.state.unlocked" : st == 2 ? "eden.talent.state.available"
                : st == 1 ? "eden.talent.state.poor" : "eden.talent.state.locked";
        lines.add(Component.translatable(stKey).getString());
        // Prerequisite names (only when still relevant) - the map lines show the shape, this names it.
        if (st != 3 && !n.prereqs().isEmpty()) {
            StringBuilder pres = new StringBuilder();
            for (String pre : n.prereqs()) {
                TalentNode pn = TalentNodes.byId(pre);
                if (pn == null) {
                    continue;
                }
                if (pres.length() > 0) {
                    pres.append("、");
                }
                boolean done = ClientTalentData.rankOf(this.selectedTree, pre) > 0;
                pres.append(done ? "✓" : "○").append(Component.translatable(pn.nameKey()).getString());
            }
            if (pres.length() > 0) {
                lines.add(Component.translatable("eden.talent.prereqs", pres.toString()).getString());
            }
        }

        int w = 0;
        for (String s : lines) w = Math.max(w, this.font.width(s));
        w = Math.min(w + 12, this.width - 20);
        int h = lines.size() * 11 + 8;
        int x = mx + 14, y = my + 14;
        if (x + w > this.width - 4) x = mx - w - 14;
        if (y + h > this.height - 4) y = my - h - 14;
        x = Math.max(4, x); y = Math.max(4, y);
        g.fill(x, y, x + w, y + h, 0xF6101018);
        border(g, x, y, x + w, y + h, 0xFF5A5A6E);
        int ty = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            int col = i == 0 ? 0xFFFFFFFF
                    : i == relicLine ? 0xFFE8C97A
                    : i == lines.size() - 1 ? stateColor(st)
                    : 0xFFC8C8D2;
            g.text(this.font, lines.get(i), x + 6, ty, col);
            ty += 11;
        }
    }

    // ---------- draw helpers (fill-based; avoid outline's x/y/w/h signature) ----------

    /** Straight connector between two stars, trimmed to their edges so it never hides under a node. */
    private static void linkLine(GuiGraphicsExtractor g, NV from, NV to, int col) {
        double dx = to.cx - from.cx, dy = to.cy - from.cy;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0) {
            return;
        }
        double ux = dx / len, uy = dy / len;
        int x0 = (int) Math.round(from.cx + ux * (from.r + 2));
        int y0 = (int) Math.round(from.cy + uy * (from.r + 2));
        int x1 = (int) Math.round(to.cx - ux * (to.r + 2));
        int y1 = (int) Math.round(to.cy - uy * (to.r + 2));
        // Bresenham over 1px fills (same idiom as the star rasterizer).
        int x = x0, y = y0;
        int sx = x1 > x0 ? 1 : -1, sy = y1 > y0 ? 1 : -1;
        int adx = Math.abs(x1 - x0), ady = -Math.abs(y1 - y0);
        int err = adx + ady;
        while (true) {
            g.fill(x, y, x + 1, y + 1, col);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= ady) {
                err += ady;
                x += sx;
            }
            if (e2 <= adx) {
                err += adx;
                y += sy;
            }
        }
    }

    private static void hline(GuiGraphicsExtractor g, int x1, int x2, int y, int col) {
        int a = Math.min(x1, x2), b = Math.max(x1, x2);
        g.fill(a, y, b + 1, y + 1, col);
    }

    private static void vline(GuiGraphicsExtractor g, int x, int y1, int y2, int col) {
        int a = Math.min(y1, y2), b = Math.max(y1, y2);
        g.fill(x, a, x + 1, b + 1, col);
    }

    private static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int col) {
        g.fill(x1, y1, x2, y1 + 1, col);
        g.fill(x1, y2 - 1, x2, y2, col);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, col);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, col);
    }

    private String fit(String s, int maxW) {
        if (maxW <= 0 || this.font.width(s) <= maxW) return s;
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i)) > maxW) i--;
        return s.substring(0, i);
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x(), my = event.y();
        if (super.mouseClicked(event, isDoubleClick)) return true;

        if (isDoubleClick && inViewport(mx, my)) {
            fitView();
            return true;
        }

        // tabs
        if (my >= TAB_Y && my < TAB_Y + TAB_H) {
            for (int i = 0; i < 6; i++) {
                if (mx >= tabX1(i) && mx < tabX2(i)) {
                    String tree = treeForTab(i);
                    this.selectedTree = tree;
                    fitView();
                    if (!tree.isEmpty() && !tree.equals(ClientTalentData.currentClass)) {
                        ClientPacketDistributor.sendToServer(new ChooseClassPayload(tree));
                    }
                    return true;
                }
            }
        }

        // begin a potential pan (or a click, decided on release by how far we dragged)
        if (event.button() == 0 && inViewport(mx, my)) {
            this.panning = true;
            this.dragDist = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.panning) {
            panX -= dragX;
            panY -= dragY;
            dragDist += Math.abs(dragX) + Math.abs(dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.panning) {
            boolean wasClick = dragDist < DRAG_THRESHOLD;
            this.panning = false;
            if (wasClick) clickNode(event.x(), event.y());
            return true;
        }
        return super.mouseReleased(event);
    }

    private void clickNode(double mx, double my) {
        if (!inViewport(mx, my)) return;
        for (NV nv : this.views) {
            double dx = mx - nv.cx, dy = my - nv.cy;
            if (dx * dx + dy * dy <= nv.r * nv.r * 1.7) {
                if (treeActive(this.selectedTree) && stateOf(nv.node, this.selectedTree) != 3) {
                    ClientPacketDistributor.sendToServer(new TalentClickPayload(nv.node.id()));
                }
                return;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXDelta, double scrollYDelta) {
        double d = scrollYDelta != 0 ? scrollYDelta : scrollXDelta;
        if (d != 0 && inViewport(mouseX, mouseY)) {
            zoomAt(mouseX, mouseY, d > 0 ? 1.12 : 1 / 1.12);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollXDelta, scrollYDelta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
