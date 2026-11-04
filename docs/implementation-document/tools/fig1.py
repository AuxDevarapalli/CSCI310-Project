# Draws Figure 1 (the revised block diagram) as a hand-laid-out SVG.
# Every box is sized from measured Helvetica text widths, so no text can overflow.
import sys, html, fitz

OUT = sys.argv[1]
FONT = "Helvetica, Arial, sans-serif"
LH = 13.0          # line height
PAD_X, PAD_Y = 9, 7

def width(text, bold=False, size=10.0, italic=False):
    font = "hebo" if bold else ("heit" if italic else "helv")
    return fitz.get_text_length(text, fontname=font, fontsize=size)

class Box:
    def __init__(self, lines, size=10.0, align="middle", min_w=0, dashed=False, title=None):
        # lines: list of (style, text); style in {"b", "n", "i"}
        self.lines, self.size, self.align, self.dashed = lines, size, align, dashed
        w = max(width(t, s == "b", size, s == "i") for s, t in lines) if lines else 0
        self.w = max(min_w, w + 2 * PAD_X + 4)
        self.h = len(lines) * LH + 2 * PAD_Y
        self.x = self.y = 0
    @property
    def r(self): return self.x + self.w
    @property
    def b(self): return self.y + self.h
    @property
    def cx(self): return self.x + self.w / 2
    @property
    def cy(self): return self.y + self.h / 2
    def svg(self):
        dash = ' stroke-dasharray="5,3"' if self.dashed else ""
        out = [f'<rect x="{self.x:.1f}" y="{self.y:.1f}" width="{self.w:.1f}" height="{self.h:.1f}" rx="4" fill="#FFFFFF" stroke="#333333" stroke-width="1"{dash}/>']
        for i, (s, t) in enumerate(self.lines):
            ty = self.y + PAD_Y + LH * (i + 1) - 3
            tx = self.cx if self.align == "middle" else self.x + PAD_X
            wt = ' font-weight="bold"' if s == "b" else ""
            st = ' font-style="italic"' if s == "i" else ""
            fill = ' fill="#555555"' if s == "i" else ""
            out.append(f'<text x="{tx:.1f}" y="{ty:.1f}" font-size="{self.size}" text-anchor="{self.align if self.align == "middle" else "start"}"{wt}{st}{fill}>{html.escape(t)}</text>')
        return "\n".join(out)

def frame(x, y, w, h, label, dashed=True, size=10, bold=True, fill="#FAFAFA"):
    dash = ' stroke-dasharray="5,3"' if dashed else ""
    wt = ' font-weight="bold"' if bold else ""
    return (f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="6" fill="{fill}" stroke="#777777" stroke-width="1"{dash}/>'
            f'<text x="{x + 8:.1f}" y="{y + 14:.1f}" font-size="{size}"{wt} fill="#333333">{html.escape(label)}</text>')

def label(x, y, lines, anchor="middle", size=9, bold=False):
    out = []
    for i, t in enumerate(lines):
        wt = ' font-weight="bold"' if bold else ""
        out.append(f'<text x="{x:.1f}" y="{y + i * 11:.1f}" font-size="{size}" text-anchor="{anchor}" fill="#333333"{wt}>{html.escape(t)}</text>')
    return "\n".join(out)

def path(points, dashed=False):
    d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in points)
    dash = ' stroke-dasharray="5,3"' if dashed else ""
    return f'<path d="{d}" fill="none" stroke="#333333" stroke-width="1.1"{dash} marker-end="url(#arrow)"/>'

B, N, I = "b", "n", "i"
app = Box([(B, "SplitSmartApp"), (B, "new: Application class that builds the"),
           (B, "AppContainer once and checks that"), (B, "Firebase is configured (A1)")])
c1 = Box([(B, "C1 UI Views"), (N, "Activities, 4 tab fragments, dialogs, adapters"),
          (B, "new: BaseActivity, BaseFragment, MembersFragment,"),
          (B, "GroupPagerAdapter, Refreshable, UiUtils (D1)"),
          (B, "changed: screens pass ids in Intents, not objects (D2)"),
          (B, "changed: settlement form: “I paid” / “I was paid” (R9)"),
          (B, "new: Match case chip in the history (R13)")])
c2 = Box([(B, "C2 ViewModels"), (N, "Screen state as LiveData; permission checks"),
          (B, "new: Event<T> one-time messages, form-state classes (D3)"),
          (B, "new: own the live-update Subscriptions (A5)"),
          (B, "changed: AppContainer injected, no longer a singleton (A1)"),
          (B, "changed: settlements recorded from either side (R9)")])
val = Box([(B, "Validation and formatting"), (N, "InputValidator, Money,"), (N, "ValidationResult, DateProvider"),
           (B, "changed: more rules and limits,"), (B, "month arithmetic (D4)")])
spl = Box([(B, "Split engine"), (N, "SplitCalculator: equal /"), (N, "exact / percentage,"),
           (N, "FR 3.5 rounding"), (B, "changed: 0% rule (R4)")])
