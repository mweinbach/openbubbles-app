# M3 Expressive — Design Principles

The design layer. Read this before making visual decisions on a new screen. The API reference tells
you what compiles; this tells you what to build.

**Source markers** used throughout, carried over from the research:
`[CANON]` = verbatim from m3.material.io · `[GOOGLE]` = another Google-owned property
(design.google, developer.android.com, Android Developers Blog, material-components GitHub) ·
`[2ND]` = reputable secondary source quoting the guidance · `[UNVERIFIED]` = could not be confirmed;
**do not cite it as guidance**.

---

## 1. What M3 Expressive is

> "Material 3 Expressive is an evolution of the Material 3 design system. It's a set of new
> features, updated components, and design tactics for creating emotionally impactful UX."
> `[CANON]`

It is **additive, not a replacement**. Do not treat Expressive as a migration away from M3 — it is
M3 with a wider expressive range and a different default posture. Announced Google I/O May 2025;
rollout began with Android 16 QPR1 (September 2025).

### What changed versus baseline M3

| Axis | Change |
| --- | --- |
| **Motion** | Springs (stiffness / damping) replace duration + easing as the primary motion model `[CANON]` |
| **Shape** | Shape becomes a first-class expressive axis: expanded corner-radius scale plus ~35 named shapes and animated morphing `[GOOGLE + 2ND]` |
| **Typography** | The type scale doubles — every baseline role gains a parallel "emphasized" role `[CANON + 2ND]` |
| **Components** | 14–15 new or refreshed. **New:** button groups, FAB menu, loading indicator, split button, toolbars. **Updated:** app bars, common buttons, extended FAB, FABs, icon buttons, navigation bar, navigation rail, progress indicators, sliders. `[CANON]` |
| **Posture** | Deliberate variety replaces uniformity — a break "away from ultra-flat sameness toward emotionally engaging UI" `[2ND]` |

### The research

Google's public research write-up states the program ran:

> "46 separate research studies with hundreds of designs, and more than 18,000 participants from
> around the world" `[GOOGLE — design.google/library/expressive-material-design-google-research]`

A peer-reviewed paper exists: **"Usability Hasn't Peaked: Exploring How Expressive Design Overcomes
the Usability Plateau," Proceedings of CHI 2026, DOI 10.1145/3772318.3790373.** Title and venue
confirmed; the abstract could not be read (ACM DL returned 403).

Verified findings, verbatim from Google's own research page `[GOOGLE]`:

| Dimension | Finding |
| --- | --- |
| Cross-age preference | "well-applied, expressive design is strongly preferred by people of all ages" |
| Young-adult preference | **87% among 18-to-24-year-olds** |
| Attribute ratings | rated "overwhelmingly … higher for attributes such as 'energetic,' 'emotive,' 'positive vibe,' 'creative,' 'playful,' and 'friendly.'" |
| Subculture perception | **+32%** |
| Modernity perception | **+34%** |
| Rebelliousness perception | **+30%** |
| Attention / findability | eye-tracking: participants spotted key UI elements **up to four times faster** in the M3 Expressive designs |
| Age-gap closure | expressive design "enabled older users to spot key interactive elements on the screen just as fast as younger users **across 10 apps tested**" |

Plus a business-facing claim `[CANON]`: "Users are more likely to switch to products that use M3
Expressive components."

**Two claims to never make:**

1. **"Expressive makes apps feel faster."** `[UNVERIFIED]` The verified result is **eye-tracking
   time-to-locate UI elements** (4× faster spotting), not perceived speed and not latency. There is
   no verified perceived-performance statistic.
2. **Specific usability deltas** (task-completion rates, error-rate percentages). `[UNVERIFIED]`
   Google's framing is that expressive design improves usability rather than trading against it —
   that is the thesis of the CHI paper title — but the numeric deltas are not published in any
   source reachable here.

Everything in the table above **is** verified and quotable.

---

## 2. The seven design tactics

Material organizes the entire system around seven tactics. Wording below is verbatim `[CANON —
m3.material.io/blog/building-with-m3-expressive]`, including the site's own Do/Caution pairs.

### Tactic 1 — Use a variety of shapes

> "Shape can be a powerful communication tool in your interface."

- **Do:** "Break from the surrounding shape style to draw attention to a particular element."
- **Caution:** "Smaller shapes can result in essential actions looking less important."

Operational reading: **contrast is relational.** A shape is emphatic only because its neighbours are
not. Applying an expressive shape uniformly destroys the emphasis it was meant to create.

### Tactic 2 — Apply rich and nuanced colors

> "Mixing these colors for key components or visual elements can help emphasize the main takeaway."

- **Do:** "Use contrast to emphasize the main takeaway or element."
- **Caution:** "Without contrast, elements can blend together."

### Tactic 3 — Guide attention with typography