bal = Box([(B, "Balance engine"), (N, "BalanceCalculator,"), (N, "BalanceSummary, PairBalance"),
           (B, "new: amountOwed (settlement cap),"), (B, "computeTotals (D4)")])
his = Box([(B, "History and summary engine"), (N, "HistoryFilter, HistoryQuery,"), (N, "SummaryCalculator"),
           (B, "new: monthsWithExpenses (D4),"), (B, "Match case option (R13)")])
c4 = Box([(B, "C4 Repositories: the only classes that import the Firebase SDK"),
          (N, "AuthRepository, UserRepository, GroupRepository, TransactionRepository, DataCallback<T>"),
          (B, "new: FirestoreMapper (schema in one place), FirebaseErrors / RepositoryException (A2)"),
          (B, "new: Subscription for snapshot listeners (A5); removeMember, transferOwnership (A6)"),
          (B, "changed: registration order (A3); batched group delete (D6)")])
other = Box([(N, "Other members’ phones run the same app and read and write the same database"),
             (I, "(NFR 3: a member’s change is visible to the others on refresh, and is now also pushed live)")],
            dashed=True)
c5 = Box([(B, "C5 Firebase Authentication"), (N, "Email + password accounts,"),
          (N, "passwords stored only as hashes (NFR 4),"), (N, "login session. Unchanged.")])
c6 = Box([(B, "C6 Cloud Firestore"), (N, "Collections: users, uscIds, groups,"), (N, "groups/{groupId}/transactions"),
          (B, "changed: users + createdAt; transactions"), (B, "+ updatedAt, + participantIds (5.4)"),
          (B, "changed: rules also validate contents, and"), (B, "the owner may delete a whole group (A4)"),
          (B, "changed: a settlement may be recorded by"), (B, "its payer or its receiver (R9)"),
          (B, "changed: leave / remove / transfer"), (B, "ownership (A6)"),
          (B, "new: snapshot listeners push changes"), (B, "to open screens (A5)")])

GAP = 14           # space between sibling boxes
LAYER_PAD = 12     # padding inside a layer frame
LAYER_TOP = 22     # room for a layer frame's label
CORRIDOR = 44      # space right of the layers, for the C2 -> C4 arrow
X0, Y0 = 6, 34     # phone frame origin (title above)

# --- widths ---------------------------------------------------------------
engines = [val, spl, bal, his]
eng_h = max(e.h for e in engines)
for e in engines: e.h = eng_h
engines_w = sum(e.w for e in engines) + GAP * (len(engines) - 1)
model = Box([(B, "Domain model (shared by every layer)"),
             (N, "User, Group, Transaction, Expense, Settlement, Share, enums; money as long cents"),
             (B, "changed: all model objects are immutable; with...() copies replace the setters (D5)")],
            min_w=engines_w)
c3_inner_w = max(engines_w, model.w)
model.w = c3_inner_w
c3_w = c3_inner_w + 2 * LAYER_PAD
pres_row_w = c1.w + 120 + c2.w
layer_inner_w = max(pres_row_w, c3_w, c4.w, other.w)
layer_w = layer_inner_w + 2 * LAYER_PAD

# --- vertical layout inside the phone ---------------------------------------
lx = X0 + 10                                   # layer frames' x
y = Y0 + 24
# SplitSmartApp sits above C2
pres_x0 = lx + LAYER_PAD + (layer_inner_w - pres_row_w) / 2
c1.x = pres_x0
c2.x = pres_x0 + c1.w + 120
app.x = c2.cx - app.w / 2
app.y = y
y = app.b + 30
pres_y = y
c1.y = c2.y = pres_y + LAYER_TOP
row_h = max(c1.h, c2.h)
c1.h = c2.h = row_h
pres_h = LAYER_TOP + row_h + LAYER_PAD
y = pres_y + pres_h + 30
biz_y = y
c3_x = lx + LAYER_PAD + (layer_inner_w - c3_w) / 2
c3_y = biz_y + LAYER_TOP
ex = c3_x + LAYER_PAD + (c3_inner_w - engines_w) / 2
for e in engines:
    e.x, e.y = ex, c3_y + LAYER_TOP
    ex += e.w + GAP
model.x, model.y = c3_x + LAYER_PAD, val.b + GAP
c3_h = LAYER_TOP + eng_h + GAP + model.h + LAYER_PAD
biz_h = LAYER_TOP + c3_h + LAYER_PAD
y = biz_y + biz_h + 30
dal_y = y
c4.x, c4.y = lx + LAYER_PAD + (layer_inner_w - c4.w) / 2, dal_y + LAYER_TOP
dal_h = LAYER_TOP + c4.h + LAYER_PAD
y = dal_y + dal_h + 14
other.x, other.y = lx + LAYER_PAD + (layer_inner_w - other.w) / 2, y
y = other.b + 12
phone_w = 10 + layer_w + CORRIDOR
phone_h = y - Y0
corr_x = lx + layer_w + CORRIDOR / 2

# --- Firebase backend ---------------------------------------------------------
GAP_FB = 128
fb_x = X0 + phone_w + GAP_FB
fb_inner_w = max(c5.w, c6.w)
c5.w = c6.w = fb_inner_w
fb_w = fb_inner_w + 2 * LAYER_PAD
c5.x = c6.x = fb_x + LAYER_PAD
c5.y = pres_y + LAYER_TOP
c6.y = c4.b + 26 - c6.h
if c6.y < c5.b + 40:
    c6.y = c5.b + 40
fb_y = Y0
fb_h = phone_h
gap_x = X0 + phone_w + 52

W = fb_x + fb_w + 6
H = Y0 + phone_h + 50

svg = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W:.0f} {H:.0f}" width="{W:.0f}" height="{H:.0f}" font-family="{FONT}">',
       '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#333333"/></marker></defs>',
       f'<rect x="0" y="0" width="{W:.0f}" height="{H:.0f}" fill="#FFFFFF"/>',
       f'<text x="{W / 2:.1f}" y="18" font-size="12.5" font-weight="bold" text-anchor="middle">Figure 1 (revises 2.2 Figure 1): SplitSmart block diagram, the same six components in three layers plus the new application class</text>',
       frame(X0, Y0, phone_w, phone_h, "Android phone: SplitSmart app (Java, package com.team04.splitsmart)", dashed=False, size=10.5, fill="#FFFFFF"),
       frame(lx, pres_y, layer_w, pres_h, "Presentation layer (MVVM)"),
       frame(lx, biz_y, layer_w, biz_h, "Business logic layer (plain Java, no Android or Firebase imports, JUnit tested)"),
       frame(c3_x, c3_y, c3_w, c3_h, "C3 Business Logic and Model", dashed=False, size=10.5, fill="#FFFFFF"),
       frame(lx, dal_y, layer_w, dal_h, "Data access layer (repository pattern)"),
       frame(fb_x, fb_y, fb_w, fb_h, "Firebase cloud backend", dashed=False, size=10.5, fill="#FFFFFF")]
for bx in [app, c1, c2] + engines + [model, c4, other, c5, c6]:
    svg.append(bx.svg())

# arrows
svg.append(path([(app.cx, app.b), (app.cx, c2.y)]))
svg.append(label(app.cx - 8, app.b + 17, ["builds AppContainer"], anchor="end"))
ay = c1.y + row_h / 2
svg.append(path([(c1.r, ay), (c2.x, ay)]))
svg.append(label((c1.r + c2.x) / 2, ay - 16, ["observes LiveData,", "forwards input"]))
svg.append(path([(c2.cx - 40, c2.b), (c2.cx - 40, c3_y)]))
svg.append(label(c2.cx - 46, (c2.b + c3_y) / 2 + 3, ["validate, split, balances, filters"], anchor="end"))
y1 = c2.y + row_h * 0.7
svg.append(path([(c2.r, y1), (corr_x, y1), (corr_x, c4.y + 12), (c4.r, c4.y + 12)]))
svg.append(label(corr_x - 6, biz_y + biz_h + 18, ["calls; every result comes back through a DataCallback<T> (asynchronous)"], anchor="end"))
ya = c4.y + c4.h * 0.42
svg.append(path([(c4.r, ya), (gap_x, ya), (gap_x, c5.cy), (c5.x, c5.cy)]))
svg.append(label(gap_x + 6, c5.cy + 14, ["register, log in,", "log out"], anchor="start"))
yb = c4.y + c4.h * 0.66
svg.append(path([(c4.r, yb), (c6.x, yb)]))
mid = (X0 + phone_w + c6.x) / 2
svg.append(label(mid, yb - 4, ["get / set / update / batch"]))
yc = c4.y + c4.h * 0.90
svg.append(path([(c6.x, yc), (c4.r, yc)], dashed=True))
svg.append(label(mid, yc + 11, ["snapshot listener →", "Subscription (new, A5)"], bold=True))
svg.append(path([(c6.cx, c6.b), (c6.cx, other.cy), (other.r, other.cy)], dashed=True))
svg.append(label(mid, other.cy - 15, ["changes pushed to", "other phones (new)"], bold=True))
legend = ["Solid arrows: calls between components (every repository call returns through a DataCallback). Dashed arrows: live updates (new, A5).",
          "Component numbers C1 to C6 are the ones from the Project 2.2 design; their responsibilities and the layer rules are unchanged.",
          "Inside a box, bold lines marked \u201cnew:\u201d or \u201cchanged:\u201d (and their continuation lines) are the departures from Project 2.2; plain lines are unchanged."]
svg.append(label(X0, Y0 + phone_h + 16, legend, anchor="start", size=9))
svg.append("</svg>")
open(OUT, "w").write("\n".join(svg))
print(f"{W:.0f} x {H:.0f}")