> "Use emphasized text styles to draw attention to important UI elements, like headlines and
> actions."

### Tactic 4 — Contain content for emphasis

> "Organize content into logical groupings or containers."

- **Do:** "Group similar content into informative groupings."
- **Caution:** "Ungrouped information can blend together."

### Tactic 5 — Add fluid and natural motion

> "Make interactions feel alive and spirited through shape morph or surface effects."

### Tactic 6 — Leverage component flexibility

> "UI should adapt to the user context."

In practice this means adapting across window size classes and form factors — foldables especially
`[2ND]`.

### Tactic 7 — Combine tactics to create hero moments

> "Hero moments use multiple expressive tactics to break from predictable or uniformly applied
> design ideas."

---

## 3. Hero moment budgeting

**This is the single most important constraint in the system. Enforce it on every screen you build.**

> "Stick to one or two hero moments in your product; too many moments can be overwhelming or
> distracting." `[CANON]`

Budget **one or two hero moments per product surface**. Not per component, not per widget — per
product. If a screen already has a hero moment, the next expressive idea goes on the baseline
treatment instead.

### The two qualifying questions

Before designating anything a hero moment, answer both `[CANON]`:

1. **"Is this interaction emotionally impactful?"**
2. **"Is this a key interaction in your product?"**

Both must be yes. A visually interesting interaction that is not key to the product does not qualify
— it is decoration competing with the real hero.

A hero moment is additionally characterized as "brief, delightful, surprising, and unexpected."
`[CANON]` "Brief" is load-bearing: a hero moment that persists is no longer surprising, it is the
new baseline, and it stops working.

### The decision procedure

1. Inventory the product's interactions; mark those that are **both** emotionally impactful **and**
   key to the product. `[CANON]`
2. Choose **at most two** as hero moments. `[CANON]`
3. On those, stack multiple tactics — shape break + color contrast + emphasized type + motion.
   `[CANON]`
4. Everywhere else, hold the baseline so the break reads as a break. Contrast is the mechanism;
   uniform expressiveness has no contrast. `[CANON]`
5. Never trade clarity for it: **"Don't compromise your product's core functionality for visual
   flourishes. No amount of emotion can compensate for a lack of clarity."** `[GOOGLE]`

### When the user asks to "make it expressive everywhere"

Push back, briefly, then implement the contrast version. State the mechanism: Expressive is a
contrast system; applying it uniformly produces a loud screen with no hierarchy, which is worse than
baseline M3, not better. Then deliver: one or two hero moments, calm elsewhere.

---

## 4. The expression levers

Four levers do the actual work. Pick the ones that serve the screen's job; do **not** pull all four
on the same element unless it is a designated hero moment.

| Lever | What it does | Reach for it when |
| --- | --- | --- |
| **Size** | Size-based hierarchy shows importance and creates natural flow `[2ND]` | One element must dominate; the content itself is the point (media, a number, a headline) |
| **Shape** | Shape flexibility lets apps reflect brand personality while improving interaction cues `[2ND]`; a shape break draws attention `[CANON]` | An element must read as a different *kind* of thing than its neighbours; state needs a non-color signal |
| **Color** | Color guides attention and enhances accessibility `[2ND]`; contrast emphasizes the main takeaway `[CANON]` | One action among several must win; a semantic distinction needs to survive a glance |
| **Motion contrast** | Springs and morphs make interactions feel alive `[CANON]` | The interaction itself is the delightful part; the transition carries meaning about where things came from |

Material's own summaries list a fifth lever, **containment** — grouping related elements in visually
distinct containers `[2ND]`. Treat containment as structural rather than expressive: it is how you
make the *rest* of the screen calm enough for the four levers above to register. See §5.

### How to choose

- **Ask what the screen's job is.** A media/browse screen wants size and shape. A dense settings or
  data screen wants containment and color, and should stay motion-quiet. A single-purpose action
  screen (timer, camera, player) is the best candidate for motion contrast.
- **One lever is usually enough** outside a hero moment. Two is a hero moment. Four is noise.
- **Prefer the lever the content already suggests.** If there is naturally one dominant object
  (album art, a chart, a countdown), size is free. If everything is the same object repeated (a
  list), size does nothing and containment plus one color accent does everything.
- **Motion contrast is the most expensive lever** — it costs frame budget, it fights reduced-motion
  settings, and a wrong spring reads as a bug. Spend it last and spend it on the hero.

---

## 5. Emphasis, containment, and grouping

### Containment is the substrate for expression

> **Do:** "Group similar content into informative groupings."
> **Caution:** "Ungrouped information can blend together." `[CANON]`

Rules:

- **Group before you decorate.** A loose row of controls cannot be made expressive; it can only be
  made loud. Put it in a connected button group, a segmented list, or a floating toolbar first —
  then the group becomes a single object that can carry one expressive treatment.
- **Connected implies "one set."** A connected button group visually asserts that its members are
  alternatives within a single set. Do not connect unrelated actions. `[UNVERIFIED — inference from
  ButtonGroup.md, but follow it]`
- **Containers signal relatedness cheaply.** Use `*Container` color roles and surface-container tones
  to group without adding shape or motion noise.

### Surface hierarchy

M3 uses **tonal surface containers**, not elevation overlays: `surfaceContainerLowest / Low /
(default) / High / Highest`, giving "a visual hierarchy with different elevations in both light and
dark themes." `[GOOGLE]`

Picking among them `[GOOGLE]`:

| Role | Use for |
| --- | --- |
| **surfaceContainerLow** | "expanded containers that need to sit below Surface-Container"; non-interactive cards |
| **surfaceContainer** | "the default container color for most elements" |
| **surfaceContainerHigh** | "high-emphasis components that need to sit on top of or in combination with Surface-Container"; "helps bring focus and hierarchy to critical areas" |

All pair with `onSurface` / `onSurfaceVariant`.

---

## 6. Motion, at the design level

The API details (spec functions, spring constants) live elsewhere. These are the design decisions.

### Springs replaced duration and easing

Motion is now specified as springs with three attributes `[CANON]`:

| Attribute | Definition (verbatim) |
| --- | --- |
| **Stiffness** | "defines the hardness of the spring. Higher stiffness resolves the motion faster" |
| **Damping** | "defines how fast the bounce wears out. Higher damping stops the bounce faster" |
| **Initial velocity** | "defines the initial speed of the spring, which influences the total spring duration" |

Why springs: they handle "gestures, interruptions, and retargeting animations seamlessly." `[CANON]`
A spring can be redirected mid-flight from its current position and velocity; a duration/easing curve
must be restarted or cross-faded. **This is why you stop writing `tween(300)`.**

### Two schemes — pick one per product, not per screen

| Scheme | Verbatim guidance | Use for |
| --- | --- | --- |
| **Expressive** | "Material's opinionated motion scheme"; it "overshoots the final values to add bounce" | "hero moments and key interactions" |
| **Standard** | "feels more functional with minimal bounce"; motions "ease into the final values" | utilitarian products |

`[CANON]`

Mechanism: Expressive uses lower damping (visible overshoot); Standard uses higher damping. `[2ND]`

### Spatial versus effects — the rule that has no exceptions

This is the central taxonomy `[CANON]`:

- **Spatial** — animates **position, rotation, size, and rounded corners**. This spring "overshoots
  the final value and bounces into place."
- **Effects** — animates **color and opacity**. "there shouldn't be any overshoot."

**Bounce is for things that move. Bounce is never for things that fade or recolor.** An overshooting
opacity animation would exceed 100% or go negative; an overshooting color animation would visibly
pass through a wrong hue. That is why the split exists — it is not stylistic.

### Speed tiers

| Tier | Canonical guidance — what it is for |
| --- | --- |
| **Fast** | "Small components like switches and buttons" |
| **Default** | "Medium-scale animations like bottom sheets and navigation rails" |
| **Slow** | "Full-screen animations and content refreshes" |

`[CANON]` Governing heuristic: **faster movements for smaller elements, slower for larger ones.**
A slow bouncy spring on a switch reads as broken.

**Device scaling:** "Token values adjust per device type," ensuring "the movement feels fast in the
context of the device." `[CANON]` A 300ms transition on a watch and on a tablet are not perceptually
equivalent; the tokens absorb that. This is a reason to use the scheme rather than hard numbers.

**Coverage:** on Jetpack Compose, **21 Material components use the physics system by default**, and
there are three levels of customization — use the default schemes, create a custom scheme, or swap
schemes per element. `[CANON]`

### The four summary rules

1. Overshoot **only** on spatial properties (position, rotation, size, corner radius). `[CANON]`
2. **No overshoot** on color or opacity. `[CANON]`
3. Reserve the Expressive (bouncier) scheme for hero moments and key interactions; use Standard for
   utilitarian surfaces. `[CANON]`
4. Match tier to element scale: Fast for small controls, Slow for full-screen. `[CANON]`

### Material's eight principles of good motion

`[2ND — summarizing m3.material.io motion guidance]`

1. **Accessibility** — respect user platform settings.
2. **Consistency** — "Having certain rules for movement creates a sense of unity."
3. **Stable layouts** — prevent layout shifts during loading.
4. **Avoid jump cuts** — prevent instant screen switches without motion.
5. **Spatial coherence** — maintain clear structural relationships.
6. **Unified directionality** — "group the elements and move them along an axis."
7. **Clean fading** — prevent content overlap during transitions.
8. **Functional simplicity** — ensure motion assists user tasks rather than distracting.

---

## 7. Shape, at the design level

### The expanded corner-radius scale

| Token | Value |
| --- | --- |
| None | 0dp |
| Extra small | 4dp |
| Small | 8dp |
| Medium | 12dp |
| Large | 16dp |
| **Large increased** | **20dp** |
| **Extra large increased** | **32dp** |
| Extra large | 28dp |
| **Extra extra large** | **48dp** |
| Full | 50% (pill) |

`[GOOGLE — material-components-android/docs/theming/Shape.md]`

The three bolded tokens — **large-increased (20dp), extra-large-increased (32dp), and
extra-extra-large (48dp)** — are the Expressive additions. They extend the old scale upward and fill
its gaps. Design intent: "Each step provides meaningful visual difference." `[2ND]`

Corner families remain **rounded** and **cut**, defaulting to rounded. `[GOOGLE]`

### The named shape library

Expressive ships ~35 named shapes (squircles, scallops, bursts, cookies, clovers) usable as
containers, avatars, and morph targets. `[GOOGLE/2ND]`

Verified names (35, exact spelling matches the Compose `MaterialShapes` API):

Arch · Arrow · Bloom · Bun · Burst · Circle · Clamshell · Diamond · Eight-leaf clover · Fan ·
Flower · Four-leaf clover · Four-sided cookie · Gem · Ghostish · Heart · Nine-sided cookie · Oval ·
Pentagon · Pill · Pixel circle · Pixel triangle · Puffy · Puffy diamond · Semicircle ·
Seven-sided cookie · Six-sided cookie · Slanted · Soft bloom · Soft burst · Square · Sunny ·
Triangle · Twelve-sided cookie · Very sunny

### Design guidance for shape

`[2ND, corroborated by CANON tactic 1]`

- **Embrace tension.** Combine contrasting shapes — "sharp, angular forms alongside soft, rounded
  ones" — rather than applying one roundedness everywhere.
- **Avoid fixed meaning.** "Avoid assigning a fixed, literal meaning to any single shape." Shapes are
  not iconography; a clover does not mean "favorite."
- **Apply intentionally.** "Abstract and unconventional shapes should be used thoughtfully" — put
  them in decorative moments such as avatars and media containers, and use them **sparingly in core
  components** where predictability matters.
- **Watch the size trap.** "Smaller shapes can result in essential actions looking less important."
  `[CANON]`

### Shape morphing is a state signal, not decoration

Morphing is animated interpolation between two shapes, used to signal state. Verified bindings:

| Binding | Source |
| --- | --- |
| **Button press** — shape morph on press/release is the standard Expressive button feedback; buttons "transform shape and size to achieve eye-catching springy animation effects" | `[GOOGLE — Wear OS blog]` |
| **Split button trailing toggle** — "The split button has a separate menu button that spins and changes shape when activated"; implemented as an animated vector on a checkable button | `[GOOGLE — SplitButton.md]` |
| **Loading** — the loading indicator is "a looping shape morph sequence composed of seven unique Material 3 shapes" | `[2ND, quoting Material]` |
| **Selection in button groups** — connected button groups override member shapes to unify the group | `[GOOGLE — ButtonGroup.md]` |

Typical resting binding: **a round resting shape morphs squarer on press**, and toggles morph on
check/select. `[2ND]` The morph is driven by the **spatial** spring, so it inherits overshoot.
`[CANON — spatial tokens cover "rounded corners"]`

**If a morph does not correspond to a state change, delete it.** A decorative morph burns the signal
that a real state morph needs.

---

## 8. Color, at the design level

### The tactic

> **Do:** "Use contrast to emphasize the main takeaway or element."
> **Caution:** "Without contrast, elements can blend together." `[CANON]`

### Role-by-role

Verbatim from Google's role documentation `[GOOGLE —
developer.android.com/design/ui/wear/guides/styles/color/roles-tokens]`:

| Role | When to use | Pair with |
| --- | --- | --- |
| **Primary** | "the most important actions in the UI, like primary buttons or calls to action"; "should stand out and be instantly recognizable" | onPrimary |
| **PrimaryContainer** | "background elements like cards or modals to highlight sections or selected states"; "draw attention to important content or ongoing activities" | onPrimaryContainer |
| **Secondary** | "supporting actions in areas with dense UI"; maintains visibility "without overshadowing primary elements" | onSecondary |
| **SecondaryContainer** | "organizing secondary elements in dense layouts"; creates structure "but not dominant" | onSecondaryContainer |
| **Tertiary** | **"drawing attention to key elements"** — "particularly effective for components that need to stand out, such as badges, stickers, or special action elements" | onTertiary |
| **TertiaryContainer** | "backgrounds that group tertiary-related content, like collections of badges or stickers" | onTertiaryContainer |
| **Error** | "remove, delete, close, or dismiss actions" | onError |
| **ErrorContainer** | "less prominent container color for components using error state"; "an active error state that feels less interactive than a filled state" | onErrorContainer |

`*-Dim` roles (primaryDim, secondaryDim, tertiaryDim, errorDim) are documented on the **Wear OS**
surface. Treat them as Wear-specific unless confirmed for mobile. `[GOOGLE]`

### Tertiary is the hero's color

**Tertiary is the "stand out" accent** — badges, stickers, special one-off actions, and content that
must break from the primary/secondary rhythm. `[GOOGLE]` This makes tertiary the natural carrier of a
hero moment's color break: it is the accent that is not already spoken for by the primary action or
its supporting actions.

Default to: primary for the main action, secondary for supporting actions, **tertiary for the one
thing that breaks the pattern**.

### Pairing rules

- **The pairing rule is structural**: always use an accent with its matching `on*` role, and a
  container with its matching `on*Container` role. This "ensures adequate contrast and visual
  hierarchy." `[GOOGLE]` Never hand-pick an `on` color.
- **Guaranteed floor**: "All color pairs provide a minimum of 3:1 color contrast." `[GOOGLE]` This is
  the **role-pair** guarantee — text still needs to satisfy WCAG text ratios (§11).
- Toolbars ship in both **standard and vibrant** color schemes; vibrant is an expressive escalation
  for a floating/docked toolbar. `[GOOGLE]`

### Dynamic color

- Roles are derived algorithmically from a seed into tonal palettes — "never raw hexes." `[2ND]`
- On Android S+, apply the dynamic theme overlay so the scheme adapts to wallpaper. Use **color
  harmonization** so that "reserved colors (eg. those used for semantic or brand) looks good next to
  a user's dynamically-generated color." `[GOOGLE]`
- Expressive brings "deeper tonal palettes" and a "wider token set … to allow more color to be
  applied across different themes." `[GOOGLE]`
- **Design consequence:** if the app uses dynamic color, you cannot rely on a specific hue for
  meaning. Encode meaning in the *role*, and let shape, size, or containment carry any distinction
  that must survive an arbitrary seed.

---

## 9. Typography, at the design level

### The tactic

> "Guide attention with typography — Use emphasized text styles to draw attention to important UI
> elements, like headlines and actions." `[CANON]`

### The 30-style scale

Baseline M3 has 15 roles: **Display / Headline / Title / Body / Label**, each in **Large / Medium /
Small**. Expressive adds a **parallel emphasized set**, producing a **30-style scale — 15 baseline
and 15 emphasized roles.** `[2ND]`

Compose exposes the pairing directly: `titleLarge` / `titleLargeEmphasized`, `headlineLarge` /
`headlineLargeEmphasized`, and so on for all 15.

**What "emphasized" actually is:** a weight-axis shift. The emphasized styles have identical
size, line-height, and tracking to their baseline counterparts — the difference is
`WeightMedium` (display/headline/title/body) or `WeightBold` (labelLargeEmphasized). Emphasized is
not a separate size ramp.

### Rules

- Apply emphasized styles to "headlines, selected items, or other areas that require a clear focal
  point." `[2ND]`
- **Emphasized is the design-system-sanctioned way to add weight. Prefer it over ad-hoc
  `FontWeight.Bold`** so emphasis stays tokenized and themeable. `[2ND]` Treat a hand-written
  `fontWeight = FontWeight.Bold` in an Expressive app as a smell.
- Contextual mapping: `labelLarge` → buttons; `bodyLarge` → list items; `bodySmall` → form-field
  hints. `[2ND]`

### Large display type as a hero element

The canonical structural home for hero typography is the **flexible app bars** `[GOOGLE —
TopAppBar.md]`:

- **Medium flexible app bar** — "Use to display a larger headline. It can collapse into a small app
  bar on scroll."
- **Large flexible app bar** — "Use to emphasize the headline of the page."
- Both offer "larger title text, subtitle, left- and center-aligned text options, text wrapping."

This is the cheapest hero moment available: a large headline that collapses into a small app bar as
the user scrolls, so the editorial moment costs nothing in sustained screen real estate. If a screen
needs a hero and nothing else obviously qualifies, this is the default answer.

### Variable fonts

`[GOOGLE]`

- "Variable fonts allow for dynamic, customizable typography."
- Expressive embraces variable fonts with "adjustable axes, including **weight and width**," enabling
  "dynamic and delightful typographic experiences."
- Named uses: **dynamic font weight** — "utilize variable font axis to signal expressive motion
  feedback"; **dynamic font width**; and the two combined for interactive effects.
- Axes extend to third-party faces such as **Roboto Flex**.

Design implication: type weight/width can be animated as *feedback* — a label thickening on press —
which is a typographic analogue of shape morph. Because weight/width change glyph bounds, treat them
as **spatial** motion. `[UNVERIFIED — this spatial/effects classification of font-axis animation is
inference, not published guidance. It is the safe reading; do not present it as Material's rule.]`

Wear-specific additions (do not assume for mobile): Arc Text for surface titles, a **numerals** type
role with "bigger and more styled text sizes for non-localized strings." `[GOOGLE]`

---

## 10. Anti-patterns

### The overriding rule

> "Don't compromise your product's core functionality for visual flourishes. **No amount of emotion
> can compensate for a lack of clarity.**" `[GOOGLE]`

### Expressive everywhere

- **Budget hero moments**: one or two, product-wide. `[CANON]`
- **Expression is relational**: a shape reads as emphatic only by "break[ing] from the surrounding
  shape style." `[CANON]` Uniform application annihilates the emphasis.
- **Keep abstract shapes out of core components**: concentrate them in decorative moments (avatars,
  media) and use them sparingly in core components. `[2ND]`
- **Don't assign literal meaning to shapes.** `[2ND]`

### Over-animating

- **No overshoot on color or opacity.** `[CANON]`
- **Reserve the bouncy scheme** for hero moments and key interactions. Shipping Expressive springs on
  every element is the motion equivalent of expressive-everywhere. `[CANON]`
- **Match tier to scale.** A slow bouncy spring on a switch feels broken. `[CANON]`
- **Motion must be functional**: "ensure motion assists user tasks, not distracts." `[2ND]`
- Don't allow layout shift during loading; don't use jump cuts; don't break spatial coherence; don't
  let content overlap during cross-fades; move grouped elements along a shared axis. `[2ND]`

### Contrast and blending

- "Without contrast, elements can blend together." `[CANON]`
- "Ungrouped information can blend together." `[CANON]`
- "Smaller shapes can result in essential actions looking less important." `[CANON]`

### Component-specific do-nots

All `[GOOGLE]` unless noted.

| Don't | Instead |
| --- | --- |
| Build a speed dial or stack small FABs | Use the FAB menu |
| Use a FAB menu with an **extended FAB** | Not supported — "Fab menu is not used with extended FABs" |
| Exceed 6 FAB-menu actions | Keep to 2–6 |
| Open a FAB menu somewhere other than its FAB | It "should always appear in the same place as the FAB that opened it" |
| Keep using the bottom app bar | Deprecated — replace with the docked toolbar |
| Let a floating toolbar run past the pane edge | It "shouldn't exceed the edge of the window or pane"; use an overflow menu |
| Show a navigation bar and a toolbar on the same page | "Show the navigation bar on primary pages, and toolbars on subsequent pages with actions" `[2ND, quoting Material]` |
| Mix linear and circular progress for the same activity | "Only one type should represent each kind of activity in an app" |
| Use the loading indicator for waits > ~5 seconds | Use a progress indicator |
| Use the loading indicator when the process becomes determinate | Use a progress indicator |
| Apply the wavy style at very small sizes | "at very small sizes, the wavy shape may not be as visible" |
| Let a button group overflow its container | Use menu or wrap overflow modes |
| Exceed 3–5 navigation bar destinations | Use a rail/drawer pattern |

---

## 11. Accessibility

Expressive is not a licence to degrade accessibility. Google's own framing is that expressive design
**narrows** an accessibility gap: it "enabled older users to spot key interactive elements on the
screen just as fast as younger users." `[GOOGLE]` Hold that line.

### Reduced motion

- Material's **first** motion principle is accessibility: respect user platform settings. `[2ND]`
- The M3 Expressive web implementation states the rule plainly: "Components respond to the
  `prefers-reduced-motion` media query, ensuring that **expressive motion never compromises comfort
  or accessibility**." `[2ND — third-party M3E implementation, not Google canon, but it reflects the
  platform expectation]`
- On Android the equivalent user setting is Remove/Reduce animations. Honoring it means disabling
  overshoot and, where practical, substituting a cross-fade. `[UNVERIFIED — the specific Material
  substitution recipe for Android reduced-motion is not published in a reachable source. The
  substitution is the safe implementation, not a quoted rule.]`
- **Time-based limits** (W3C-derived, published in Material's accessibility guidance) `[GOOGLE]`:
  - Content that "moves, scrolls, or blinks automatically" must be "paused, stopped, or hidden if it
    lasts more than five seconds."
  - Flashing limited to "three times in a one-second period."
  - "Avoid flashing large central regions of the screen."

Design consequence: a looping expressive animation — a morphing hero, a wavy indicator, an animated
background — that runs longer than five seconds needs a stop/pause affordance or must be gated on the
reduced-motion setting.

### Touch targets

`[GOOGLE — Material accessibility guidance]`

- **Touch targets: at least 48 × 48 dp** — "a physical size of about 9mm, regardless of screen size."
- **Pointer targets: at least 44 × 44 dp** for motion-tracking devices (mouse, stylus).
- **Spacing:** "Touch targets should be separated by 8dp of space or more to ensure balanced
  information density and usability."

This constrains the expressive size scale directly. The XS button and XS slider sizes must still
carry a ≥48dp touch target even when their **visual** bounds are smaller, and connected button groups
(2dp internal spacing) must not let two adjacent targets collapse below the separation guidance.
`[UNVERIFIED — the specific reconciliation of XS visual size with 48dp targets is inference; treat
48dp as non-negotiable regardless.]`

### Contrast

`[GOOGLE]`

- **Small text: minimum 4.5:1** against its background.
- **Large text (14pt bold / 18pt regular and above): minimum 3:1.**
- **Material color-role guarantee: "All color pairs provide a minimum of 3:1 color contrast."** Use
  accent-with-on-accent and container-with-on-container pairings rather than improvising.
- Component specs bake contrast in — these gaps exist "to meet modern contrast requirements," so do
  not zero them out for a cleaner look:
  - Progress indicator: **4dp track gap**, **4dp stop indicator**.
  - Slider: **6dp thumb–track gap**, **2dp inside corner**, **4dp stop indicator**.

### Semantics for new components

- **Progress indicators**: determinate vs indeterminate is a **semantic** choice — determinate when
  progress and wait time are known, indeterminate when they are not. Choosing wrong misreports state
  to every user, including assistive tech. `[GOOGLE]`
- **Loading indicator**: it "communicate[s] app state and available actions, indicating whether users
  can navigate away." `[GOOGLE]`
- **Split button**: the trailing button is a **checkable** control with an animated icon — its checked
  state is real state, not decoration. `[GOOGLE]`
- **Connected button groups**: a single-select group is a radio group. Set
  `semantics { role = Role.RadioButton }` on each member; use `Role.Button` for a group of
  independent actions. (This is what the reference apps do; the accessible-name obligation is
  inference from `[GOOGLE — BottomNavigation.md]` label-visibility docs.)
- **Navigation bar**: label visibility modes exist (auto / selected / labeled / unlabeled). Unlabeled
  mode removes visible text, so icons must carry accessible names. `[GOOGLE; the accessible-name
  obligation is inference — UNVERIFIED]`
- **Navigation rail** expanded state is **non-modal**, unlike the drawer it replaces — focus handling
  differs accordingly. `[GOOGLE]`
- **Chips**: all chips "include accessibility features like configurable touch targets and
  RTL-friendly layouts." `[GOOGLE]`
- Detailed screen-reader semantics (roles, live regions, announcements) for the five brand-new
  components are `[UNVERIFIED]` — not published in any reachable source. Do not invent them; use the
  standard Compose semantics APIs and test with TalkBack.

### Accessibility overrides are baked into components

Floating toolbars stay expanded and disable `scrollBehavior`, and `FlexibleBottomAppBar` disables
`scrollBehavior`, whenever an accessibility service is active. This is intentional platform behavior.
Do not fight it with manual state.

---

## 12. Component design constraints — quick lookup

Design-layer constraints only. API signatures live in the components skill.

| Component | Design constraints |
| --- | --- |
| **Buttons** | Five sizes: XS, S, M, L, XL. Treatments: elevated, filled, tonal, outlined. Round and square resting shapes with **shape morph on press**. Small components → **Fast** spring tier. Don't shrink a primary action into insignificance. |
| **Button groups** | Two variants. **Standard**: preserves each button's shape, default spacing **12dp**. **Connected**: overrides member shapes for cohesion — **2dp spacing, 8dp inner corners, fully rounded outer corners**. Supports fixed/flexible/mixed sizes. Overflow modes: **menu** or **wrap** — use them rather than letting the group exceed its container. |
| **Split button** | "Split buttons open a menu to give people more options related to an action." A specialized connected button group. Leading button (icon and/or label) + trailing menu button that "spins and changes shape when activated." 2dp default spacing. Use only when there is one obvious default action — the leading button must be worth pressing on its own. |
| **FAB menu** | "2–6 related actions floating on screen." Replaces the speed dial and stacked small FABs. Must open in the same place as the FAB that opened it. One consistent menu size regardless of the opening FAB's size. **"Fab menu is not used with extended FABs."** |
| **Docked toolbar** | Full window width. "Best used for global actions that remain the same across multiple pages." **Replaces the deprecated bottom app bar.** Shorter height, more layout options. |
| **Floating toolbar** | Floats above body content. "Best used for contextual actions relevant to the body content or the specific page." Horizontal or vertical (vertical suits larger screens). Can pair with a FAB. Must not exceed the window/pane edge — use overflow menus. Available in **standard and vibrant** color schemes. |
| **Toolbars vs nav bar** | "Show the navigation bar on primary pages, and toolbars on subsequent pages with actions." Do not stack them. |
| **Loading indicator** | For progress that loads in **under five seconds**. Replaces most indeterminate circular progress indicators. A looping morph of **seven** Material shapes. Contained (default) or uncontained. Default **38dp** indicator in a **48×48dp** container. Do **not** use it when the process transitions to determinate, or for waits > 5s. |
| **Progress indicators** | Linear = animate along a fixed visible track. Circular = animate along an invisible circular track, applicable directly to a surface. **"Only one type should represent each kind of activity in an app."** Determinate when progress and wait time are known; indeterminate when not. Wavy styling "is best used when a more expressive style is appropriate" — but "at very small sizes, the wavy shape may not be as visible." Default track thickness **4dp**; expressive thick variant **8dp**. |
| **Navigation bar** | **3–5 destinations.** Expressive sizing: height 80→**64dp**, top padding 12→**6dp**, bottom padding 16→**6dp**, active indicator 64→**56dp**. At **≥600dp** items switch to a horizontal arrangement (icon moves from top to start) and item width becomes content-based. |
| **Navigation rail** | Expressive sizing: width 80→**96dp**, item min height 60→**64dp**, elevation 0→**3dp**, active label color → **secondary**, selected label no longer bolded. Can now **expand** like a drawer — it **replaces the navigation drawer component** — with **non-modal** behavior. Medium-scale → **Default** spring tier. |
| **App bars** | Small: "Use in dense layouts or when a page is scrolled." Medium flexible: "Use to display a larger headline. It can collapse into a small app bar on scroll." Large flexible: "Use to emphasize the headline of the page." Flexible variants **replace the deprecated medium and large variants**. Subtitles supported across variants, defaulting to `onSurfaceVariant`. Scroll behaviors: lift-on-scroll, scroll/enterAlways/snap, compress. |
| **Carousel** | Four strategies. **Multi-browse** (default): "quick browsing of many small items." **Hero**: "highlights large content, like movies and other media, for more considered browsing." **Uncontained**: "fits as many items as possible … without altering the item size." **Full-screen**: one item at a time; recommended for **vertical orientation in portrait**. Item width must be a concrete dp value, not wrap-content. Expressive adds vertical carousels. |
| **Sliders** | Continuous slider renamed **standard slider**; discrete slider is now the **stops configuration**. **Five sizes** XS (default) → XL, differing in track thickness and corner size. Horizontal **and vertical**. Optional inset icons (standard sliders only). Use **centered** when the default/zero value sits at the midpoint. |
| **Chips** | Four types with a decision procedure: "Does the chip represent an action (assist chip) or filter results (filter chip)? Is its content generated by the product (suggestion chip), or by the person using the product (input chip)?" Elevated styles for backgrounds that need protection, such as images. No Expressive-specific chip guidance was found. |
| **ListItem / segmented** | **No Expressive-specific canonical guidance could be retrieved.** Verified adjacent facts only: `bodyLarge` is the type role for list items; connected button groups (2dp spacing, 8dp inner corners, fully rounded outer corners) are the family segmented-button-like patterns now belong to. Anything beyond that is `[UNVERIFIED]`. |
| **Wear OS** | Four principles: round form factor (edge-hugging button), screen layout (3-slot tile `PrimaryLayout`, `TransformingLazyColumn`), visual elevation (dynamic color, variable fonts), expressive motion (spring animations, shape morphing). |

---

## 13. Applying this — the working checklist

Run this before declaring a screen done:

1. **Name the hero moment.** One, maybe two, per product. If you can't name it, the screen has none —
   that is fine and often correct.
2. **Check both qualifying questions** for anything you designated a hero: emotionally impactful AND
   key to the product.
3. **Count the levers on each element.** More than one lever outside the hero → pull one back.
4. **Verify the baseline is actually calm.** If everything is rounded/animated/colorful, the hero has
   nothing to break from.
5. **Motion audit:** no overshoot on color or alpha; spring tier matches element scale; nothing loops
   past five seconds without a stop affordance; reduced-motion honored.
6. **Contrast audit:** accent paired with its `on` role; text meets 4.5:1 (small) / 3:1 (large);
   component gap specs (4dp track gap, 6dp thumb gap) left intact.
7. **Target audit:** every interactive element ≥48×48dp, ≥8dp apart, regardless of visual size.
8. **Semantics audit:** connected groups have `Role.RadioButton`/`Role.Button`; icon-only controls
   have content descriptions; determinate vs indeterminate progress is truthful.
9. **Clarity check, last and highest priority:** "No amount of emotion can compensate for a lack of
   clarity." If any expressive choice made the screen harder to read, remove it.
